package com.nurudeen.ussd_wallet.wallet.repository;

import com.nurudeen.ussd_wallet.wallet.entity.WalletTransferEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransferEntryRepository extends JpaRepository<WalletTransferEntry, UUID> {
}
