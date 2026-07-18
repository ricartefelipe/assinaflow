package br.com.ricarte.assinaflow.subscription;

import br.com.ricarte.assinaflow.common.time.TimeProvider;
import br.com.ricarte.assinaflow.metrics.BillingMetrics;
import br.com.ricarte.assinaflow.outbox.OutboxEventEntity;
import br.com.ricarte.assinaflow.outbox.OutboxRepository;
import br.com.ricarte.assinaflow.outbox.OutboxStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenewalServiceOutboxReclaimTest {

    @Mock
    SubscriptionRepository subscriptionRepository;

    @Mock
    SubscriptionRenewalAttemptRepository attemptRepository;

    @Mock
    PaymentService paymentService;

    @Mock
    TimeProvider timeProvider;

    @Mock
    SubscriptionCache subscriptionCache;

    @Mock
    BillingMetrics billingMetrics;

    @Mock
    OutboxRepository outboxRepository;

    @Mock
    PlatformTransactionManager transactionManager;

    @Mock
    TransactionStatus transactionStatus;

    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    RenewalService renewalService;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);

        renewalService = new RenewalService(
                subscriptionRepository,
                attemptRepository,
                paymentService,
                new ProrationService(),
                timeProvider,
                subscriptionCache,
                billingMetrics,
                mock(br.com.ricarte.assinaflow.notification.NotificationService.class),
                outboxRepository,
                objectMapper,
                transactionManager,
                true
        );
    }

    @Test
    void shouldReclaimDeadOutboxWhenEnqueueHitsDuplicateKey() {
        Instant now = Instant.parse("2025-04-12T00:00:00Z");
        LocalDate today = LocalDate.parse("2025-04-12");
        when(timeProvider.now()).thenReturn(now);
        when(timeProvider.todayUtc()).thenReturn(today);

        UUID subscriptionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SubscriptionEntity s = new SubscriptionEntity();
        s.setId(subscriptionId);
        s.setUserId(userId);
        s.setPlan(Plan.PREMIUM);
        s.setStartDate(LocalDate.parse("2025-03-10"));
        s.setExpirationDate(LocalDate.parse("2025-04-10"));
        s.setStatus(SubscriptionStatus.ATIVA);
        s.setAutoRenew(true);
        s.setRenewalFailures(0);

        when(subscriptionRepository.lockBatchDueForRenewal(eq(today), eq(now), eq(1)))
                .thenReturn(List.of(s))
                .thenReturn(List.of());

        OutboxEventEntity dead = new OutboxEventEntity();
        dead.setId(UUID.randomUUID());
        dead.setAggregateType("SUBSCRIPTION");
        dead.setAggregateId(subscriptionId);
        dead.setEventType("PAYMENT_CHARGE_REQUESTED");
        dead.setIdempotencyKey(subscriptionId + "|2025-04-10|1");
        dead.setPayload("{}");
        dead.setStatus(OutboxStatus.DEAD);
        dead.setPublishAttempts(10);
        dead.setDeadAt(now.minusSeconds(60));

        when(outboxRepository.save(any(OutboxEventEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"))
                .thenAnswer(inv -> inv.getArgument(0));

        when(outboxRepository.findByIdempotencyKey(subscriptionId + "|2025-04-10|1"))
                .thenReturn(Optional.of(dead));

        int processed = renewalService.processDueRenewals(2);

        assertThat(processed).isEqualTo(1);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());

        OutboxEventEntity reclaimed = captor.getAllValues().stream()
                .filter(e -> dead.getId().equals(e.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(reclaimed.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reclaimed.getPublishAttempts()).isEqualTo(0);
        assertThat(reclaimed.getDeadAt()).isNull();
        verify(billingMetrics).outboxEnqueued("PAYMENT_CHARGE_REQUESTED");
        verify(paymentService, never()).charge(any(), anyInt());
        verify(subscriptionRepository).save(s);
        assertThat(s.getRenewalInFlightUntil()).isEqualTo(now.plusSeconds(600));
    }
}
