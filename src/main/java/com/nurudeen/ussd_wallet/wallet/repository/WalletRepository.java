package com.nurudeen.ussd_wallet.wallet.repository;

import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    boolean existsByWalletNumber(String walletNumber);

    boolean existsByCustomer_Id(UUID customerId);

    @EntityGraph(attributePaths = "balance")
    Optional<Wallet> findByCustomer_Id(UUID customerId);

    @Query("select wallet from Wallet wallet where wallet.customer.id = :customerId")
    Optional<Wallet> findWalletForTransfer(@Param("customerId") UUID customerId);

    Optional<Wallet> findByWalletNumber(String walletNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from Wallet wallet where wallet.id in :walletIds order by wallet.id")
    List<Wallet> findAllByIdInForUpdate(@Param("walletIds") Collection<UUID> walletIds);
}
