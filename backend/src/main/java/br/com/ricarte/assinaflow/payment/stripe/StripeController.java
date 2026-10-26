package br.com.ricarte.assinaflow.payment.stripe;

import br.com.ricarte.assinaflow.payment.PaymentLedger;
import br.com.ricarte.assinaflow.subscription.PaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@ConditionalOnProperty(name="app.payments.gateway",havingValue="stripe")
public class StripeController {
    private final StripeCustomers customers;private final StripeWebhooks webhooks;private final StripePaymentGateway gateway;
    private final PaymentService payments;private final PaymentLedger ledger;private final String publishable;
    public StripeController(StripeCustomers customers,StripeWebhooks webhooks,StripePaymentGateway gateway,PaymentService payments,PaymentLedger ledger,
                            @Value("${STRIPE_PUBLISHABLE_KEY:}") String publishable) {
        if (!publishable.startsWith("pk_test_")) throw new IllegalStateException("Stripe sandbox publishable key required");
        this.customers=customers;this.webhooks=webhooks;this.gateway=gateway;this.payments=payments;this.ledger=ledger;this.publishable=publishable;
    }
    @PostMapping("/api/v1/users/{userId}/billing/setup") public Map<String,String> setup(@PathVariable UUID userId) {return Map.of("url",customers.setup(userId));}
    @GetMapping("/api/v1/users/{userId}/billing/status") public Map<String,Boolean> status(@PathVariable UUID userId) {return Map.of("ready",customers.refresh(userId));}
    @PostMapping("/api/v1/users/{userId}/payments/{id}/stripe/action") public org.springframework.http.ResponseEntity<Map<String,String>> action(@PathVariable UUID userId,@PathVariable UUID id) {return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(gateway.action(userId,id));}
    @PostMapping("/api/v1/users/{userId}/payments/{id}/stripe/reconcile") public void reconcile(@PathVariable UUID userId,@PathVariable UUID id) {
        if(!ledger.get(id).userId().equals(userId)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        payments.reconcile(id);
    }
    @PostMapping("/api/v1/billing/stripe/webhook") public void webhook(@RequestBody byte[] body,@RequestHeader(value="Stripe-Signature",required=false) String signature) {webhooks.receive(body,signature);}
}
