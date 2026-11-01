package com.nurudeen.ussd_wallet.externalbank.service;

import com.nurudeen.ussd_wallet.common.exception.InsufficientWalletFundsException;
import com.nurudeen.ussd_wallet.externalbank.entity.ExternalTransfer;
import com.nurudeen.ussd_wallet.externalbank.entity.TransferReversal;
import com.nurudeen.ussd_wallet.externalbank.enums.ExternalTransferStatus;
import com.nurudeen.ussd_wallet.externalbank.enums.ReversalStatus;
import com.nurudeen.ussd_wallet.externalbank.exception.TransferReversalException;
import com.nurudeen.ussd_wallet.externalbank.repository.TransferReversalRepository;
import com.nurudeen.ussd_wallet.wallet.entity.WalletBalance;
import com.nurudeen.ussd_wallet.wallet.repository.WalletBalanceRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferReversalService {

    private final TransferReversalRepository reversalRepository;
    private final WalletBalanceRepository walletBalanceRepository;

    public TransferReversalService(TransferReversalRepository reversalRepository, WalletBalanceRepository walletBalanceRepository) {
        this.reversalRepository = reversalRepository;
        this.walletBalanceRepository = walletBalanceRepository;
    }

    @Transactional
    public TransferReversal reverse(ExternalTransfer transfer, String reason) {
        if (transfer.getStatus() == ExternalTransferStatus.REVERSED) {
            throw new TransferReversalException("Transfer reversal was already processed");
        }
        if (reversalRepository.findByTransfer_Id(transfer.getId()).isPresent()) {
            throw new TransferReversalException("Duplicate reversal attempted");
        }
        WalletBalance walletBalance = walletBalanceRepository.findByWalletIdForUpdate(transfer.getWalletId())
                .orElseThrow(() -> new IllegalStateException("Wallet balance missing for reversed transfer"));
        BigDecimal reversalAmount = transfer.getTotalDebit();
        if (walletBalance.getAvailableBalance().compareTo(reversalAmount) < 0 && walletBalance.getLedgerBalance().compareTo(reversalAmount) < 0) {
            throw new InsufficientWalletFundsException();
        }
        walletBalance.credit(reversalAmount);
        walletBalanceRepository.save(walletBalance);

        TransferReversal reversal = new TransferReversal(transfer, "REV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(), reversalAmount, reason);
        reversal.complete();
        transfer.setStatus(ExternalTransferStatus.REVERSED);
        reversalRepository.save(reversal);
        return reversal;
    }
}
