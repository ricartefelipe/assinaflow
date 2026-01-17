package br.com.ricarte.assinaflow.subscription;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "subscription_renewal_attempts",
        uniqueConstraints = @UniqueConstraint(name = "uq_attempt_per_cycle",
                columnNames = {"subscription_id", "cycle_expiration_date", "attempt_number"}))
public class SubscriptionRenewalAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "subscription_id", nullable = false, columnDefinition = "uuid")
    private UUID subscriptionId;

    @Column(name = "cycle_expiration_date", nullable = false)
    private LocalDate cycleExpirationDate;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "attempted_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private RenewalAttemptResult result;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    public UUID getId() {
        return id;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public LocalDate getCycleExpirationDate() {
        return cycleExpirationDate;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public RenewalAttemptResult getResult() {
        return result;
    }

    public int getAmountCents() {
        return amountCents;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setSubscriptionId(UUID subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public void setCycleExpirationDate(LocalDate cycleExpirationDate) {
        this.cycleExpirationDate = cycleExpirationDate;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public void setAttemptedAt(Instant attemptedAt) {
        this.attemptedAt = attemptedAt;
    }

    public void setResult(RenewalAttemptResult result) {
        this.result = result;
    }

    public void setAmountCents(int amountCents) {
        this.amountCents = amountCents;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
