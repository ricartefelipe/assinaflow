package br.com.ricarte.assinaflow.payment;

import br.com.ricarte.assinaflow.subscription.PaymentResult;

public interface PaymentGateway {

    PaymentResult charge(PaymentChargeCommand command);

    default void requireReady(java.util.UUID user) {}

    default boolean supportsRecovery() { return false; }

    default ProviderPayment submit(PaymentChargeCommand command) {
        throw new PaymentOutcomeUnknownException();
    }

    default ProviderPayment lookup(PaymentChargeCommand command, String providerReference) {
        throw new PaymentOutcomeUnknownException();
    }
}
