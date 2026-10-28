package com.nurudeen.ussd_wallet.ledger.repository;

import com.nurudeen.ussd_wallet.ledger.entity.LedgerAccount;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
}
