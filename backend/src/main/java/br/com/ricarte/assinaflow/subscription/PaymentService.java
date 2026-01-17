package br.com.ricarte.assinaflow.subscription;

import br.com.ricarte.assinaflow.metrics.BillingMetrics;
import br.com.ricarte.assinaflow.user.PaymentBehavior;
import br.com.ricarte.assinaflow.user.PaymentProfileEntity;
import br.com.ricarte.assinaflow.user.PaymentProfileRepository;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentProfileRepository paymentProfileRepository;
    private final BillingMetrics billingMetrics;

    public PaymentService(PaymentProfileRepository paymentProfileRepository, BillingMetrics billingMetrics) {
        this.paymentProfileRepository = paymentProfileRepository;
        this.billingMetrics = billingMetrics;
    }

    /**
     * Deterministic payment simulation based on user's payment profile.
     * Safe to call inside a DB transaction; the profile row is locked for update.
     */
    public PaymentResult charge(UUID userId, int amountCents) {
        Timer.Sample sample = billingMetrics.startPaymentTimer();
        boolean approved = false;

        try {
            PaymentProfileEntity profile = paymentProfileRepository.findByUserIdForUpdate(userId).orElse(null);

            // Safe default: approve if no profile exists.
            if (profile == null) {
                PaymentResult res = PaymentResult.approved();
                approved = true;
                return res;
            }

            PaymentBehavior behavior = Optional.ofNullable(profile.getBehavior()).orElse(PaymentBehavior.ALWAYS_APPROVE);

            PaymentResult result = switch (behavior) {
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

            approved = result.approved();
            return result;
        } finally {
            billingMetrics.stopPaymentTimer(sample, approved);
        }
    }
}
