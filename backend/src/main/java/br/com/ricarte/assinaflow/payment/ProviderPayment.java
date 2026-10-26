package br.com.ricarte.assinaflow.payment;

import java.util.UUID;

/** Explicit simulator contract; NOT_FOUND is never proof that a debit did not occur. */
public record ProviderPayment(Status status, String reference, UUID obligationId, UUID userId,
                              int amountCents, String currency) {
    public enum Status { APPROVED, DECLINED, PENDING, NOT_FOUND }
    public boolean matches(PaymentChargeCommand command) {
        return status != null && obligationId != null && obligationId.equals(command.obligationId())
                && userId != null && userId.equals(command.userId())
                && amountCents == command.amountCents() && "BRL".equals(currency)
                && reference != null && !reference.isBlank() && reference.length() <= 200;
    }
}
