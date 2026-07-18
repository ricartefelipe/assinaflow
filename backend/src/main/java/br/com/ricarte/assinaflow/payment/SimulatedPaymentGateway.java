package br.com.ricarte.assinaflow.payment;

import br.com.ricarte.assinaflow.subscription.PaymentResult;
import br.com.ricarte.assinaflow.user.PaymentBehavior;
import br.com.ricarte.assinaflow.user.PaymentProfileEntity;
import br.com.ricarte.assinaflow.user.PaymentProfileRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.payments.gateway", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPaymentGateway implements PaymentGateway {

    private final PaymentProfileRepository paymentProfileRepository;

    public SimulatedPaymentGateway(PaymentProfileRepository paymentProfileRepository) {
        this.paymentProfileRepository = paymentProfileRepository;
    }

    @Override
    public PaymentResult charge(PaymentChargeCommand command) {
        PaymentProfileEntity profile = paymentProfileRepository.findByUserIdForUpdate(command.userId()).orElse(null);

        if (profile == null) {
            return PaymentResult.approved();
        }

        PaymentBehavior behavior = Optional.ofNullable(profile.getBehavior()).orElse(PaymentBehavior.ALWAYS_APPROVE);

        return switch (behavior) {
            case ALWAYS_APPROVE -> PaymentResult.approved();
            case ALWAYS_DECLINE -> PaymentResult.declined("PAYMENT_DECLINED", "Pagamento recusado (simulado)");
            case FAIL_NEXT_N -> {
                int remaining = profile.getFailNextN();
                if (remaining > 0) {
                    profile.setFailNextN(remaining - 1);
                    yield PaymentResult.declined(
                            "PAYMENT_DECLINED",
                            "Pagamento recusado (simulado), failNextN restante=" + (remaining - 1)
                    );
                }
                yield PaymentResult.approved();
            }
        };
    }
}
