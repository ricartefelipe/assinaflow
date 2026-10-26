package br.com.ricarte.assinaflow.subscription;

import java.util.UUID;

public record PaymentResult(boolean isApproved, String errorCode, String errorMessage, UUID obligationId, UUID owner) {
    public PaymentResult(boolean approved, String code, String message) { this(approved, code, message, null, null); }
    public static PaymentResult approved() { return new PaymentResult(true, null, null); }
    public static PaymentResult declined(String code, String message) { return new PaymentResult(false, code, message); }
}
