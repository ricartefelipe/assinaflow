package br.com.ricarte.assinaflow.payment;

import br.com.ricarte.assinaflow.subscription.PaymentResult;
import br.com.ricarte.assinaflow.user.PaymentBehavior;
import br.com.ricarte.assinaflow.user.PaymentProfileEntity;
import br.com.ricarte.assinaflow.user.PaymentProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulatedPaymentGatewayTest {

    @Mock
    PaymentProfileRepository paymentProfileRepository;

    @InjectMocks
    SimulatedPaymentGateway gateway;

    @Test
    void shouldApproveWhenNoProfile() {
        UUID userId = UUID.randomUUID();
        when(paymentProfileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        PaymentResult result = gateway.charge(new PaymentChargeCommand(userId, 1000, "k", "d"));
        assertThat(result.isApproved()).isTrue();
    }

    @Test
    void shouldDeclineWhenAlwaysDecline() {
        UUID userId = UUID.randomUUID();
        PaymentProfileEntity profile = new PaymentProfileEntity();
        profile.setUserId(userId);
        profile.setBehavior(PaymentBehavior.ALWAYS_DECLINE);
        when(paymentProfileRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(profile));

        PaymentResult result = gateway.charge(new PaymentChargeCommand(userId, 1000, "k", "d"));
        assertThat(result.isApproved()).isFalse();
        assertThat(result.errorCode()).isEqualTo("PAYMENT_DECLINED");
    }
}
