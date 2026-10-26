package br.com.ricarte.assinaflow.payment;

import br.com.ricarte.assinaflow.subscription.PaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.payments.recovery.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentRecoveryScheduler {
    private final PaymentService payments;
    public PaymentRecoveryScheduler(PaymentService payments) { this.payments = payments; }
    @Scheduled(fixedDelayString = "${app.payments.recovery.delay-ms:15000}")
    public void recover() { payments.recover(100); }
}
