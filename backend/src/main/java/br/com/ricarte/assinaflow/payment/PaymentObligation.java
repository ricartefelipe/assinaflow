package br.com.ricarte.assinaflow.payment;

import java.time.Instant;
import java.util.UUID;

public record PaymentObligation(UUID id, String businessKey, UUID userId, UUID subscriptionId, int amountCents,
                                String currency, String key, String providerReference, PaymentContext context, String status,
                                boolean closed, boolean settled, int failures, int executions, int lookups,
                                UUID owner, Instant leaseUntil, Instant nextAttemptAt, Instant retentionUntil) {
    public PaymentChargeCommand command() {
        return new PaymentChargeCommand(userId, amountCents, key, context.kind(), id, currency);
    }
}
