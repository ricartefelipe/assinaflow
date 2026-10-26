package br.com.ricarte.assinaflow.payment;

import br.com.ricarte.assinaflow.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class PaymentOutcomeUnknownException extends ApiException {
    public PaymentOutcomeUnknownException() {
        super("PAYMENT_OUTCOME_UNKNOWN", HttpStatus.SERVICE_UNAVAILABLE,
                "Resultado de pagamento incerto; reconciliar pela mesma chave antes de nova tentativa.");
    }
}
