package br.com.ricarte.assinaflow.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class BillingConfigController {
    private final String gateway; private final String publishable;
    public BillingConfigController(@Value("${app.payments.gateway:simulated}") String gateway,
                                   @Value("${STRIPE_PUBLISHABLE_KEY:}") String publishable) {
        this.gateway=gateway;this.publishable=publishable;
    }
    @GetMapping("/api/v1/billing/config") public Map<String,String> config() {
        return gateway.equals("stripe")?Map.of("gateway","stripe","mode","sandbox","publishableKey",publishable):Map.of("gateway","simulated","mode","demo");
    }
}
