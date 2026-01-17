package br.com.ricarte.assinaflow.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, UUID> {

    boolean existsByUserIdAndStatusIn(UUID userId, Collection<SubscriptionStatus> statuses);

    Optional<SubscriptionEntity> findFirstByUserIdAndStatusIn(UUID userId, Collection<SubscriptionStatus> statuses);

    List<SubscriptionEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SubscriptionEntity s where s.id = :id")
    Optional<SubscriptionEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = """
            SELECT *
            FROM subscriptions
            WHERE status = 'ATIVA'
              AND auto_renew = true
              AND renewal_failures < 3
              AND expiration_date = :today
              AND (next_renewal_attempt_at IS NULL OR next_renewal_attempt_at <= :now)
              AND (renewal_in_flight_until IS NULL OR renewal_in_flight_until <= :now)
            ORDER BY expiration_date ASC, updated_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<SubscriptionEntity> lockBatchDueForRenewal(
            @Param("today") LocalDate today,
            @Param("now") Instant now,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT *
            FROM subscriptions
            WHERE status = 'CANCELAMENTO_AGENDADO'
              AND expiration_date <= :today
            ORDER BY expiration_date ASC, updated_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<SubscriptionEntity> lockBatchDueForFinalization(
            @Param("today") LocalDate today,
            @Param("limit") int limit
    );
}
