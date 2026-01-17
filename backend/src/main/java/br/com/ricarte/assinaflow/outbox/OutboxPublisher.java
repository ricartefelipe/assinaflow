package br.com.ricarte.assinaflow.outbox;

import br.com.ricarte.assinaflow.metrics.BillingMetrics;
import br.com.ricarte.assinaflow.subscription.PaymentChargeRequested;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final BillingMetrics billingMetrics;

    private final String exchange;
    private final String routingKey;

    private final int maxPublishAttempts;

    private final TransactionTemplate requiresNewTx;

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            BillingMetrics billingMetrics,
            PlatformTransactionManager transactionManager,
            @Value("${app.rabbitmq.payments.exchange:payments.exchange}") String exchange,
            @Value("${app.rabbitmq.payments.routingKey:payments.charge}") String routingKey,
            @Value("${app.outbox.publisher.maxAttempts:10}") int maxPublishAttempts
    ) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.billingMetrics = billingMetrics;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.maxPublishAttempts = Math.max(1, maxPublishAttempts);

        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Publishes PENDING events that are ready (next_attempt_at <= now).
     *
     * Delivery model:
     * - at-least-once publication to the broker
     * - consumer MUST be idempotent
     *
     * Failure handling:
     * - backoff is deterministic
     * - after max attempts, the record is marked DEAD
     */
    public int publishPending(int max) {
        return requiresNewTx.execute(status -> {
            Instant now = Instant.now();
            List<OutboxEventEntity> events = outboxRepository.lockPendingReady(now, max);
            int published = 0;

            for (OutboxEventEntity e : events) {
                if (publishOne(e, now)) {
                    published++;
                }
            }

            return published;
        });
    }

    private boolean publishOne(OutboxEventEntity e, Instant now) {
        int attempt = e.getPublishAttempts() + 1;

        try {
            PaymentChargeRequested msg = objectMapper.readValue(e.getPayload(), PaymentChargeRequested.class);
            rabbitTemplate.convertAndSend(exchange, routingKey, msg);

            e.setStatus(OutboxStatus.SENT);
            e.setSentAt(now);
            e.setPublishAttempts(attempt);
            e.setLastError(null);
            e.setNextAttemptAt(now);
            outboxRepository.save(e);

            billingMetrics.outboxPublish(true);
            return true;
        } catch (Exception ex) {
            billingMetrics.outboxPublish(false);

            e.setPublishAttempts(attempt);
            e.setLastError(truncate(ex.toString(), 1000));

            if (attempt >= maxPublishAttempts) {
                e.setStatus(OutboxStatus.DEAD);
                e.setDeadAt(now);
                e.setNextAttemptAt(now);
                outboxRepository.save(e);
                billingMetrics.outboxDeadLetter();

                log.error("outbox DEAD eventId={} attempts={} error={}", e.getId(), attempt, ex.toString());
                return false;
            }

            Duration backoff = publishBackoff(attempt);
            e.setNextAttemptAt(now.plus(backoff));
            outboxRepository.save(e);

            log.warn("outbox publish failed eventId={} attempts={} nextAttemptAt={} error={}",
                    e.getId(), attempt, e.getNextAttemptAt(), ex.toString());
            return false;
        }
    }

    private static Duration publishBackoff(int attempt) {
        // Deterministic backoff (avoid jitter to keep behavior predictable).
        return switch (attempt) {
            case 1 -> Duration.ofSeconds(1);
            case 2 -> Duration.ofSeconds(5);
            case 3 -> Duration.ofSeconds(15);
            case 4 -> Duration.ofMinutes(1);
            case 5 -> Duration.ofMinutes(5);
            default -> Duration.ofMinutes(15);
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
