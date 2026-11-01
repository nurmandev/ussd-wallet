package com.nurudeen.ussd_wallet.externalbank.repository;

import com.nurudeen.ussd_wallet.externalbank.entity.TransferAttempt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferAttemptRepository extends JpaRepository<TransferAttempt, UUID> {
}
