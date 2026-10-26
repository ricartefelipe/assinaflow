package br.com.ricarte.assinaflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Gates the demonstration and contract sandbox until a provider is homologated. */
@Component
public class BillingModeGuard {
    public BillingModeGuard(@Value("${app.billing.mode:demo}") String mode,
                            @Value("${app.payments.gateway:simulated}") String gateway,
                            @Value("${app.security.enabled:true}") boolean security,
                            @Value("${app.security.jwt.secret}") String secret) {
        boolean demo = "demo".equals(mode) && "simulated".equals(gateway);
        boolean sandbox = "sandbox".equals(mode) && ("http".equals(gateway) || "stripe".equals(gateway));
        if (!demo && !sandbox) {
            throw new IllegalStateException("Billing supports demo/simulated or sandbox/stripe only; production is not homologated");
        }
        if (sandbox && (!security || secret.length() < 32 || secret.contains("dev-only"))) {
            throw new IllegalStateException("Sandbox requires authentication and a dedicated JWT secret");
        }
    }
}
