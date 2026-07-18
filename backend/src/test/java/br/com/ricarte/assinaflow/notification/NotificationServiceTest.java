package br.com.ricarte.assinaflow.notification;

import br.com.ricarte.assinaflow.metrics.BillingMetrics;
import br.com.ricarte.assinaflow.outbox.OutboxEventEntity;
import br.com.ricarte.assinaflow.outbox.OutboxRepository;
import br.com.ricarte.assinaflow.user.UserEntity;
import br.com.ricarte.assinaflow.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    OutboxRepository outboxRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    BillingMetrics billingMetrics;

    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    NotificationService notificationService;

    @Test
    void enqueueShouldPersistOutboxEvent() {
        notificationService = new NotificationService(outboxRepository, userRepository, objectMapper, billingMetrics);

        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("a@b.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(outboxRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        notificationService.enqueue(
                NotificationService.SUBSCRIPTION_CREATED,
                userId,
                subscriptionId,
                "Assinatura ativada",
                "ok",
                Map.of("plano", "PREMIUM"),
                "key-1"
        );

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(NotificationService.SUBSCRIPTION_CREATED);
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("key-1");
        verify(billingMetrics).outboxEnqueued(NotificationService.SUBSCRIPTION_CREATED);
    }
}
