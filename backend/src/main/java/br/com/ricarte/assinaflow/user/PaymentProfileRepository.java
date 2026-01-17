package br.com.ricarte.assinaflow.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface PaymentProfileRepository extends JpaRepository<PaymentProfileEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentProfileEntity p where p.userId = :userId")
    Optional<PaymentProfileEntity> findByUserIdForUpdate(@Param("userId") UUID userId);
}
