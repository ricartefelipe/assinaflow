package br.com.ricarte.assinaflow.subscription;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class SubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan", nullable = false, length = 30)
    private Plan plan;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private SubscriptionStatus status;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew = true;

    @Column(name = "renewal_failures", nullable = false)
    private int renewalFailures = 0;

    @Column(name = "next_renewal_attempt_at", columnDefinition = "timestamp with time zone")
    private Instant nextRenewalAttemptAt;

    @Column(name = "renewal_in_flight_until", columnDefinition = "timestamp with time zone")
    private Instant renewalInFlightUntil;

    @Column(name = "cancel_requested_at", columnDefinition = "timestamp with time zone")
    private Instant cancelRequestedAt;

    @Column(name = "suspended_at", columnDefinition = "timestamp with time zone")
    private Instant suspendedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Plan getPlan() {
        return plan;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }

    public int getRenewalFailures() {
        return renewalFailures;
    }

    public Instant getNextRenewalAttemptAt() {
        return nextRenewalAttemptAt;
    }

    public Instant getRenewalInFlightUntil() {
        return renewalInFlightUntil;
    }

    public Instant getCancelRequestedAt() {
        return cancelRequestedAt;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public void setAutoRenew(boolean autoRenew) {
        this.autoRenew = autoRenew;
    }

    public void setRenewalFailures(int renewalFailures) {
        this.renewalFailures = renewalFailures;
    }

    public void setNextRenewalAttemptAt(Instant nextRenewalAttemptAt) {
        this.nextRenewalAttemptAt = nextRenewalAttemptAt;
    }

    public void setRenewalInFlightUntil(Instant renewalInFlightUntil) {
        this.renewalInFlightUntil = renewalInFlightUntil;
    }

    public void setCancelRequestedAt(Instant cancelRequestedAt) {
        this.cancelRequestedAt = cancelRequestedAt;
    }

    public void setSuspendedAt(Instant suspendedAt) {
        this.suspendedAt = suspendedAt;
    }
}
