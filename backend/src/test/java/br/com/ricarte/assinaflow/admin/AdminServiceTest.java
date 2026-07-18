package br.com.ricarte.assinaflow.admin;

import br.com.ricarte.assinaflow.common.time.TimeProvider;
import br.com.ricarte.assinaflow.outbox.OutboxEventEntity;
import br.com.ricarte.assinaflow.outbox.OutboxRepository;
import br.com.ricarte.assinaflow.outbox.OutboxStatus;
import br.com.ricarte.assinaflow.subscription.SubscriptionRepository;
import br.com.ricarte.assinaflow.user.UserRepository;
import br.com.ricarte.assinaflow.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    SubscriptionRepository subscriptionRepository;

    @Mock
    OutboxRepository outboxRepository;

    @Mock
    UserService userService;

    @Mock
    TimeProvider timeProvider;

    @InjectMocks
    AdminService adminService;

    @Test
    void requeueShouldResetDeadEvent() {
        UUID id = UUID.randomUUID();
        OutboxEventEntity e = new OutboxEventEntity();
        e.setId(id);
        e.setStatus(OutboxStatus.DEAD);
        e.setPublishAttempts(10);

        when(outboxRepository.findById(id)).thenReturn(Optional.of(e));
        when(timeProvider.now()).thenReturn(Instant.parse("2025-03-10T12:00:00Z"));
        when(outboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OutboxEventEntity result = adminService.requeueOutbox(id);
        assertThat(result.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(result.getPublishAttempts()).isEqualTo(0);
        verify(outboxRepository).save(e);
    }
}
