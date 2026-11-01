package com.nurudeen.ussd_wallet.externalbank.repository;

import com.nurudeen.ussd_wallet.externalbank.entity.ExternalBankAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalBankAccountRepository extends JpaRepository<ExternalBankAccount, UUID> {
    Optional<ExternalBankAccount> findByBank_BankCodeAndAccountNumberHash(String bankCode, String accountNumberHash);
    Optional<ExternalBankAccount> findByBank_IdAndAccountNumberHash(UUID bankId, String accountNumberHash);
}
