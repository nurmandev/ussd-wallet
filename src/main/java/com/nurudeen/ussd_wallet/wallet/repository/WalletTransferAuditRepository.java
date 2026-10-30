package com.nurudeen.ussd_wallet.wallet.repository;

import com.nurudeen.ussd_wallet.wallet.entity.WalletTransferAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransferAuditRepository extends JpaRepository<WalletTransferAudit, UUID> {
}
