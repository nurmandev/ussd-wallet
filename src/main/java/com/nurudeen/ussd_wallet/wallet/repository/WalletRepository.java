package com.nurudeen.ussd_wallet.wallet.repository;

import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    boolean existsByWalletNumber(String walletNumber);

    boolean existsByCustomer_Id(UUID customerId);

    @EntityGraph(attributePaths = "balance")
    Optional<Wallet> findByCustomer_Id(UUID customerId);
}
