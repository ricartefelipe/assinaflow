package br.com.ricarte.assinaflow.subscription.dto;

import br.com.ricarte.assinaflow.subscription.Plan;

public class PlanResponse {

    private Plan plano;
    private int precoCentavos;
    private String moeda;

    public PlanResponse() {
    }

    public PlanResponse(Plan plano, int precoCentavos, String moeda) {
        this.plano = plano;
        this.precoCentavos = precoCentavos;
        this.moeda = moeda;
    }

    public Plan getPlano() {
        return plano;
    }

    public void setPlano(Plan plano) {
        this.plano = plano;
    }

    public int getPrecoCentavos() {
        return precoCentavos;
    }

    public void setPrecoCentavos(int precoCentavos) {
        this.precoCentavos = precoCentavos;
    }

    public String getMoeda() {
        return moeda;
    }

    public void setMoeda(String moeda) {
        this.moeda = moeda;
    }
}
