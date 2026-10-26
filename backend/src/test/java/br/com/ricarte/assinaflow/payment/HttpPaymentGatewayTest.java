package br.com.ricarte.assinaflow.payment;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HttpPaymentGatewayTest {
    @Test
    void approvedAndDeclinedAreDefinitiveButTransportFailureIsUnknown() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var gateway = new HttpPaymentGateway(builder, "http://sandbox.test/charge");
        var command = new PaymentChargeCommand(UUID.randomUUID(), 1990, "cycle-1", "renewal");
        server.expect(requestTo("http://sandbox.test/charge"))
                .andExpect(header("Idempotency-Key", "cycle-1"))
                .andRespond(withSuccess("{\"approved\":true}", MediaType.APPLICATION_JSON));
        assertThat(gateway.charge(command).isApproved()).isTrue();
        server.verify();
        server.reset();
        server.expect(requestTo("http://sandbox.test/charge"))
                .andRespond(withSuccess("{\"approved\":false}", MediaType.APPLICATION_JSON));
        assertThat(gateway.charge(command).isApproved()).isFalse();
        server.verify();
        server.reset();
        server.expect(requestTo("http://sandbox.test/charge"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));
        assertThatThrownBy(() -> gateway.charge(command)).isInstanceOf(PaymentOutcomeUnknownException.class)
                .hasMessageNotContaining("sandbox.test");
        server.verify();
        server.reset();
        server.expect(requestTo("http://sandbox.test/charge"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> gateway.charge(command)).isInstanceOf(PaymentOutcomeUnknownException.class);
        server.verify();
        server.reset();
        server.expect(requestTo("http://sandbox.test/charge"))
                .andRespond(withException(new java.net.SocketTimeoutException("synthetic timeout after remote acceptance")));
        assertThatThrownBy(() -> gateway.charge(command)).isInstanceOf(PaymentOutcomeUnknownException.class);
        server.verify();
    }
}
