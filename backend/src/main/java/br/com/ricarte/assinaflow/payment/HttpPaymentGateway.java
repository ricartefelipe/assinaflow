package br.com.ricarte.assinaflow.payment;

import br.com.ricarte.assinaflow.subscription.PaymentResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.payments.gateway", havingValue = "http")
public class HttpPaymentGateway implements PaymentGateway {

    private final RestClient restClient;
    private final String chargeUrl;

    public HttpPaymentGateway(
            RestClient.Builder restClientBuilder,
            @Value("${app.payments.http.url}") String chargeUrl
    ) {
        this.restClient = restClientBuilder.build();
        this.chargeUrl = chargeUrl;
    }

    @Override
    public PaymentResult charge(PaymentChargeCommand command) {
        try {
            HttpChargeResponse body = restClient.post()
                    .uri(chargeUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "userId", command.userId().toString(),
                            "amountCents", command.amountCents(),
                            "idempotencyKey", command.idempotencyKey(),
                            "description", command.description()
                    ))
                    .retrieve()
                    .body(HttpChargeResponse.class);

            if (body == null) {
                return PaymentResult.declined("PAYMENT_GATEWAY_ERROR", "Resposta vazia do gateway HTTP.");
            }
            if (body.approved()) {
                return PaymentResult.approved();
            }
            String code = body.errorCode() != null ? body.errorCode() : "PAYMENT_DECLINED";
            String message = body.errorMessage() != null ? body.errorMessage() : "Pagamento recusado pelo gateway HTTP.";
            return PaymentResult.declined(code, message);
        } catch (RestClientException ex) {
            return PaymentResult.declined("PAYMENT_GATEWAY_ERROR", "Falha ao chamar gateway HTTP: " + ex.getMessage());
        }
    }

    public record HttpChargeResponse(boolean approved, String errorCode, String errorMessage) {
    }
}
