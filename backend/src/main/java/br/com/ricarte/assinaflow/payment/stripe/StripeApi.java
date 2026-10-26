package br.com.ricarte.assinaflow.payment.stripe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.util.Map;

@Component
@ConditionalOnProperty(name="app.payments.gateway", havingValue="stripe")
public class StripeApi {
    private final RestClient client;
    private final ObjectMapper mapper;
    public StripeApi(RestClient.Builder builder, ObjectMapper mapper,
                     @Value("${STRIPE_SECRET_KEY:}") String key) {
        if (!key.startsWith("sk_test_")) throw new IllegalStateException("Stripe sandbox requires a test secret key");
        this.mapper=mapper;
        client=builder.clone().baseUrl("https://api.stripe.com/v1")
                .defaultHeaders(h -> h.setBasicAuth(key, "")).defaultHeader("Stripe-Version","2025-02-24.acacia").build();
    }
    public JsonNode get(String path) { return parse(client.get().uri(java.net.URI.create("https://api.stripe.com/v1"+path)).retrieve().body(String.class)); }
    public JsonNode post(String path, String key, Map<String,String> fields) {
        var form=new LinkedMultiValueMap<String,String>();fields.forEach(form::add);
        try {
            return parse(client.post().uri(path).header("Idempotency-Key",key)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(String.class));
        } catch (RestClientResponseException ex) {
            // A declined confirmation can return its existing PaymentIntent with HTTP402.
            if (ex.getStatusCode().value()==402) {
                JsonNode intent=parse(ex.getResponseBodyAsString()).path("error").path("payment_intent");
                if (intent.isObject()) return intent;
            }
            throw new IllegalStateException("Stripe request inconclusive");
        }
    }
    private JsonNode parse(String value) {
        try { return mapper.readTree(value); }
        catch (Exception ex) { throw new IllegalStateException("Invalid Stripe response"); }
    }
    static String text(JsonNode n,String field) { return n.path(field).asText(""); }
    static void testObject(JsonNode n) {
        if (!n.has("livemode") || n.path("livemode").asBoolean(true)) throw new IllegalStateException("Non-sandbox Stripe object rejected");
    }
}
