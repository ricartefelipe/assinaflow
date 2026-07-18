package br.com.ricarte.assinaflow.subscription.dto;

import br.com.ricarte.assinaflow.subscription.Plan;
import jakarta.validation.constraints.NotNull;

public class ChangePlanRequest {

    @NotNull
    private Plan plano;

    public Plan getPlano() {
        return plano;
    }

    public void setPlano(Plan plano) {
        this.plano = plano;
    }
}
