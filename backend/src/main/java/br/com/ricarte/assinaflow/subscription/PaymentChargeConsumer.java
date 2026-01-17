package br.com.ricarte.assinaflow.subscription;

import br.com.ricarte.assinaflow.common.time.TimeProvider;
import br.com.ricarte.assinaflow.metrics.BillingMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

@Component
@ConditionalOnProperty(name = "app.payments.async.enabled", havingValue = "true")
public class PaymentChargeConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentChargeConsumer.class);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionRenewalAttemptRepository attemptRepository;
    private final PaymentService paymentService;
    private final TimeProvider timeProvider;
    private final SubscriptionCache subscriptionCache;
    private final BillingMetrics billingMetrics;

    public PaymentChargeConsumer(
            SubscriptionRepository subscriptionRepository,
            SubscriptionRenewalAttemptRepository attemptRepository,
            PaymentService paymentService,
            TimeProvider timeProvider,
            SubscriptionCache subscriptionCache,
            BillingMetrics billingMetrics
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.attemptRepository = attemptRepository;
        this.paymentService = paymentService;
        this.timeProvider = timeProvider;
        this.subscriptionCache = subscriptionCache;
        this.billingMetrics = billingMetrics;
    }

    @RabbitListener(queues = "${app.rabbitmq.payments.queue:payments.charge}")
    @Transactional
    public void onMessage(PaymentChargeRequested msg) {
        SubscriptionEntity s = subscriptionRepository.findByIdForUpdate(msg.subscriptionId()).orElse(null);
        if (s == null) {
            return;
        }

        // If subscription is no longer eligible (canceled, suspended, etc.), clear in-flight and stop.
        if (s.getStatus() != SubscriptionStatus.ATIVA || !s.isAutoRenew()) {
            s.setRenewalInFlightUntil(null);
            subscriptionRepository.save(s);
            subscriptionCache.evictActive(s.getUserId());
            return;
        }

        // Guard: only process if cycle still matches
        LocalDate currentCycleExpiration = s.getExpirationDate();
        if (!currentCycleExpiration.equals(msg.cycleExpirationDate())) {
            s.setRenewalInFlightUntil(null);
            subscriptionRepository.save(s);
            return;
        }

        // Idempotency: if already processed, release in-flight and stop.
        if (attemptRepository.existsBySubscriptionIdAndCycleExpirationDateAndAttemptNumber(
                s.getId(), msg.cycleExpirationDate(), msg.attemptNumber())) {
            s.setRenewalInFlightUntil(null);
            subscriptionRepository.save(s);
            return;
        }

        Instant now = timeProvider.now();
        int amountCents = msg.amountCents();

        PaymentResult payment = paymentService.charge(s.getUserId(), amountCents);

        SubscriptionRenewalAttemptEntity attempt = new SubscriptionRenewalAttemptEntity();
        attempt.setSubscriptionId(s.getId());
        attempt.setCycleExpirationDate(msg.cycleExpirationDate());
        attempt.setAttemptNumber(msg.attemptNumber());
        attempt.setAttemptedAt(now);
        attempt.setAmountCents(amountCents);

        if (payment.approved()) {
            attempt.setResult(RenewalAttemptResult.SUCCESS);
            attemptRepository.save(attempt);
            billingMetrics.renewalAttempt(true, "async");

            // Renewal: [start, expiration) -> move boundary
            s.setStartDate(currentCycleExpiration);
            s.setExpirationDate(currentCycleExpiration.plusMonths(1));
            s.setRenewalFailures(0);
            s.setNextRenewalAttemptAt(null);
            s.setRenewalInFlightUntil(null);

            subscriptionRepository.save(s);
            subscriptionCache.evictActive(s.getUserId());

            log.info("payment approved -> renewed subscriptionId={} userId={} newExpiration={}", s.getId(), s.getUserId(), s.getExpirationDate());
            return;
        }

        attempt.setResult(RenewalAttemptResult.FAILURE);
        attempt.setErrorCode(payment.errorCode());
        attempt.setErrorMessage(payment.errorMessage());
        attemptRepository.save(attempt);
        billingMetrics.renewalAttempt(false, "async");

        int attemptNumber = msg.attemptNumber();
        s.setRenewalFailures(attemptNumber);

        if (attemptNumber >= 3) {
            s.setStatus(SubscriptionStatus.SUSPENSA);
            s.setAutoRenew(false);
            s.setNextRenewalAttemptAt(null);
            s.setSuspendedAt(now);
            s.setRenewalInFlightUntil(null);
            subscriptionRepository.save(s);
            subscriptionCache.evictActive(s.getUserId());
            billingMetrics.subscriptionSuspended("async");

            log.warn("payment declined 3x -> suspended subscriptionId={} userId={}", s.getId(), s.getUserId());
            return;
        }

        Duration backoff = backoffForAttempt(attemptNumber);
        s.setNextRenewalAttemptAt(now.plus(backoff));
        s.setRenewalInFlightUntil(null);
        subscriptionRepository.save(s);
        subscriptionCache.evictActive(s.getUserId());

        log.info("payment declined subscriptionId={} userId={} attempt={} nextAttemptAt={}",
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
