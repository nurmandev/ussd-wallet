package com.nurudeen.ussd_wallet.wallet.repository;

import com.nurudeen.ussd_wallet.wallet.entity.WalletTransferIntent;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface WalletTransferIntentRepository extends JpaRepository<WalletTransferIntent, UUID> {

    Optional<WalletTransferIntent> findByCustomerIdAndIdempotencyKey(UUID customerId, UUID idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select intent from WalletTransferIntent intent where intent.id = :id and intent.customerId = :customerId")
    Optional<WalletTransferIntent> findOwnedByIdForUpdate(
            @Param("id") UUID id, @Param("customerId") UUID customerId);

    @Modifying
    @Query("""
            update WalletTransferIntent intent
            set intent.status = com.nurudeen.ussd_wallet.wallet.enums.TransferIntentStatus.EXPIRED,
                intent.completedAt = :now
            where intent.status = com.nurudeen.ussd_wallet.wallet.enums.TransferIntentStatus.PENDING_CONFIRMATION
              and intent.expiresAt <= :now
            """)
    int expirePendingBefore(@Param("now") java.time.Instant now);
}
