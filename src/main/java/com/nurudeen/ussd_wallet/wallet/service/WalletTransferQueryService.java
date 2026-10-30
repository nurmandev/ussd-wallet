package com.nurudeen.ussd_wallet.wallet.service;

import com.nurudeen.ussd_wallet.common.exception.WalletNotFoundException;
import com.nurudeen.ussd_wallet.wallet.dto.TransferHistoryItem;
import com.nurudeen.ussd_wallet.wallet.dto.TransferHistoryPage;
import com.nurudeen.ussd_wallet.wallet.dto.TransferReceiptResponse;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.entity.WalletTransfer;
import com.nurudeen.ussd_wallet.wallet.repository.WalletRepository;
import com.nurudeen.ussd_wallet.wallet.repository.WalletTransferRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletTransferQueryService {

    private final WalletRepository walletRepository;
    private final WalletTransferRepository transferRepository;

    public WalletTransferQueryService(
            WalletRepository walletRepository, WalletTransferRepository transferRepository) {
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;
    }

    @Transactional(readOnly = true)
    public TransferHistoryPage history(UUID customerId, int page, int size) {
        validatePage(page, size);
        Wallet ownerWallet = walletRepository.findByCustomer_Id(customerId)
                .orElseThrow(WalletNotFoundException::new);
        Page<WalletTransfer> records = transferRepository.findHistoryForWallet(
                ownerWallet.getId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Map<UUID, Wallet> wallets = loadWallets(records.getContent());
        List<TransferHistoryItem> items = records.getContent().stream()
                .map(transfer -> historyItem(transfer, ownerWallet.getId(), wallets))
                .toList();
        return new TransferHistoryPage(
                items, records.getNumber(), records.getSize(), records.getTotalElements(), records.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TransferReceiptResponse receipt(UUID customerId, String reference) {
        Wallet ownerWallet = walletRepository.findByCustomer_Id(customerId)
                .orElseThrow(WalletNotFoundException::new);
        WalletTransfer transfer = transferRepository.findOwnedByReference(reference, ownerWallet.getId())
                .orElseThrow(WalletNotFoundException::new);
        Map<UUID, Wallet> wallets = loadWallets(List.of(transfer));
        Wallet sender = wallets.get(transfer.getSenderWalletId());
        Wallet recipient = wallets.get(transfer.getRecipientWalletId());
        if (sender == null || recipient == null) {
            throw new IllegalStateException("Transfer references a wallet that no longer exists");
        }
        return new TransferReceiptResponse(
                transfer.getReference(),
                "SUCCESSFUL",
                transfer.getSenderWalletId().equals(ownerWallet.getId()) ? "SENT" : "RECEIVED",
                sender.getWalletNumber(),
                recipient.getWalletNumber(),
                transfer.getAmount(),
                transfer.getFee(),
                transfer.getTotalDebit(),
                transfer.getCurrency(),
                transfer.getDescription(),
                transfer.getCreatedAt());
    }

    private Map<UUID, Wallet> loadWallets(List<WalletTransfer> records) {
        List<UUID> walletIds = records.stream()
                .flatMap(transfer -> java.util.stream.Stream.of(
                        transfer.getSenderWalletId(), transfer.getRecipientWalletId()))
                .distinct()
                .toList();
        return walletRepository.findAllById(walletIds).stream()
                .collect(Collectors.toMap(Wallet::getId, Function.identity()));
    }

    private TransferHistoryItem historyItem(
            WalletTransfer transfer, UUID ownerWalletId, Map<UUID, Wallet> wallets) {
        boolean sent = transfer.getSenderWalletId().equals(ownerWalletId);
        UUID counterpartyId = sent ? transfer.getRecipientWalletId() : transfer.getSenderWalletId();
        Wallet counterparty = wallets.get(counterpartyId);
        if (counterparty == null) {
            throw new IllegalStateException("Transfer references a wallet that no longer exists");
        }
        return new TransferHistoryItem(
                transfer.getReference(),
                sent ? "SENT" : "RECEIVED",
                counterparty.getWalletNumber(),
                transfer.getAmount(),
                sent ? transfer.getFee() : null,
                transfer.getCurrency(),
                transfer.getDescription(),
                "SUCCESSFUL",
                transfer.getCreatedAt());
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 50");
        }
    }
}
