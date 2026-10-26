package br.com.ricarte.assinaflow.payment;

import java.util.UUID;

public record PaymentChargeCommand(UUID userId, int amountCents, String idempotencyKey, String description,
                                   UUID obligationId, String currency) {
    public PaymentChargeCommand(UUID userId, int amountCents, String key, String description) {
        this(userId, amountCents, key, description, UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "BRL");
    }
}
