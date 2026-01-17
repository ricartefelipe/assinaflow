package br.com.ricarte.assinaflow;

import br.com.ricarte.assinaflow.outbox.OutboxPublisher;
import br.com.ricarte.assinaflow.subscription.SubscriptionEntity;
import br.com.ricarte.assinaflow.subscription.SubscriptionRepository;
import br.com.ricarte.assinaflow.subscription.SubscriptionRenewalAttemptRepository;
import br.com.ricarte.assinaflow.testutil.MutableTimeProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("subscriptions")
            .withUsername("subscriptions")
            .withPassword("subscriptions");

    @Container
    static final RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine");

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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sub))
                .andExpect(status().isCreated());

        timeProvider.setNow(Instant.parse("2025-04-10T00:00:00Z"));

        // Enqueue outbox event
        int enqueued = renewalService.processDueRenewals(10);
        assertThat(enqueued).isEqualTo(1);

        // Publish to Rabbit
        int published = outboxPublisher.publishPending(10);
        assertThat(published).isEqualTo(1);

        // Wait for consumer to process message and renew
        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            SubscriptionEntity s = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId).get(0);
            assertThat(s.getExpirationDate()).isEqualTo(java.time.LocalDate.parse("2025-05-10"));
        });

        SubscriptionEntity s = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId).get(0);
        long attempts = attemptRepository.countBySubscriptionIdAndCycleExpirationDate(s.getId(), java.time.LocalDate.parse("2025-04-10"));
        assertThat(attempts).isEqualTo(1);
    }

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
