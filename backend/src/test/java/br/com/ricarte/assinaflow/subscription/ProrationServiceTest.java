package br.com.ricarte.assinaflow.subscription;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProrationServiceTest {

    ProrationService service = new ProrationService();

    @Test
    void upgradeShouldProducePositiveDelta() {
        int delta = service.proratedDeltaCents(
                Plan.BASICO,
                Plan.PREMIUM,
                LocalDate.parse("2025-03-10"),
                LocalDate.parse("2025-04-10"),
                LocalDate.parse("2025-03-20")
        );
        assertThat(delta).isGreaterThan(0);
    }

    @Test
    void downgradeShouldProduceNegativeDelta() {
        int delta = service.proratedDeltaCents(
                Plan.PREMIUM,
                Plan.BASICO,
                LocalDate.parse("2025-03-10"),
                LocalDate.parse("2025-04-10"),
                LocalDate.parse("2025-03-20")
        );
        assertThat(delta).isLessThan(0);
    }

    @Test
    void renewalAmountShouldApplyCredit() {
        assertThat(service.renewalAmountCents(Plan.PREMIUM, 1000)).isEqualTo(2990);
        assertThat(service.renewalAmountCents(Plan.BASICO, 5000)).isEqualTo(0);
    }
}
