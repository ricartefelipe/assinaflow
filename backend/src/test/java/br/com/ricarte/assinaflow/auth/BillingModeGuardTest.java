package br.com.ricarte.assinaflow.auth;

import br.com.ricarte.assinaflow.config.BillingModeGuard;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BillingModeGuardTest {
    @Test
    void validatesModeAndSecretBeforeGatewayCapabilityCheck() {
        String key = "synthetic-dedicated-secret-at-least-32-bytes";
        assertThatCode(() -> new BillingModeGuard("demo", "simulated", true, key)).doesNotThrowAnyException();
        assertThatCode(() -> new BillingModeGuard("sandbox", "http", true, key)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new BillingModeGuard("production", "http", true, key)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BillingModeGuard("demo", "http", true, key)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BillingModeGuard("sandbox", "http", false, key)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BillingModeGuard("sandbox", "http", true, "local-dev-only-change-me-assinaflow-secret-key-32b")).isInstanceOf(IllegalStateException.class);
    }
}
