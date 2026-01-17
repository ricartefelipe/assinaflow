package br.com.ricarte.assinaflow.subscription;

public record PaymentResult(boolean approved, String errorCode, String errorMessage) {
    public static PaymentResult approved() {
        return new PaymentResult(true, null, null);
    }

    public static PaymentResult declined(String errorCode, String errorMessage) {
        return new PaymentResult(false, errorCode, errorMessage);
    }
}
