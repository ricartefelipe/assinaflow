package br.com.ricarte.assinaflow.payment.stripe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HostedCheckoutUrlTest {

    @Test
    void acceptsTheHostedCheckoutPage() {
        String url = "https://checkout.stripe.com/c/pay/cs_test_a1b2#fid";
        assertThrows(IllegalStateException.class, () -> HostedCheckoutUrl.require(url));
        assertEquals("https://checkout.stripe.com/c/pay/cs_test_a1b2?x=1",
                HostedCheckoutUrl.require("https://checkout.stripe.com/c/pay/cs_test_a1b2?x=1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://checkout.stripe.com/c/pay/cs_test_1",
            "https://evil.checkout.stripe.com/c/pay/cs_test_1",
            "https://checkout.stripe.com.evil.example/c/pay/cs_test_1",
            "https://checkout.stripe.com:8443/c/pay/cs_test_1",
            "https://user:pass@checkout.stripe.com/c/pay/cs_test_1",
            "https://checkout.stripe.com@evil.example/c/pay/cs_test_1",
            "https://checkout.stripe.com%2F@evil.example/",
            "//checkout.stripe.com/c/pay/cs_test_1",
            "/c/pay/cs_test_1",
            "javascript:alert(1)",
            "",
            "https://checkout.stripe.com/c/pay/cs_test_1#fragment",
            "https://CHECKOUT.STRIPE.COM./c/pay/cs_test_1",
            "not a url"
    })
    void rejectsEverythingThatIsNotTheHostedOrigin(String raw) {
        assertThrows(IllegalStateException.class, () -> HostedCheckoutUrl.require(raw));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalStateException.class, () -> HostedCheckoutUrl.require(null));
    }
}
