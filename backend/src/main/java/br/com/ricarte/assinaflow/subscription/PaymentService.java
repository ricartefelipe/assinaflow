package br.com.ricarte.assinaflow.subscription;

import br.com.ricarte.assinaflow.metrics.BillingMetrics;
import br.com.ricarte.assinaflow.payment.PaymentChargeCommand;
import br.com.ricarte.assinaflow.payment.PaymentGateway;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentGateway paymentGateway;
    private final BillingMetrics billingMetrics;

    public PaymentService(PaymentGateway paymentGateway, BillingMetrics billingMetrics) {
        this.paymentGateway = paymentGateway;
        this.billingMetrics = billingMetrics;
    }

    public PaymentResult charge(UUID userId, int amountCents) {
        return charge(userId, amountCents, "charge-" + userId + "-" + amountCents, "charge");
    }

    public PaymentResult charge(UUID userId, int amountCents, String idempotencyKey, String description) {
        Timer.Sample sample = billingMetrics.startPaymentTimer();
        boolean approved = false;

        try {
            PaymentResult result = paymentGateway.charge(
                    new PaymentChargeCommand(userId, amountCents, idempotencyKey, description)
            );
            approved = result.isApproved();
            return result;
        } finally {
            billingMetrics.stopPaymentTimer(sample, approved);
        }
    }
}
