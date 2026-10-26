package br.com.ricarte.assinaflow;

import br.com.ricarte.assinaflow.outbox.OutboxPublisher;
import br.com.ricarte.assinaflow.subscription.SubscriptionEntity;
import br.com.ricarte.assinaflow.subscription.SubscriptionRepository;
import br.com.ricarte.assinaflow.subscription.SubscriptionRenewalAttemptRepository;
import br.com.ricarte.assinaflow.testutil.MutableTimeProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@Testcontainers
@SpringBootTest(classes = {AssinaFlowApplication.class, br.com.ricarte.assinaflow.testutil.TestTimeProviderConfig.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.scheduler.enabled=false",
        "app.payments.async.enabled=true",
        "spring.cache.type=simple"
})
public class AsyncPaymentsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(org.testcontainers.utility.DockerImageName.parse(
                    System.getProperty("test.postgres.image", "postgres:16-alpine")).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("subscriptions")
            .withUsername("subscriptions")
            .withPassword("subscriptions");

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer(org.testcontainers.utility.DockerImageName.parse(
                    System.getProperty("test.rabbitmq.image", "rabbitmq:3-management-alpine"))
                    .asCompatibleSubstituteFor("rabbitmq"))
            .withStartupTimeout(java.time.Duration.ofMinutes(3));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    @Autowired
    br.com.ricarte.assinaflow.auth.JwtService jwtService;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MutableTimeProvider timeProvider;

    @Autowired
    br.com.ricarte.assinaflow.subscription.RenewalService renewalService;

    @Autowired
    OutboxPublisher outboxPublisher;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    SubscriptionRenewalAttemptRepository attemptRepository;

    @Test
    void asyncFlowShouldEnqueuePublishAndRenew() throws Exception {
        UUID userId = createUser("async@example.com", "Async", "ALWAYS_APPROVE", 0);

        String sub = """
                {"plano":"PREMIUM","dataInicio":"2025-03-10"}
                """;

        mockMvc.perform(post("/api/v1/users/{userId}/subscriptions", userId)
                        .header("Authorization", "Bearer " + jwtService.createToken(userId, "async@example.com", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sub))
                .andExpect(status().isCreated());

        timeProvider.setNow(Instant.parse("2025-04-10T00:00:00Z"));

        // Enqueue outbox event
        int enqueued = renewalService.processDueRenewals(10);
        assertThat(enqueued).isEqualTo(1);

        listeners.stop();
        try {
            assertThat(outboxPublisher.publishPending(10)).isEqualTo(2);
            com.rabbitmq.client.ConnectionFactory factory = new com.rabbitmq.client.ConnectionFactory();
            factory.setHost(rabbit.getHost());
            factory.setPort(rabbit.getAmqpPort());
            factory.setUsername("guest");
            factory.setPassword("guest");
            br.com.ricarte.assinaflow.subscription.PaymentChargeRequested replay;
            try (var connection = factory.newConnection()) {
                try (var channel = connection.createChannel()) {
                    var delivery = channel.basicGet("payments.charge", false);
                    assertThat(delivery).isNotNull();
                    replay = objectMapper.readValue(delivery.getBody(),
                            br.com.ricarte.assinaflow.subscription.PaymentChargeRequested.class);
                    // Closing an unacknowledged channel requeues the actual broker delivery.
                }
                try (var channel = connection.createChannel()) {
                    var redelivery = channel.basicGet("payments.charge", false);
                    assertThat(redelivery).isNotNull();
                    assertThat(redelivery.getEnvelope().isRedeliver()).isTrue();
                    assertThat(objectMapper.readTree(redelivery.getBody()))
                            .isEqualTo(objectMapper.valueToTree(replay));
                    // Leave this delivery unacknowledged for the application after restart.
                }
            }
            listeners.start();
            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() ->
                    assertThat(subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId).get(0).getExpirationDate())
                            .isEqualTo(java.time.LocalDate.parse("2025-05-10")));
            // The same reserved obligation is delivered repeatedly through the real broker.
            for (int i = 0; i < 10; i++)
                rabbitTemplate.convertAndSend("payments.exchange", "payments.charge", replay);
            listeners.stop();
            listeners.start();
            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                var queue = rabbitTemplate.execute(channel -> channel.queueDeclarePassive("payments.charge"));
                assertThat(queue.getMessageCount()).isZero();
                assertThat(jdbc.queryForObject("SELECT sum(effects) FROM simulated_provider_payments WHERE user_id=?",
                        Integer.class, userId)).isEqualTo(2); // Initial subscription plus one renewal.
                assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_executions e JOIN payment_obligations o ON o.id=e.obligation_id WHERE o.user_id=? AND o.kind='RENEWAL' AND e.operation='CHARGE'",
                        Integer.class, userId)).isEqualTo(1);
            });
            var subscription = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId).get(0);
            assertThat(attemptRepository.countBySubscriptionIdAndCycleExpirationDate(subscription.getId(),
                    java.time.LocalDate.parse("2025-04-10"))).isEqualTo(1);
        } finally {
            listeners.start();
        }
    }

    @Autowired
    org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry listeners;

    @Autowired
    org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    private UUID createUser(String email, String nome, String behavior, int failNextN) throws Exception {
        String json = String.format("""
                {"email":"%s","nome":"%s","paymentProfile":{"behavior":"%s","failNextN":%d}}
                """, email, nome, behavior, failNextN);

        var res = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }
}
