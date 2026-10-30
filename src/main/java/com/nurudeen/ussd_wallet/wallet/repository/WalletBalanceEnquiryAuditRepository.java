package com.nurudeen.ussd_wallet.wallet.repository;

import com.nurudeen.ussd_wallet.wallet.entity.WalletBalanceEnquiryAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletBalanceEnquiryAuditRepository
        extends JpaRepository<WalletBalanceEnquiryAudit, UUID> {
}
