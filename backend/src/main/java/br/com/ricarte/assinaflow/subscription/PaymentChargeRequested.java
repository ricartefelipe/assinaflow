package br.com.ricarte.assinaflow.subscription;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentChargeRequested(
        UUID outboxEventId,
        UUID subscriptionId,
        UUID userId,
        LocalDate cycleExpirationDate,
        int attemptNumber,
        int amountCents,
        Instant requestedAt
) {
}
