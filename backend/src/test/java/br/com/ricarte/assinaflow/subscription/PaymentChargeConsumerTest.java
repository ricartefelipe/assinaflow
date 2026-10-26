package br.com.ricarte.assinaflow.subscription;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;

class PaymentChargeConsumerTest {
    private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    private final PaymentService payments = mock(PaymentService.class);
    private final br.com.ricarte.assinaflow.payment.PaymentLedger ledger = mock(br.com.ricarte.assinaflow.payment.PaymentLedger.class);
    private final PaymentChargeConsumer consumer = new PaymentChargeConsumer(payments, subscriptions, ledger);
    private final UUID id = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final LocalDate cycle = LocalDate.parse("2025-04-30");
    private SubscriptionEntity subscription() {
        var s = new SubscriptionEntity(); s.setId(id); s.setUserId(user); s.setPlan(Plan.BASICO);
        s.setStatus(SubscriptionStatus.ATIVA); s.setAutoRenew(true); s.setExpirationDate(cycle);
        when(subscriptions.findById(id)).thenReturn(Optional.of(s)); return s;
    }
    private PaymentChargeRequested message(LocalDate date, UUID owner) {
        return new PaymentChargeRequested(UUID.randomUUID(), id, owner, date, 1, 1990, Instant.EPOCH);
    }
    @Test void forwardsRedeliveryToDurableObligationWithoutInventingNewIdentity() {
        subscription();
        when(ledger.authorizedRenewal(id,user,cycle,1990)).thenReturn(true);
        for (int i=0;i<10;i++) consumer.onMessage(message(cycle,user));
        verify(payments,times(10)).renew(user,id,cycle,1990);
    }
    @Test void staleCanceledAndForgedOwnerNeverAuthorizeCharge() {
        var s=subscription(); consumer.onMessage(message(cycle.plusMonths(1),user));
        consumer.onMessage(message(cycle,UUID.randomUUID()));
        s.setAutoRenew(false); consumer.onMessage(message(cycle,user));
        verifyNoInteractions(payments);
    }
    @Test void missingSubscriptionNeverCharges() {
        consumer.onMessage(message(cycle,user)); verifyNoInteractions(payments);
    }
}
