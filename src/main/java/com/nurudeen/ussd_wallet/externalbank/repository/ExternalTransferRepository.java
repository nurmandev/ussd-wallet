package com.nurudeen.ussd_wallet.externalbank.repository;

import com.nurudeen.ussd_wallet.externalbank.entity.ExternalTransfer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalTransferRepository extends JpaRepository<ExternalTransfer, UUID> {
    Optional<ExternalTransfer> findByTransactionReference(String transactionReference);
    Optional<ExternalTransfer> findByTransactionReferenceAndWalletId(String transactionReference, UUID walletId);
    Optional<ExternalTransfer> findByWalletIdAndIdempotencyKey(UUID walletId, String idempotencyKey);
    List<ExternalTransfer> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
}
