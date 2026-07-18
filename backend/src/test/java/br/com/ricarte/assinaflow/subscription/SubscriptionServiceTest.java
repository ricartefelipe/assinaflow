package br.com.ricarte.assinaflow.subscription;

import br.com.ricarte.assinaflow.common.exception.BadRequestException;
import br.com.ricarte.assinaflow.common.exception.ConflictException;
import br.com.ricarte.assinaflow.common.exception.NotFoundException;
import br.com.ricarte.assinaflow.common.time.TimeProvider;
import br.com.ricarte.assinaflow.subscription.dto.ChangePlanRequest;
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

    @Test
    void getActiveShouldFailWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> subscriptionService.getActive(userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Usuario");
    }

    @Test
    void getActiveShouldReturnNullWhenUserHasNoActiveSubscription() {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);
        when(subscriptionRepository.findFirstByUserIdAndStatusIn(eq(userId), any(EnumSet.class)))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
                userId, SubscriptionStatus.SUSPENSA))
                .thenReturn(Optional.empty());

        assertThat(subscriptionService.getActive(userId)).isNull();
    }

    @Test
    void getActiveShouldFallbackToSuspended() {
        UUID userId = UUID.randomUUID();
        SubscriptionEntity s = new SubscriptionEntity();
        s.setId(UUID.randomUUID());
        s.setUserId(userId);
        s.setPlan(Plan.PREMIUM);
        s.setStartDate(LocalDate.parse("2025-03-10"));
        s.setExpirationDate(LocalDate.parse("2025-04-10"));
        s.setStatus(SubscriptionStatus.SUSPENSA);
        s.setAutoRenew(false);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(subscriptionRepository.findFirstByUserIdAndStatusIn(eq(userId), any(EnumSet.class)))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
                userId, SubscriptionStatus.SUSPENSA))
                .thenReturn(Optional.of(s));

        var resp = subscriptionService.getActive(userId);
        assertThat(resp).isNotNull();
        assertThat(resp.getStatus()).isEqualTo(SubscriptionStatus.SUSPENSA);
    }

    @Test
    void resumeShouldRestoreActiveAutoRenew() {
        UUID userId = UUID.randomUUID();
        SubscriptionEntity s = new SubscriptionEntity();
        s.setId(UUID.randomUUID());
        s.setUserId(userId);
        s.setPlan(Plan.BASICO);
        s.setStartDate(LocalDate.parse("2025-03-10"));
        s.setExpirationDate(LocalDate.parse("2025-04-10"));
        s.setStatus(SubscriptionStatus.CANCELAMENTO_AGENDADO);
        s.setAutoRenew(false);
        s.setCancelRequestedAt(Instant.parse("2025-03-15T00:00:00Z"));

        when(userRepository.existsById(userId)).thenReturn(true);
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
                userId, SubscriptionStatus.CANCELAMENTO_AGENDADO))
                .thenReturn(Optional.of(s));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = subscriptionService.resume(userId);
        assertThat(resp.getStatus()).isEqualTo(SubscriptionStatus.ATIVA);
        assertThat(resp.isAutoRenew()).isTrue();
        verify(subscriptionCache).evictActive(userId);
    }

    @Test
    void reactivateShouldOpenNewCycleWhenExpired() {
        UUID userId = UUID.randomUUID();
        SubscriptionEntity s = new SubscriptionEntity();
        s.setId(UUID.randomUUID());
        s.setUserId(userId);
        s.setPlan(Plan.PREMIUM);
        s.setStartDate(LocalDate.parse("2025-03-10"));
        s.setExpirationDate(LocalDate.parse("2025-04-10"));
        s.setStatus(SubscriptionStatus.SUSPENSA);
        s.setAutoRenew(false);
        s.setRenewalFailures(3);
        s.setSuspendedAt(Instant.parse("2025-04-10T02:00:00Z"));

        when(userRepository.existsById(userId)).thenReturn(true);
        when(subscriptionRepository.existsByUserIdAndStatusIn(eq(userId), any())).thenReturn(false);
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
                userId, SubscriptionStatus.SUSPENSA))
                .thenReturn(Optional.of(s));
        when(timeProvider.todayUtc()).thenReturn(LocalDate.parse("2025-04-12"));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = subscriptionService.reactivate(userId);
        assertThat(resp.getStatus()).isEqualTo(SubscriptionStatus.ATIVA);
        assertThat(resp.isAutoRenew()).isTrue();
        assertThat(resp.getRenewalFailures()).isEqualTo(0);
        assertThat(resp.getDataInicio()).isEqualTo(LocalDate.parse("2025-04-12"));
        assertThat(resp.getDataExpiracao()).isEqualTo(LocalDate.parse("2025-05-12"));
    }

    @Test
    void changePlanShouldUpdateActiveSubscription() {
        UUID userId = UUID.randomUUID();
        SubscriptionEntity s = new SubscriptionEntity();
        s.setId(UUID.randomUUID());
        s.setUserId(userId);
        s.setPlan(Plan.BASICO);
        s.setStartDate(LocalDate.parse("2025-03-10"));
        s.setExpirationDate(LocalDate.parse("2025-04-10"));
        s.setStatus(SubscriptionStatus.ATIVA);
        s.setAutoRenew(true);

        ChangePlanRequest req = new ChangePlanRequest();
        req.setPlano(Plan.PREMIUM);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
                userId, SubscriptionStatus.ATIVA))
                .thenReturn(Optional.of(s));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = subscriptionService.changePlan(userId, req);
        assertThat(resp.getPlano()).isEqualTo(Plan.PREMIUM);
        assertThat(resp.getDataExpiracao()).isEqualTo(LocalDate.parse("2025-04-10"));
        verify(subscriptionCache).evictActive(userId);
    }

    @Test
    void changePlanShouldRejectSamePlan() {
        UUID userId = UUID.randomUUID();
        SubscriptionEntity s = new SubscriptionEntity();
        s.setId(UUID.randomUUID());
        s.setUserId(userId);
        s.setPlan(Plan.PREMIUM);
        s.setStartDate(LocalDate.parse("2025-03-10"));
        s.setExpirationDate(LocalDate.parse("2025-04-10"));
        s.setStatus(SubscriptionStatus.ATIVA);

        ChangePlanRequest req = new ChangePlanRequest();
        req.setPlano(Plan.PREMIUM);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByUpdatedAtDesc(
                userId, SubscriptionStatus.ATIVA))
                .thenReturn(Optional.of(s));

        assertThatThrownBy(() -> subscriptionService.changePlan(userId, req))
                .isInstanceOf(BadRequestException.class);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void getByIdShouldFailWhenSubscriptionBelongsToAnotherUser() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();

        SubscriptionEntity s = new SubscriptionEntity();
        s.setId(subscriptionId);
        s.setUserId(otherUserId);
        s.setPlan(Plan.BASICO);
        s.setStartDate(LocalDate.parse("2025-03-10"));
        s.setExpirationDate(LocalDate.parse("2025-04-10"));
        s.setStatus(SubscriptionStatus.ATIVA);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> subscriptionService.getById(userId, subscriptionId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void historyShouldFailWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> subscriptionService.history(userId))
                .isInstanceOf(NotFoundException.class);
    }
}
