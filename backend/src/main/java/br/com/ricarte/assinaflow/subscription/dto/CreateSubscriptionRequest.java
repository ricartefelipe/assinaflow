package br.com.ricarte.assinaflow.subscription.dto;

import br.com.ricarte.assinaflow.subscription.Plan;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class CreateSubscriptionRequest {

    @NotNull
    private Plan plano;

    /** Optional; defaults to today (UTC). */
    private LocalDate dataInicio;

    public Plan getPlano() {
        return plano;
    }

    public void setPlano(Plan plano) {
        this.plano = plano;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public void setDataInicio(LocalDate dataInicio) {
        this.dataInicio = dataInicio;
    }
}
