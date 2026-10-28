package com.nurudeen.ussd_wallet.wallet.repository;

import com.nurudeen.ussd_wallet.wallet.entity.WalletBalance;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletBalanceRepository extends JpaRepository<WalletBalance, UUID> {
}
