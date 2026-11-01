package com.nurudeen.ussd_wallet.externalbank.service;

import com.nurudeen.ussd_wallet.externalbank.dto.TransferStatusResponse;
import com.nurudeen.ussd_wallet.externalbank.entity.ExternalTransfer;
import com.nurudeen.ussd_wallet.externalbank.exception.ExternalTransferException;
import com.nurudeen.ussd_wallet.externalbank.repository.ExternalTransferRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TransferStatusService {

    private final ExternalTransferRepository transferRepository;

    public TransferStatusService(ExternalTransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    public TransferStatusResponse fetch(UUID customerId, String transactionReference) {
        ExternalTransfer transfer = transferRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new ExternalTransferException("Transfer not found"));
        if (!transfer.getWalletId().equals(customerId)) {
            throw new ExternalTransferException("Access denied to this transfer");
        }
        return new TransferStatusResponse(
                transfer.getTransactionReference(),
                transfer.getStatus().name(),
                transfer.getAmount(),
                transfer.getFee(),
                transfer.getTotalDebit(),
                transfer.getCurrency(),
                transfer.getBank().getBankName(),
                maskAccount(transfer.getDestinationAccountNumberProtected()),
                transfer.getDestinationAccountName(),
                transfer.getCreatedAt(),
                transfer.getCompletedAt());
    }

    private String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "******";
        }
        return "******" + accountNumber.substring(Math.max(0, accountNumber.length() - 4));
    }
}
