package br.com.ricarte.assinaflow.user;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_profiles")
public class PaymentProfileEntity {

    @Id
    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "behavior", nullable = false, length = 40)
    private PaymentBehavior behavior;

    @Column(name = "fail_next_n", nullable = false)
    private int failNextN;

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

    public UUID getUserId() {
        return userId;
    }

    public UserEntity getUser() {
        return user;
    }

    public PaymentBehavior getBehavior() {
        return behavior;
    }

    public int getFailNextN() {
        return failNextN;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public void setBehavior(PaymentBehavior behavior) {
        this.behavior = behavior;
    }

    public void setFailNextN(int failNextN) {
        this.failNextN = failNextN;
    }
}
