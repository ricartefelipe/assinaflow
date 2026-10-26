package br.com.ricarte.assinaflow.subscription;

import br.com.ricarte.assinaflow.common.exception.ConflictException;
import br.com.ricarte.assinaflow.metrics.BillingMetrics;
import br.com.ricarte.assinaflow.payment.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentGateway gateway;
    private final BillingMetrics metrics;
    private final PaymentLedger ledger;
    private final PaymentSettlement settlement;

    public PaymentService(PaymentGateway gateway, BillingMetrics metrics, PaymentLedger ledger, PaymentSettlement settlement) {
        if (!gateway.supportsRecovery()) throw new IllegalStateException("Gateway requires an implemented recovery contract; select a provider before enabling HTTP sandbox");
        this.gateway = gateway; this.metrics = metrics; this.ledger = ledger; this.settlement = settlement;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentResult charge(UUID user, int amount, String key, String description) {
        if (user == null || amount < 0 || key == null || key.isBlank()) throw new IllegalArgumentException("Invalid payment identity");
        if (amount > 0) gateway.requireReady(user);
        var o = ledger.prepare(user, amount, key);
        if (o.closed() && o.status().equals("DECLINED")) o = ledger.reauthorizeDeclined(o.id());
        if (o.closed()) {
            if (o.status().equals("APPROVED")) return result(o);
            if (o.status().equals("DECLINED")) return result(o);
            throw new PaymentOutcomeUnknownException();
        }
        var completed = execute(o.id());
        if (completed == null || !(completed.status().equals("APPROVED") || completed.status().equals("DECLINED")))
            throw new PaymentOutcomeUnknownException();
        if (completed.status().equals("DECLINED") && !completed.context().kind().equals("RENEWAL"))
            settlement.apply(completed);
        return result(completed);
    }

    private PaymentObligation execute(UUID id) { return execute(id, false); }

    private PaymentObligation execute(UUID id, boolean lookupOnly) {
        var claim = lookupOnly ? ledger.claimLookup(id) : ledger.claim(id);
        if (claim == null) return null;
        String operation = ledger.operation(claim);
        if (operation.equals("APPLY")) return claim;
        var timer = operation.equals("CHARGE") ? metrics.startPaymentTimer() : null;
        boolean approved = false;
        try {
            ProviderPayment receipt;
            try {
                receipt = operation.equals("CHARGE") ? gateway.submit(claim.command()) : gateway.lookup(claim.command(), claim.providerReference());
            } catch (RuntimeException ex) {
                // Keep provider messages, URLs, tokens and response bodies out of durable records and logs.
                return ledger.record(claim, null);
            }
            approved = receipt != null && receipt.status() == ProviderPayment.Status.APPROVED;
            return ledger.record(claim, receipt);
        } finally { if (timer != null) metrics.stopPaymentTimer(timer, approved); }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int recover(int limit) {
        int processed = 0;
        for (UUID id : ledger.due(limit)) {
            var result = execute(id);
            if (result == null) continue;
            if (result.status().equals("APPROVED") || result.status().equals("DECLINED")) settlement.apply(result);
            processed++;
        }
        return processed;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void renew(UUID user, UUID subscription, java.time.LocalDate cycle, int amount) {
        try {
            var payment = charge(user, amount, "renewal|" + subscription + "|" + cycle, "Renovacao de assinatura");
            var o = ledger.get(payment.obligationId());
            if (!o.closed()) settlement.apply(o);
        } catch (PaymentOutcomeUnknownException ignored) {
            // Durable recovery owns the uncertain obligation; broker redelivery must not authorize a new debit.
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void reconcile(UUID id) {
        ledger.wakeForProvider(id);
        var result=execute(id, true);
        if (result!=null && (result.status().equals("APPROVED") || result.status().equals("DECLINED"))) settlement.apply(result);
    }

    @Transactional
    public void markApplied(PaymentResult result, UUID subscription) {
        if (result.obligationId() == null) return;
        var o = ledger.get(result.obligationId());
        if (o.closed() && o.settled()) throw new ConflictException("PAYMENT_ALREADY_APPLIED", "Obrigacao ja aplicada a assinatura.");
        if (!result.owner().equals(o.owner())) throw new ConflictException("PAYMENT_LEASE_LOST", "Pagamento pertence a outro worker.");
        ledger.applied(o, subscription, result.isApproved(), 0);
    }

    @Transactional
    public void resumeCanceledRenewal(UUID user, UUID subscription, java.time.LocalDate cycle) {
        ledger.resumeCanceledRenewal(user, subscription, cycle);
    }

    @Transactional
    public void lockForCancellation(UUID user) { ledger.lockUser(user); }

    @Transactional
    public void requireNoPending(UUID user) { ledger.assertNoPending(user); }

    private PaymentResult result(PaymentObligation o) {
        return new PaymentResult(o.status().equals("APPROVED"), o.status().equals("DECLINED") ? "PAYMENT_DECLINED" : null,
                o.status().equals("DECLINED") ? "Pagamento recusado pelo provedor." : null, o.id(), o.owner());
    }
}
