package br.com.ricarte.assinaflow;

import br.com.ricarte.assinaflow.outbox.OutboxEventEntity;
import br.com.ricarte.assinaflow.outbox.OutboxPublisher;
import br.com.ricarte.assinaflow.outbox.OutboxRepository;
import br.com.ricarte.assinaflow.outbox.OutboxStatus;
import br.com.ricarte.assinaflow.subscription.PaymentChargeRequested;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(classes = {AssinaFlowApplication.class})
@TestPropertySource(properties = {
        "app.scheduler.enabled=false",
        "app.payments.async.enabled=true",
        "spring.cache.type=simple",
        "app.outbox.publisher.maxAttempts=3",
        "spring.rabbitmq.host=127.0.0.1",
        "spring.rabbitmq.port=1",
        "spring.rabbitmq.username=guest",
        "spring.rabbitmq.password=guest",
        "spring.rabbitmq.connection-timeout=500ms"
})
public class OutboxRetryDeadLetterIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("subscriptions")
            .withUsername("subscriptions")
            .withPassword("subscriptions");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    OutboxPublisher outboxPublisher;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void shouldMarkEventAsDeadAfterMaxPublishAttemptsWhenBrokerDown() throws Exception {
        UUID outboxEventId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        LocalDate cycleExpirationDate = LocalDate.parse("2025-04-10");
        int attemptNumber = 1;
        int amountCents = 3990;
        Instant requestedAt = Instant.parse("2025-04-10T00:00:00Z");

        PaymentChargeRequested msg = new PaymentChargeRequested(
                outboxEventId,
                subscriptionId,
                userId,
                cycleExpirationDate,
                attemptNumber,
                amountCents,
                requestedAt
        );

        String payload = objectMapper.writeValueAsString(msg);

        OutboxEventEntity e = new OutboxEventEntity();
        e.setId(outboxEventId);
        e.setAggregateType("subscription");
        e.setAggregateId(subscriptionId);
        e.setEventType("PAYMENT_CHARGE_REQUESTED");
        e.setIdempotencyKey(subscriptionId + ":" + cycleExpirationDate + ":" + attemptNumber);
        e.setPayload(payload);
        e.setStatus(OutboxStatus.PENDING);
        e.setPublishAttempts(0);
        e.setNextAttemptAt(Instant.EPOCH);

        outboxRepository.save(e);

        // Attempt 1: fails to publish (Rabbit is unreachable)
        int published1 = outboxPublisher.publishPending(10);
        assertThat(published1).isEqualTo(0);

        OutboxEventEntity a1 = outboxRepository.findById(outboxEventId).orElseThrow();
        assertThat(a1.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(a1.getPublishAttempts()).isEqualTo(1);
        assertThat(a1.getLastError()).isNotBlank();
        assertThat(a1.getDeadAt()).isNull();

        // Force immediate retry eligibility (avoid waiting for backoff)
        a1.setNextAttemptAt(Instant.EPOCH);
        outboxRepository.save(a1);

        // Attempt 2: still fails
        int published2 = outboxPublisher.publishPending(10);
        assertThat(published2).isEqualTo(0);

        OutboxEventEntity a2 = outboxRepository.findById(outboxEventId).orElseThrow();
        assertThat(a2.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(a2.getPublishAttempts()).isEqualTo(2);
        assertThat(a2.getDeadAt()).isNull();

        a2.setNextAttemptAt(Instant.EPOCH);
        outboxRepository.save(a2);

        // Attempt 3: must DEAD-letter
        int published3 = outboxPublisher.publishPending(10);
        assertThat(published3).isEqualTo(0);

        OutboxEventEntity a3 = outboxRepository.findById(outboxEventId).orElseThrow();
        assertThat(a3.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(a3.getPublishAttempts()).isEqualTo(3);
        assertThat(a3.getDeadAt()).isNotNull();
        assertThat(a3.getLastError()).isNotBlank();
    }
}
