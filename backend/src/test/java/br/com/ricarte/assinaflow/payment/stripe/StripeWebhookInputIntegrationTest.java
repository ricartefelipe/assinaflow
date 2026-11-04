package br.com.ricarte.assinaflow.payment.stripe;

import br.com.ricarte.assinaflow.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O webhook do Stripe é público: entrada ausente, malformada ou sem assinatura válida responde 400, sem detalhes. */
@TestPropertySource(properties = {"app.billing.mode=sandbox", "app.payments.gateway=stripe", "app.security.enabled=true",
        "app.security.jwt.secret=webhook-input-dedicated-secret-at-least32",
        "STRIPE_SECRET_KEY=sk_test_webhook", "STRIPE_PUBLISHABLE_KEY=pk_test_webhook", "STRIPE_WEBHOOK_SECRET=whsec_webhook"})
class StripeWebhookInputIntegrationTest extends IntegrationTestBase {
    private static final String PATH = "/api/v1/billing/stripe/webhook";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void emptyOrMalformedNotificationsAreBadRequests() throws Exception {
        mvc.perform(post(PATH)).andExpect(status().isBadRequest());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isBadRequest());
        mvc.perform(post(PATH).contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}").header("Stripe-Signature", "garbage"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}").header("Stripe-Signature", "t=abc,v1=zz"))
                .andExpect(status().isBadRequest());
        String signedButInvalid = "{";
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(signedButInvalid)
                        .header("Stripe-Signature", sign(signedButInvalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void validNotificationIsAcceptedOnceAndRepeatsAreIdempotent() throws Exception {
        String id = "evt_" + UUID.randomUUID().toString().replace("-", "");
        String body = "{\"id\":\"" + id + "\",\"object\":\"event\",\"livemode\":false,\"type\":\"customer.updated\",\"data\":{\"object\":{}}}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body).header("Stripe-Signature", sign(body)))
                    .andExpect(status().isOk());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stripe_webhook_inbox WHERE event_id=?", Integer.class, id)).isEqualTo(1);
    }

    private static String sign(String body) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_webhook".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
