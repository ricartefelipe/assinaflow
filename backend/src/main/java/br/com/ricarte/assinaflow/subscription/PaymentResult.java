package br.com.ricarte.assinaflow.subscription;

/**
 * Resultado de cobranca.
 *
 * Importante: o componente do record se chama {@code isApproved} (e nao {@code approved})
 * para evitar conflito de assinatura com o factory method {@code approved()}.
 */
public record PaymentResult(boolean isApproved, String errorCode, String errorMessage) {

    public static PaymentResult approved() {
        return new PaymentResult(true, null, null);
    }

    public static PaymentResult declined(String errorCode, String errorMessage) {
        return new PaymentResult(false, errorCode, errorMessage);
    }
}
