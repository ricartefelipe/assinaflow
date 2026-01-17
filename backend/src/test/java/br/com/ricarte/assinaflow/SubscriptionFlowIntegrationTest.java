package br.com.ricarte.assinaflow;

import br.com.ricarte.assinaflow.subscription.*;
import br.com.ricarte.assinaflow.testutil.MutableTimeProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class SubscriptionFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MutableTimeProvider timeProvider;

    @Autowired
    RenewalService renewalService;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    SubscriptionRenewalAttemptRepository attemptRepository;

    @Test
    void shouldCreateSubscription() throws Exception {
        UUID userId = createUser("alice@example.com", "Alice", "ALWAYS_APPROVE", 0);

        String subJson = """
                {"plano":"PREMIUM","dataInicio":"2025-03-10"}
                """;

        var res = mockMvc.perform(post("/api/v1/users/{userId}/subscriptions", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(subJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId").value(userId.toString()))
                .andExpect(jsonPath("$.plano").value("PREMIUM"))
                .andExpect(jsonPath("$.dataInicio").value("2025-03-10"))
                .andExpect(jsonPath("$.dataExpiracao").value("2025-04-10"))
                .andExpect(jsonPath("$.status").value("ATIVA"))
                .andReturn();

        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("id").asText()).isNotBlank();
    }

    @Test
    void shouldPreventTwoActiveSubscriptions() throws Exception {
        UUID userId = createUser("bob@example.com", "Bob", "ALWAYS_APPROVE", 0);

        String first = """
                {"plano":"BASICO","dataInicio":"2025-03-10"}
                """;
        String second = """
                {"plano":"PREMIUM","dataInicio":"2025-03-15"}
                """;

        mockMvc.perform(post("/api/v1/users/{userId}/subscriptions", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(first))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users/{userId}/subscriptions", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(second))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_ALREADY_ACTIVE"));
    }

    @Test
    void cancelShouldKeepAccessUntilExpirationAndShouldNotRenew() throws Exception {
        UUID userId = createUser("carol@example.com", "Carol", "ALWAYS_APPROVE", 0);

        String sub = """
                {"plano":"PREMIUM","dataInicio":"2025-03-10"}
                """;

        mockMvc.perform(post("/api/v1/users/{userId}/subscriptions", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sub))
                .andExpect(status().isCreated());

        // Cancel on 2025-03-15
        timeProvider.setNow(Instant.parse("2025-03-15T10:00:00Z"));

        mockMvc.perform(post("/api/v1/users/{userId}/subscriptions/cancel", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELAMENTO_AGENDADO"))
                .andExpect(jsonPath("$.autoRenew").value(false))
                .andExpect(jsonPath("$.dataExpiracao").value("2025-04-10"));

        // On due date, renewal processor should ignore cancelled subscriptions.
        timeProvider.setNow(Instant.parse("2025-04-10T00:00:00Z"));
        renewalService.processDueRenewals(10);

        SubscriptionEntity s = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId).get(0);
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.CANCELAMENTO_AGENDADO);
        assertThat(s.getExpirationDate()).isEqualTo(java.time.LocalDate.parse("2025-04-10"));

        // Finalize cancellation at/after expiration
        renewalService.finalizeScheduledCancellations(10);

        SubscriptionEntity s2 = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId).get(0);
        assertThat(s2.getStatus()).isEqualTo(SubscriptionStatus.CANCELADA);
    }

    @Test
    void shouldRenewOnDueDateWhenPaymentSucceeds() throws Exception {
        UUID userId = createUser("dave@example.com", "Dave", "ALWAYS_APPROVE", 0);

        String sub = """
                {"plano":"FAMILIA","dataInicio":"2025-03-10"}
                """;

        mockMvc.perform(post("/api/v1/users/{userId}/subscriptions", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sub))
                .andExpect(status().isCreated());

        timeProvider.setNow(Instant.parse("2025-04-10T00:00:00Z"));
        renewalService.processDueRenewals(10);

        SubscriptionEntity s = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId).get(0);
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ATIVA);
        assertThat(s.getStartDate()).isEqualTo(java.time.LocalDate.parse("2025-04-10"));
        assertThat(s.getExpirationDate()).isEqualTo(java.time.LocalDate.parse("2025-05-10"));
        assertThat(s.getRenewalFailures()).isEqualTo(0);

        long attempts = attemptRepository.countBySubscriptionIdAndCycleExpirationDate(
                s.getId(), java.time.LocalDate.parse("2025-04-10"));
        assertThat(attempts).isEqualTo(1);
    }

    @Test
    void shouldSuspendAfterThreeFailedRenewalAttempts() throws Exception {
        UUID userId = createUser("erin@example.com", "Erin", "ALWAYS_DECLINE", 0);

        String sub = """
                {"plano":"BASICO","dataInicio":"2025-03-10"}
                """;

        mockMvc.perform(post("/api/v1/users/{userId}/subscriptions", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sub))
                .andExpect(status().isCreated());

        // Attempt 1
        timeProvider.setNow(Instant.parse("2025-04-10T00:00:00Z"));
        renewalService.processDueRenewals(10);

        SubscriptionEntity s1 = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId).get(0);
        assertThat(s1.getRenewalFailures()).isEqualTo(1);
        assertThat(s1.getStatus()).isEqualTo(SubscriptionStatus.ATIVA);
        assertThat(s1.getNextRenewalAttemptAt()).isEqualTo(Instant.parse("2025-04-10T00:15:00Z"));

        // Attempt 2
        timeProvider.setNow(Instant.parse("2025-04-10T00:15:00Z"));
        renewalService.processDueRenewals(10);

        SubscriptionEntity s2 = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId).get(0);
        assertThat(s2.getRenewalFailures()).isEqualTo(2);
        assertThat(s2.getStatus()).isEqualTo(SubscriptionStatus.ATIVA);
        assertThat(s2.getNextRenewalAttemptAt()).isEqualTo(Instant.parse("2025-04-10T01:15:00Z"));

        // Attempt 3
        timeProvider.setNow(Instant.parse("2025-04-10T01:15:00Z"));
        renewalService.processDueRenewals(10);

        SubscriptionEntity s3 = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId).get(0);
        assertThat(s3.getRenewalFailures()).isEqualTo(3);
        assertThat(s3.getStatus()).isEqualTo(SubscriptionStatus.SUSPENSA);
        assertThat(s3.isAutoRenew()).isFalse();
        assertThat(s3.getNextRenewalAttemptAt()).isNull();

        long attempts = attemptRepository.countBySubscriptionIdAndCycleExpirationDate(
                s3.getId(), java.time.LocalDate.parse("2025-04-10"));
        assertThat(attempts).isEqualTo(3);
    }

    @Test
    void shouldNotRenewTwiceWithTwoParallelExecutions() throws Exception {
        UUID userId = createUser("frank@example.com", "Frank", "ALWAYS_APPROVE", 0);

        String sub = """
                {"plano":"PREMIUM","dataInicio":"2025-03-10"}
                """;

        mockMvc.perform(post("/api/v1/users/{userId}/subscriptions", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sub))
                .andExpect(status().isCreated());

        timeProvider.setNow(Instant.parse("2025-04-10T00:00:00Z"));

        ExecutorService exec = Executors.newFixedThreadPool(2);
        Callable<Integer> call = () -> renewalService.processDueRenewals(1);

        Future<Integer> f1 = exec.submit(call);
        Future<Integer> f2 = exec.submit(call);

        int p1 = f1.get();
        int p2 = f2.get();
        exec.shutdown();

        assertThat(p1 + p2).isEqualTo(1);

        SubscriptionEntity s = subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId).get(0);
        assertThat(s.getExpirationDate()).isEqualTo(java.time.LocalDate.parse("2025-05-10"));

        long attempts = attemptRepository.countBySubscriptionIdAndCycleExpirationDate(
                s.getId(), java.time.LocalDate.parse("2025-04-10"));
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
