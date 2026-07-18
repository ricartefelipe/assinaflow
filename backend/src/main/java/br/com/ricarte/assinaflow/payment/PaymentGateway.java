package br.com.ricarte.assinaflow.payment;

import br.com.ricarte.assinaflow.subscription.PaymentResult;

public interface PaymentGateway {

    PaymentResult charge(PaymentChargeCommand command);
}
