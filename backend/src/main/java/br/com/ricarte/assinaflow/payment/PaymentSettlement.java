package br.com.ricarte.assinaflow.payment;

import br.com.ricarte.assinaflow.common.time.TimeProvider;
import br.com.ricarte.assinaflow.notification.NotificationService;
import br.com.ricarte.assinaflow.subscription.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class PaymentSettlement {
    private final br.com.ricarte.assinaflow.metrics.BillingMetrics metrics;
    private final PaymentLedger ledger;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionRenewalAttemptRepository attempts;
    private final NotificationService notifications;
    private final SubscriptionCache cache;
    private final TimeProvider time;

    public PaymentSettlement(PaymentLedger ledger, SubscriptionRepository subscriptions,
                             SubscriptionRenewalAttemptRepository attempts, NotificationService notifications,
                             SubscriptionCache cache, TimeProvider time, br.com.ricarte.assinaflow.metrics.BillingMetrics metrics) {
        this.metrics = metrics; this.ledger = ledger; this.subscriptions = subscriptions; this.attempts = attempts;
        this.notifications = notifications; this.cache = cache; this.time = time;
    }

    @Transactional
    public void apply(PaymentObligation claim) {
        ledger.lockUser(claim.userId());
        var s = claim.subscriptionId() == null ? null : subscriptions.findByIdForUpdate(claim.subscriptionId()).orElseThrow();
        var current = ledger.lockForSettlement(claim);
        if (current == null) return;
        boolean approved = current.status().equals("APPROVED");
        if (!approved && !current.status().equals("DECLINED")) return;
        var context = current.context();
        if (context.kind().equals("RENEWAL")) {
            if (!s.getExpirationDate().equals(context.cycle())) {
                ledger.review(current.id(), "SUBSCRIPTION_CYCLE_CHANGED"); return;
            }
            int number = current.failures() + 1;
            var attempt = new SubscriptionRenewalAttemptEntity();
            attempt.setSubscriptionId(s.getId()); attempt.setCycleExpirationDate(context.cycle());
            attempt.setAttemptNumber(number); attempt.setAttemptedAt(time.now()); attempt.setAmountCents(current.amountCents());
            attempt.setResult(approved ? RenewalAttemptResult.SUCCESS : RenewalAttemptResult.FAILURE);
            if (!approved) { attempt.setErrorCode("PAYMENT_DECLINED"); attempt.setErrorMessage("Recusa definitiva do provedor."); }
            attempts.save(attempt);
            metrics.renewalAttempt(approved, "durable");
            String event;
            if (approved) {
                s.setStartDate(context.cycle()); s.setExpirationDate(context.cycle().plusMonths(1));
                s.setRenewalCreditCents(Math.max(0, context.creditCents() - context.priceCents()));
                s.setRenewalFailures(0); s.setNextRenewalAttemptAt(null);
                // An authorized debit may finish after cancellation. Keep paid access and cancellation intent.
                if (s.getStatus() != SubscriptionStatus.CANCELAMENTO_AGENDADO) s.setStatus(SubscriptionStatus.ATIVA);
                event = NotificationService.RENEWAL_SUCCEEDED;
            } else {
                s.setRenewalFailures(number);
                if (s.getStatus() == SubscriptionStatus.CANCELAMENTO_AGENDADO) {
                    s.setNextRenewalAttemptAt(null);
                } else if (number >= 3) {
                    s.setStatus(SubscriptionStatus.SUSPENSA); s.setAutoRenew(false);
                    s.setNextRenewalAttemptAt(null); s.setSuspendedAt(time.now());
                    metrics.subscriptionSuspended("durable");
                } else s.setNextRenewalAttemptAt(time.now().plus(RenewalBackoff.afterFailedAttempt(number)));
                event = number >= 3 ? NotificationService.SUBSCRIPTION_SUSPENDED : NotificationService.RENEWAL_FAILED;
            }
            s.setRenewalInFlightUntil(null);
            subscriptions.save(s);
            notifications.enqueue(event, s.getUserId(), s.getId(), "Resultado de renovacao", "Consulte sua assinatura.",
                    Map.of("expiration", s.getExpirationDate().toString()), "payment-result|" + current.id() + "|" + number);
            ledger.applied(current, s.getId(), approved, approved ? 0 : number);
        } else if (!approved) {
            ledger.applied(current, current.subscriptionId(), false, 1);
        } else {
            if (context.kind().equals("SUBSCRIBE")) {
                if (subscriptions.existsByUserIdAndStatusIn(current.userId(), List.of(SubscriptionStatus.ATIVA, SubscriptionStatus.CANCELAMENTO_AGENDADO))) {
                    ledger.review(current.id(), "ACTIVE_SUBSCRIPTION_AFTER_PAYMENT"); return;
                }
                s = new SubscriptionEntity(); s.setUserId(current.userId()); s.setPlan(Plan.valueOf(context.plan()));
                s.setStartDate(context.start()); s.setExpirationDate(context.start().plusMonths(1));
                s.setStatus(SubscriptionStatus.ATIVA); s.setAutoRenew(true);
            } else {
                if (s.getVersion() != context.version()) { ledger.review(current.id(), "SUBSCRIPTION_CHANGED_AFTER_PAYMENT"); return; }
                if (context.kind().equals("REACTIVATE")) {
                    s.setStartDate(context.start()); s.setExpirationDate(context.start().plusMonths(1));
                    s.setStatus(SubscriptionStatus.ATIVA); s.setAutoRenew(true); s.setRenewalFailures(0);
                    s.setRenewalCreditCents(Math.max(0, context.creditCents() - context.priceCents()));
                    s.setNextRenewalAttemptAt(null); s.setRenewalInFlightUntil(null); s.setSuspendedAt(null);
                } else s.setPlan(Plan.valueOf(context.plan()));
            }
            subscriptions.save(s);
            String type = context.kind().equals("CHANGE_PLAN") ? NotificationService.PLAN_CHANGED : NotificationService.SUBSCRIPTION_CREATED;
            notifications.enqueue(type, current.userId(), s.getId(), "Pagamento reconciliado", "Consulte sua assinatura.", Map.of(), "payment-applied|" + current.id());
            ledger.applied(current, s.getId(), true, 0);
        }
        cache.evictActive(current.userId());
    }
}
