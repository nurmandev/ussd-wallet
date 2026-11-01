package com.nurudeen.ussd_wallet.externalbank.service;

import com.nurudeen.ussd_wallet.externalbank.dto.BankResponse;
import com.nurudeen.ussd_wallet.externalbank.entity.Bank;
import com.nurudeen.ussd_wallet.externalbank.enums.BankStatus;
import com.nurudeen.ussd_wallet.externalbank.enums.TransferChannel;
import com.nurudeen.ussd_wallet.externalbank.exception.BankUnavailableException;
import com.nurudeen.ussd_wallet.externalbank.repository.BankRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankService {

    private final BankRepository bankRepository;

    public BankService(BankRepository bankRepository) {
        this.bankRepository = bankRepository;
    }

    public List<BankResponse> listActiveBanks() {
        return bankRepository.findByStatusOrderByBankName(BankStatus.ACTIVE).stream()
                .map(bank -> new BankResponse(bank.getBankCode(), bank.getBankName(), bank.getStatus(), bank.getSupportedTransferChannel()))
                .toList();
    }

    public Optional<Bank> findByBankCode(String bankCode) {
        return bankRepository.findByBankCode(bankCode);
    }

    public Bank requireActiveBank(String bankCode) {
        return bankRepository.findByBankCodeAndStatus(bankCode, BankStatus.ACTIVE)
                .orElseThrow(() -> new BankUnavailableException("Destination bank is unavailable or inactive"));
    }

    @Transactional
    public Bank updateBankAvailability(String bankCode, boolean active) {
        Bank bank = bankRepository.findByBankCode(bankCode)
                .orElseThrow(() -> new BankUnavailableException("Destination bank was not found"));
        bank.setStatus(active ? BankStatus.ACTIVE : BankStatus.INACTIVE);
        return bankRepository.save(bank);
    }

    @Transactional
    public void seedDefaultBanks() {
        if (bankRepository.count() > 0) {
            return;
        }
        List<Bank> defaults = List.of(
                new Bank("Access Bank", "SIM001", BankStatus.ACTIVE, TransferChannel.BANK_TRANSFER),
                new Bank("First Bank of Nigeria", "SIM002", BankStatus.ACTIVE, TransferChannel.BANK_TRANSFER),
                new Bank("United Bank for Africa", "SIM003", BankStatus.ACTIVE, TransferChannel.BANK_TRANSFER),
                new Bank("Zenith Bank", "SIM004", BankStatus.ACTIVE, TransferChannel.BANK_TRANSFER),
                new Bank("Guaranty Trust Bank", "SIM005", BankStatus.ACTIVE, TransferChannel.BANK_TRANSFER),
                new Bank("Fidelity Bank", "SIM006", BankStatus.ACTIVE, TransferChannel.BANK_TRANSFER),
                new Bank("Union Bank", "SIM007", BankStatus.ACTIVE, TransferChannel.BANK_TRANSFER),
                new Bank("Sterling Bank", "SIM008", BankStatus.ACTIVE, TransferChannel.BANK_TRANSFER),
                new Bank("Wema Bank", "SIM009", BankStatus.ACTIVE, TransferChannel.BANK_TRANSFER),
                new Bank("Stanbic IBTC Bank", "SIM010", BankStatus.ACTIVE, TransferChannel.BANK_TRANSFER),
                new Bank("Ecobank Nigeria", "SIM011", BankStatus.ACTIVE, TransferChannel.BANK_TRANSFER),
                new Bank("FCMB", "SIM012", BankStatus.ACTIVE, TransferChannel.BANK_TRANSFER),
                new Bank("Polaris Bank", "SIM013", BankStatus.ACTIVE, TransferChannel.BANK_TRANSFER),
                new Bank("Opay", "SIM014", BankStatus.ACTIVE, TransferChannel.BANK_TRANSFER),
                new Bank("Moniepoint", "SIM015", BankStatus.ACTIVE, TransferChannel.BANK_TRANSFER),
                new Bank("Kuda Bank", "SIM016", BankStatus.ACTIVE, TransferChannel.BANK_TRANSFER)
        );
        bankRepository.saveAll(defaults);
    }
}
