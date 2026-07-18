package br.com.ricarte.assinaflow.payment;

import java.util.UUID;

public record PaymentChargeCommand(
        UUID userId,
        int amountCents,
        String idempotencyKey,
        String description
) {
}
