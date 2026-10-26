package br.com.ricarte.assinaflow.subscription;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.payments.async.enabled", havingValue = "true")
public class PaymentChargeConsumer {
    private final br.com.ricarte.assinaflow.payment.PaymentLedger ledger;
    private final PaymentService payments;
    private final SubscriptionRepository subscriptions;
    public PaymentChargeConsumer(PaymentService payments, SubscriptionRepository subscriptions, br.com.ricarte.assinaflow.payment.PaymentLedger ledger) {
        this.ledger = ledger; this.payments = payments; this.subscriptions = subscriptions;
    }

    @RabbitListener(queues = "${app.rabbitmq.payments.queue:payments.charge}")
    public void onMessage(PaymentChargeRequested message) {
        var s = subscriptions.findById(message.subscriptionId()).orElse(null);
        if (s == null || !s.getUserId().equals(message.userId()) || !s.getExpirationDate().equals(message.cycleExpirationDate())) return;
        if (s.getStatus() != SubscriptionStatus.ATIVA || !s.isAutoRenew()) return;
        if (!ledger.authorizedRenewal(s.getId(), s.getUserId(), message.cycleExpirationDate(), message.amountCents())) return;
        // Amount comes from the durable obligation on replay; broker input cannot alter it.
        payments.renew(s.getUserId(), s.getId(), message.cycleExpirationDate(), message.amountCents());
    }
}
