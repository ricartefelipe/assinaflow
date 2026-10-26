package br.com.ricarte.assinaflow.subscription;

import br.com.ricarte.assinaflow.common.time.TimeProvider;
import br.com.ricarte.assinaflow.outbox.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class RenewalService {
    private final br.com.ricarte.assinaflow.metrics.BillingMetrics metrics;
    private final SubscriptionRepository subscriptions;
    private final br.com.ricarte.assinaflow.payment.PaymentLedger ledger;
    private final PaymentService payments;
    private final ProrationService proration;
    private final TimeProvider time;
    private final SubscriptionCache cache;
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final boolean async;
    private final TransactionTemplate tx;

    public RenewalService(SubscriptionRepository subscriptions, PaymentService payments, ProrationService proration,
                          TimeProvider time, SubscriptionCache cache, OutboxRepository outbox, ObjectMapper mapper,
                          PlatformTransactionManager manager, br.com.ricarte.assinaflow.metrics.BillingMetrics metrics, br.com.ricarte.assinaflow.payment.PaymentLedger ledger, @Value("${app.payments.async.enabled:false}") boolean async) {
        this.subscriptions = subscriptions; this.payments = payments; this.proration = proration; this.time = time;
        this.cache = cache; this.metrics = metrics; this.ledger = ledger; this.outbox = outbox; this.mapper = mapper; this.async = async;
        tx = new TransactionTemplate(manager); tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private record Due(UUID id, UUID user, LocalDate cycle, int amount) { }

    public int processDueRenewals(int max) {
        int processed = 0;
        for (int i = 0; i < max; i++) {
            Due due = tx.execute(status -> {
                var batch = subscriptions.lockBatchDueForRenewal(time.todayUtc(), time.now(), 1);
                if (batch.isEmpty()) return null;
                var s = batch.getFirst();
                var item = new Due(s.getId(), s.getUserId(), s.getExpirationDate(), proration.renewalAmountCents(s.getPlan(), s.getRenewalCreditCents()));
                ledger.reserveRenewal(s, item.amount());
                if (async) enqueue(s, item);
                s.setRenewalInFlightUntil(time.now().plus(Duration.ofMinutes(10)));
                subscriptions.save(s);
                return item;
            });
            if (due == null) break;
            // The reservation transaction is committed before reaching the provider.
            if (!async) payments.renew(due.user(), due.id(), due.cycle(), due.amount());
            processed++;
        }
        return processed;
    }

    private void enqueue(SubscriptionEntity s, Due due) {
        String key = s.getId() + "|" + s.getExpirationDate() + "|" + (s.getRenewalFailures() + 1);
        var existing = outbox.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            var event = existing.get();
            // Redelivery is safe; financial recovery is owned by the durable obligation, not the outbox row.
            event.setStatus(OutboxStatus.PENDING); event.setPublishAttempts(0); event.setNextAttemptAt(time.now());
            event.setDeadAt(null); event.setLastError(null); event.setSentAt(null); outbox.save(event);
            metrics.outboxEnqueued(event.getEventType());
            return;
        }
        var event = new OutboxEventEntity();
        event.setAggregateType("SUBSCRIPTION"); event.setAggregateId(s.getId()); event.setEventType("PAYMENT_CHARGE_REQUESTED");
        event.setIdempotencyKey(key); event.setStatus(OutboxStatus.PENDING); event.setPublishAttempts(0);
        try {
            event.setPayload(mapper.writeValueAsString(new PaymentChargeRequested(null, s.getId(), s.getUserId(), due.cycle(),
                    s.getRenewalFailures() + 1, due.amount(), time.now())));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException(ex); }
        outbox.save(event);
        metrics.outboxEnqueued(event.getEventType());
    }

    public int finalizeScheduledCancellations(int max) {
        int processed = 0;
        for (int i = 0; i < max; i++) {
            Boolean did = tx.execute(status -> {
                var batch = subscriptions.lockBatchDueForFinalization(time.todayUtc(), 1);
                if (batch.isEmpty()) return false;
                var s = batch.getFirst(); s.setStatus(SubscriptionStatus.CANCELADA); s.setAutoRenew(false);
                subscriptions.save(s); cache.evictActive(s.getUserId()); return true;
            });
            if (!Boolean.TRUE.equals(did)) break;
            processed++;
        }
        return processed;
    }
}
