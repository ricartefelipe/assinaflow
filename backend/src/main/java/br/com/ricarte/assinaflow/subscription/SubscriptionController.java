package br.com.ricarte.assinaflow.subscription;

import br.com.ricarte.assinaflow.subscription.dto.CreateSubscriptionRequest;
import br.com.ricarte.assinaflow.subscription.dto.SubscriptionResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> create(@PathVariable UUID userId, @Valid @RequestBody CreateSubscriptionRequest req) {
        SubscriptionResponse created = subscriptionService.create(userId, req);
        return ResponseEntity
                .created(URI.create("/api/v1/users/" + userId + "/subscriptions/" + created.getId()))
                .body(created);
    }

    @GetMapping("/active")
    public SubscriptionResponse getActive(@PathVariable UUID userId) {
        return subscriptionService.getActive(userId);
    }

    @GetMapping
    public List<SubscriptionResponse> history(@PathVariable UUID userId) {
        return subscriptionService.history(userId);
    }

    @PostMapping("/cancel")
    public SubscriptionResponse cancel(@PathVariable UUID userId) {
        return subscriptionService.cancel(userId);
    }
}
