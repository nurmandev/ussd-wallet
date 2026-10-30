package com.nurudeen.ussd_wallet.wallet.repository;

import com.nurudeen.ussd_wallet.wallet.entity.WalletTransfer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTransferRepository extends JpaRepository<WalletTransfer, UUID> {

    Optional<WalletTransfer> findBySenderWalletIdAndIdempotencyKey(
            UUID senderWalletId, UUID idempotencyKey);

    @Query("""
            select count(transfer), coalesce(sum(transfer.amount), 0.00)
            from WalletTransfer transfer
            where transfer.senderWalletId = :walletId and transfer.createdAt >= :dayStart
            """)
    List<Object[]> findDailyTotals(
            @Param("walletId") UUID walletId, @Param("dayStart") Instant dayStart);

    @Query("""
            select transfer from WalletTransfer transfer
            where transfer.senderWalletId = :walletId or transfer.recipientWalletId = :walletId
            """)
    Page<WalletTransfer> findHistoryForWallet(
            @Param("walletId") UUID walletId, Pageable pageable);

    @Query("""
            select transfer from WalletTransfer transfer
            where transfer.reference = :reference
              and (transfer.senderWalletId = :walletId or transfer.recipientWalletId = :walletId)
            """)
    Optional<WalletTransfer> findOwnedByReference(
            @Param("reference") String reference, @Param("walletId") UUID walletId);
}
