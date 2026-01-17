package br.com.ricarte.assinaflow.subscription;

import br.com.ricarte.assinaflow.common.exception.ConflictException;
import br.com.ricarte.assinaflow.common.exception.NotFoundException;
import br.com.ricarte.assinaflow.common.time.TimeProvider;
import br.com.ricarte.assinaflow.subscription.dto.CreateSubscriptionRequest;
import br.com.ricarte.assinaflow.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    SubscriptionRepository subscriptionRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    TimeProvider timeProvider;

    @Mock
    SubscriptionCache subscriptionCache;

    @InjectMocks
    SubscriptionService subscriptionService;

    @Test
    void createShouldFailWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(false);

        CreateSubscriptionRequest req = new CreateSubscriptionRequest();
        req.setPlano(Plan.PREMIUM);

        assertThatThrownBy(() -> subscriptionService.create(userId, req))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createShouldFailWhenThereIsActiveSubscription() {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);
        when(subscriptionRepository.existsByUserIdAndStatusIn(eq(userId), any())).thenReturn(true);

        CreateSubscriptionRequest req = new CreateSubscriptionRequest();
        req.setPlano(Plan.PREMIUM);

        assertThatThrownBy(() -> subscriptionService.create(userId, req))
                .isInstanceOf(ConflictException.class);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void cancelShouldBeIdempotent() {
        UUID userId = UUID.randomUUID();
        SubscriptionEntity s = new SubscriptionEntity();
        s.setId(UUID.randomUUID());
        s.setUserId(userId);
        s.setPlan(Plan.BASICO);
        s.setStartDate(LocalDate.parse("2025-03-10"));
        s.setExpirationDate(LocalDate.parse("2025-04-10"));
        s.setStatus(SubscriptionStatus.CANCELAMENTO_AGENDADO);
        s.setAutoRenew(false);

        when(subscriptionRepository.findFirstByUserIdAndStatusIn(eq(userId), any(EnumSet.class)))
                .thenReturn(Optional.of(s));

        var resp = subscriptionService.cancel(userId);
        verify(subscriptionRepository, never()).save(any());
        assertThat(resp.getStatus()).isEqualTo(SubscriptionStatus.CANCELAMENTO_AGENDADO);
    }

    @Test
    void cancelShouldDisableAutoRenew() {
        UUID userId = UUID.randomUUID();
        SubscriptionEntity s = new SubscriptionEntity();
        s.setId(UUID.randomUUID());
        s.setUserId(userId);
        s.setPlan(Plan.BASICO);
        s.setStartDate(LocalDate.parse("2025-03-10"));
        s.setExpirationDate(LocalDate.parse("2025-04-10"));
        s.setStatus(SubscriptionStatus.ATIVA);
        s.setAutoRenew(true);

        when(subscriptionRepository.findFirstByUserIdAndStatusIn(eq(userId), any(EnumSet.class)))
                .thenReturn(Optional.of(s));
        when(timeProvider.now()).thenReturn(Instant.parse("2025-03-15T00:00:00Z"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = subscriptionService.cancel(userId);
        assertThat(resp.getStatus()).isEqualTo(SubscriptionStatus.CANCELAMENTO_AGENDADO);
        assertThat(resp.isAutoRenew()).isFalse();
    }
}
