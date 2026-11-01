package com.nurudeen.ussd_wallet.externalbank.service;

import com.nurudeen.ussd_wallet.externalbank.entity.ExternalTransfer;
import com.nurudeen.ussd_wallet.externalbank.enums.ExternalTransferStatus;
import com.nurudeen.ussd_wallet.externalbank.repository.ExternalTransferRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TransferReconciliationService {

    private final ExternalTransferRepository transferRepository;

    public TransferReconciliationService(ExternalTransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    public List<String> reconcile() {
        List<String> issues = new ArrayList<>();
        List<ExternalTransfer> transfers = transferRepository.findAll();
        for (ExternalTransfer transfer : transfers) {
            if (transfer.getStatus() == ExternalTransferStatus.SUCCESSFUL && transfer.getCompletedAt() == null) {
                issues.add("Successful transfer missing completion timestamp: " + transfer.getTransactionReference());
            }
            if (transfer.getStatus() == ExternalTransferStatus.PENDING && transfer.getCreatedAt().plusSeconds(900).isBefore(java.time.Instant.now())) {
                issues.add("Pending transfer exceeds expected processing window: " + transfer.getTransactionReference());
            }
            if (transfer.getTotalDebit() == null || transfer.getTotalDebit().compareTo(transfer.getAmount().add(transfer.getFee())) != 0) {
                issues.add("Transfer amount mismatch: " + transfer.getTransactionReference());
            }
        }
        return issues;
    }
}
