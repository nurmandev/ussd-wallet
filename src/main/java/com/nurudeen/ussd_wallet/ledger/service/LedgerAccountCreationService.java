package com.nurudeen.ussd_wallet.ledger.service;

import com.nurudeen.ussd_wallet.ledger.entity.LedgerAccount;
import com.nurudeen.ussd_wallet.ledger.repository.LedgerAccountRepository;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import org.springframework.stereotype.Service;

@Service
public class LedgerAccountCreationService {

    private final LedgerAccountRepository ledgerAccountRepository;

    public LedgerAccountCreationService(LedgerAccountRepository ledgerAccountRepository) {
        this.ledgerAccountRepository = ledgerAccountRepository;
    }

    public LedgerAccount createCustomerWalletLiabilityAccount(Wallet wallet) {
        return ledgerAccountRepository.saveAndFlush(new LedgerAccount(wallet));
    }
}
