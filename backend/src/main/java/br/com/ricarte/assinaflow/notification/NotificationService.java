package br.com.ricarte.assinaflow.notification;

import br.com.ricarte.assinaflow.metrics.BillingMetrics;
import br.com.ricarte.assinaflow.outbox.OutboxEventEntity;
import br.com.ricarte.assinaflow.outbox.OutboxRepository;
import br.com.ricarte.assinaflow.outbox.OutboxStatus;
import br.com.ricarte.assinaflow.user.UserEntity;
import br.com.ricarte.assinaflow.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    public static final String SUBSCRIPTION_CREATED = "NOTIFICATION_SUBSCRIPTION_CREATED";
    public static final String RENEWAL_SUCCEEDED = "NOTIFICATION_RENEWAL_SUCCEEDED";
    public static final String RENEWAL_FAILED = "NOTIFICATION_RENEWAL_FAILED";
    public static final String SUBSCRIPTION_SUSPENDED = "NOTIFICATION_SUBSCRIPTION_SUSPENDED";
    public static final String PLAN_CHANGED = "NOTIFICATION_PLAN_CHANGED";

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final OutboxRepository outboxRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final BillingMetrics billingMetrics;

    public NotificationService(
            OutboxRepository outboxRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            BillingMetrics billingMetrics
    ) {
        this.outboxRepository = outboxRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.billingMetrics = billingMetrics;
    }

    public void enqueue(
            String eventType,
            UUID userId,
            UUID subscriptionId,
            String subject,
            String body,
            Map<String, String> attributes,
            String idempotencyKey
    ) {
        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getEmail() == null) {
            log.warn("skip notification eventType={} userId={} reason=user_or_email_missing", eventType, userId);
            return;
        }

        try {
            NotificationMessage message = new NotificationMessage(
                    eventType,
                    userId,
                    user.getEmail(),
                    subscriptionId,
                    subject,
                    body,
                    attributes == null ? Map.of() : attributes
            );

            OutboxEventEntity e = new OutboxEventEntity();
            e.setAggregateType("NOTIFICATION");
            e.setAggregateId(subscriptionId != null ? subscriptionId : userId);
            e.setEventType(eventType);
            e.setIdempotencyKey(idempotencyKey);
            e.setPayload(objectMapper.writeValueAsString(message));
            e.setStatus(OutboxStatus.PENDING);
            e.setPublishAttempts(0);
            outboxRepository.save(e);
            billingMetrics.outboxEnqueued(eventType);
        } catch (DataIntegrityViolationException dup) {
            log.debug("notification already enqueued idempotencyKey={}", idempotencyKey);
        } catch (Exception ex) {
            log.warn("failed to enqueue notification eventType={} error={}", eventType, ex.toString());
        }
    }
}
