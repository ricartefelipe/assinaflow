package br.com.ricarte.assinaflow.subscription;

import br.com.ricarte.assinaflow.subscription.dto.PlanResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanControllerTest {

    @Test
    void listShouldExposeAllPlansWithPricesInBrl() {
        PlanController controller = new PlanController();

        List<PlanResponse> plans = controller.list();

        assertThat(plans).hasSize(3);
        assertThat(plans)
                .extracting(PlanResponse::getPlano)
                .containsExactly(Plan.BASICO, Plan.PREMIUM, Plan.FAMILIA);
        assertThat(plans)
                .extracting(PlanResponse::getPrecoCentavos)
                .containsExactly(1990, 3990, 5990);
        assertThat(plans)
                .extracting(PlanResponse::getMoeda)
                .containsOnly("BRL");
    }
}
