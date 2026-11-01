package com.nurudeen.ussd_wallet.externalbank.repository;

import com.nurudeen.ussd_wallet.externalbank.entity.Bank;
import com.nurudeen.ussd_wallet.externalbank.enums.BankStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankRepository extends JpaRepository<Bank, UUID> {
    Optional<Bank> findByBankCode(String bankCode);
    Optional<Bank> findByBankCodeAndStatus(String bankCode, BankStatus status);
    List<Bank> findByStatusOrderByBankName(BankStatus status);
}
