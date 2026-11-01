package com.nurudeen.ussd_wallet.externalbank.repository;

import com.nurudeen.ussd_wallet.externalbank.entity.TransferReversal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferReversalRepository extends JpaRepository<TransferReversal, UUID> {
    Optional<TransferReversal> findByTransfer_Id(UUID transferId);
}
