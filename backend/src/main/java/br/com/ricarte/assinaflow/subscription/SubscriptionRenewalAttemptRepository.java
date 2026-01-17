package br.com.ricarte.assinaflow.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SubscriptionRenewalAttemptRepository extends JpaRepository<SubscriptionRenewalAttemptEntity, UUID> {

    long countBySubscriptionIdAndCycleExpirationDate(UUID subscriptionId, LocalDate cycleExpirationDate);

    boolean existsBySubscriptionIdAndCycleExpirationDateAndAttemptNumber(UUID subscriptionId, LocalDate cycleExpirationDate, int attemptNumber);

    List<SubscriptionRenewalAttemptEntity> findBySubscriptionIdOrderByAttemptedAtDesc(UUID subscriptionId);
}
