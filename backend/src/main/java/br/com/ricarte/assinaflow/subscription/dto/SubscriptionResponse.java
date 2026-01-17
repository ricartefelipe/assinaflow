package br.com.ricarte.assinaflow.subscription.dto;

import br.com.ricarte.assinaflow.subscription.Plan;
import br.com.ricarte.assinaflow.subscription.SubscriptionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class SubscriptionResponse {
    private UUID id;
    private UUID usuarioId;
    private Plan plano;
    private LocalDate dataInicio;
    private LocalDate dataExpiracao;
    private SubscriptionStatus status;
    private boolean autoRenew;
    private int renewalFailures;
    private Instant nextRenewalAttemptAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

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

    public LocalDate getDataExpiracao() {
        return dataExpiracao;
    }

    public void setDataExpiracao(LocalDate dataExpiracao) {
        this.dataExpiracao = dataExpiracao;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }

    public void setAutoRenew(boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    public int getRenewalFailures() {
        return renewalFailures;
    }

    public void setRenewalFailures(int renewalFailures) {
        this.renewalFailures = renewalFailures;
    }

    public Instant getNextRenewalAttemptAt() {
        return nextRenewalAttemptAt;
    }

    public void setNextRenewalAttemptAt(Instant nextRenewalAttemptAt) {
        this.nextRenewalAttemptAt = nextRenewalAttemptAt;
    }
}
