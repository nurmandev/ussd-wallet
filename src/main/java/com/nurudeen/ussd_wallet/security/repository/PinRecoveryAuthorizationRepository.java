package com.nurudeen.ussd_wallet.security.repository;

import com.nurudeen.ussd_wallet.security.entity.PinRecoveryAuthorization;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PinRecoveryAuthorizationRepository
        extends JpaRepository<PinRecoveryAuthorization, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select authorization from PinRecoveryAuthorization authorization
            where authorization.tokenHash = :tokenHash
            """)
    Optional<PinRecoveryAuthorization> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash);

    @Query("""
            select authorization from PinRecoveryAuthorization authorization
            where authorization.customerId = :customerId and authorization.consumedAt is null
            """)
    java.util.List<PinRecoveryAuthorization> findUnconsumedByCustomerId(
            @Param("customerId") UUID customerId);
}
