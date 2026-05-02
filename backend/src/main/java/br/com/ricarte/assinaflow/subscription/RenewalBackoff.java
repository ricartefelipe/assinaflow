package br.com.ricarte.assinaflow.subscription;

import java.time.Duration;

/** Intervalos entre novas tentativas de cobranca apos falhas de renovacao. */
public final class RenewalBackoff {

    private RenewalBackoff() {
    }

    public static Duration afterFailedAttempt(int attemptNumber) {
        return switch (attemptNumber) {
            case 1 -> Duration.ofMinutes(15);
            case 2 -> Duration.ofMinutes(60);
            default -> Duration.ZERO;
        };
    }
}
