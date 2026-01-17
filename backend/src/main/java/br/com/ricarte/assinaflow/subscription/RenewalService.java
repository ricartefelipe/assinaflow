package br.com.ricarte.assinaflow.subscription;

import br.com.ricarte.assinaflow.common.time.TimeProvider;
import br.com.ricarte.assinaflow.metrics.BillingMetrics;
import br.com.ricarte.assinaflow.outbox.OutboxEventEntity;
import br.com.ricarte.assinaflow.outbox.OutboxRepository;
import br.com.ricarte.assinaflow.outbox.OutboxStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class RenewalService {

    private static final Logger log = LoggerFactory.getLogger(RenewalService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionRenewalAttemptRepository attemptRepository;
    private final PaymentService paymentService;
    private final TimeProvider timeProvider;
    private final SubscriptionCache subscriptionCache;
    private final BillingMetrics billingMetrics;

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    private final boolean asyncEnabled;

    private final TransactionTemplate requiresNewTx;

    public RenewalService(
            SubscriptionRepository subscriptionRepository,
            SubscriptionRenewalAttemptRepository attemptRepository,
            PaymentService paymentService,
            TimeProvider timeProvider,
            SubscriptionCache subscriptionCache,
            BillingMetrics billingMetrics,
            OutboxRepository outboxRepository,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            @Value("${app.payments.async.enabled:false}") boolean asyncEnabled
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.attemptRepository = attemptRepository;
        this.paymentService = paymentService;
        this.timeProvider = timeProvider;
        this.subscriptionCache = subscriptionCache;
        this.billingMetrics = billingMetrics;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.asyncEnabled = asyncEnabled;

        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Processes up to max items.
     * - Direct mode: charges synchronously and updates subscription.
     * - Async mode: enqueues an outbox event and marks in-flight.
     */
    public int processDueRenewals(int max) {
        int processed = 0;
        for (int i = 0; i < max; i++) {
            Boolean didProcess = requiresNewTx.execute(status -> processOneRenewal());
            if (Boolean.FALSE.equals(didProcess)) {
                break;
            }
            processed++;
        }
        return processed;
    }

    /**
     * Finalizes scheduled cancellations after expiration.
     */
    public int finalizeScheduledCancellations(int max) {
        int processed = 0;
        for (int i = 0; i < max; i++) {
            Boolean did = requiresNewTx.execute(status -> processOneFinalization());
            if (Boolean.FALSE.equals(did)) {
                break;
            }
            processed++;
        }
        return processed;
    }

    private boolean processOneRenewal() {
        Instant now = timeProvider.now();
        LocalDate today = timeProvider.todayUtc();

        List<SubscriptionEntity> batch = subscriptionRepository.lockBatchDueForRenewal(today, now, 1);
        if (batch.isEmpty()) {
            return false;
        }

        SubscriptionEntity s = batch.get(0);
        if (asyncEnabled) {
            enqueuePaymentCharge(s, now);
        } else {
            processLockedSubscriptionDirect(s, now);
        }
        return true;
    }

    private boolean processOneFinalization() {
        LocalDate today = timeProvider.todayUtc();
        List<SubscriptionEntity> batch = subscriptionRepository.lockBatchDueForFinalization(today, 1);
        if (batch.isEmpty()) {
            return false;
        }

        SubscriptionEntity s = batch.get(0);
        s.setStatus(SubscriptionStatus.CANCELADA);
        s.setAutoRenew(false);
        subscriptionRepository.save(s);
        subscriptionCache.evictActive(s.getUserId());

        log.info("finalized cancellation subscriptionId={} userId={}", s.getId(), s.getUserId());
        return true;
    }

    private void enqueuePaymentCharge(SubscriptionEntity s, Instant now) {
        // Defensive: should already be filtered by query
        if (s.getStatus() != SubscriptionStatus.ATIVA || !s.isAutoRenew()) {
            return;
        }

        int attemptNumber = s.getRenewalFailures() + 1;
        int amountCents = s.getPlan().getPriceCents();
        LocalDate cycleExpiration = s.getExpirationDate();

        String idempotencyKey = s.getId() + "|" + cycleExpiration + "|" + attemptNumber;

        try {
            PaymentChargeRequested msg = new PaymentChargeRequested(
                    null,
                    s.getId(),
                    s.getUserId(),
                    cycleExpiration,
                    attemptNumber,
                    amountCents,
                    now
            );

            String payload = objectMapper.writeValueAsString(msg);

            OutboxEventEntity e = new OutboxEventEntity();
            e.setAggregateType("SUBSCRIPTION");
            e.setAggregateId(s.getId());
            e.setEventType("PAYMENT_CHARGE_REQUESTED");
            e.setIdempotencyKey(idempotencyKey);
            e.setPayload(payload);
            e.setStatus(OutboxStatus.PENDING);
            e.setPublishAttempts(0);

            outboxRepository.save(e);
            billingMetrics.outboxEnqueued(e.getEventType());

            // Mark in-flight to prevent repeated enqueueing while message is being processed.
            s.setRenewalInFlightUntil(now.plus(Duration.ofMinutes(10)));
            subscriptionRepository.save(s);

            log.info("enqueued payment charge subscriptionId={} userId={} attempt={}", s.getId(), s.getUserId(), attemptNumber);
        } catch (DataIntegrityViolationException dup) {
            // Idempotency key already exists: another instance enqueued the same attempt.
            s.setRenewalInFlightUntil(now.plus(Duration.ofMinutes(10)));
            subscriptionRepository.save(s);
        } catch (Exception ex) {
            log.warn("failed to enqueue outbox subscriptionId={} error={}", s.getId(), ex.toString());
        }
    }

    private void processLockedSubscriptionDirect(SubscriptionEntity s, Instant now) {
        if (s.getStatus() != SubscriptionStatus.ATIVA || !s.isAutoRenew()) {
            return;
        }

        int attemptNumber = s.getRenewalFailures() + 1;
        int amountCents = s.getPlan().getPriceCents();
        LocalDate cycleExpiration = s.getExpirationDate();

        PaymentResult payment = paymentService.charge(s.getUserId(), amountCents);

        SubscriptionRenewalAttemptEntity attempt = new SubscriptionRenewalAttemptEntity();
        attempt.setSubscriptionId(s.getId());
        attempt.setCycleExpirationDate(cycleExpiration);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setAttemptedAt(now);
        attempt.setAmountCents(amountCents);

        if (payment.approved()) {
            attempt.setResult(RenewalAttemptResult.SUCCESS);
            attemptRepository.save(attempt);
            billingMetrics.renewalAttempt(true, "sync");

            s.setStartDate(cycleExpiration);
            s.setExpirationDate(cycleExpiration.plusMonths(1));
            s.setRenewalFailures(0);
            s.setNextRenewalAttemptAt(null);
            s.setStatus(SubscriptionStatus.ATIVA);
            s.setRenewalInFlightUntil(null);

            subscriptionRepository.save(s);
            subscriptionCache.evictActive(s.getUserId());

            log.info("renewal success subscriptionId={} userId={} newExpiration={}", s.getId(), s.getUserId(), s.getExpirationDate());
            return;
        }

        attempt.setResult(RenewalAttemptResult.FAILURE);
        attempt.setErrorCode(payment.errorCode());
        attempt.setErrorMessage(payment.errorMessage());
        attemptRepository.save(attempt);
        billingMetrics.renewalAttempt(false, "sync");

        s.setRenewalFailures(attemptNumber);

        if (attemptNumber >= 3) {
            s.setStatus(SubscriptionStatus.SUSPENSA);
            s.setAutoRenew(false);
            s.setNextRenewalAttemptAt(null);
            s.setSuspendedAt(now);
            s.setRenewalInFlightUntil(null);
            subscriptionRepository.save(s);
            subscriptionCache.evictActive(s.getUserId());
            billingMetrics.subscriptionSuspended("sync");

            log.warn("renewal failed 3x -> suspended subscriptionId={} userId={}", s.getId(), s.getUserId());
            return;
        }

        Duration backoff = backoffForAttempt(attemptNumber);
        s.setNextRenewalAttemptAt(now.plus(backoff));
        s.setRenewalInFlightUntil(null);
        subscriptionRepository.save(s);
        subscriptionCache.evictActive(s.getUserId());

        log.info("renewal failed subscriptionId={} userId={} attempt={} nextAttemptAt={}",
                s.getId(), s.getUserId(), attemptNumber, s.getNextRenewalAttemptAt());
    }

    private static Duration backoffForAttempt(int attemptNumber) {
        return switch (attemptNumber) {
            case 1 -> Duration.ofMinutes(15);
            case 2 -> Duration.ofMinutes(60);
            default -> Duration.ZERO;
        };
    }
}
