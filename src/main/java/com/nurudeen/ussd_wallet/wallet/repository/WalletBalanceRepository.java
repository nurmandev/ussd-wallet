package com.nurudeen.ussd_wallet.wallet.repository;

import com.nurudeen.ussd_wallet.wallet.entity.WalletBalance;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletBalanceRepository extends JpaRepository<WalletBalance, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select balance from WalletBalance balance where balance.walletId in :walletIds order by balance.walletId")
    List<WalletBalance> findAllByWalletIdInForUpdate(@Param("walletIds") Collection<UUID> walletIds);
}
