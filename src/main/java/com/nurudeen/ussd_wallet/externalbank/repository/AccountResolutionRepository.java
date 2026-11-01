package com.nurudeen.ussd_wallet.externalbank.repository;

import com.nurudeen.ussd_wallet.externalbank.entity.AccountResolution;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountResolutionRepository extends JpaRepository<AccountResolution, UUID> {
    Optional<AccountResolution> findByResolutionReference(String resolutionReference);
    Optional<AccountResolution> findByResolutionReferenceAndCustomerId(String resolutionReference, UUID customerId);
    Optional<AccountResolution> findByCustomerIdAndBankIdAndAccountNumberHash(UUID customerId, UUID bankId, String accountNumberHash);
}
