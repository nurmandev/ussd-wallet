package com.nurudeen.ussd_wallet.wallet.service;

import com.nurudeen.ussd_wallet.common.exception.WalletNumberCollisionException;
import com.nurudeen.ussd_wallet.config.RegistrationProperties;
import com.nurudeen.ussd_wallet.customer.entity.Customer;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;

@Service
public class WalletCreationService {

    private final WalletNumberGenerationService numberGenerationService;
    private final WalletRepository walletRepository;
    private final int maxAttempts;

    public WalletCreationService(
            WalletNumberGenerationService numberGenerationService,
            WalletRepository walletRepository,
            RegistrationProperties properties) {
        this.numberGenerationService = numberGenerationService;
        this.walletRepository = walletRepository;
        this.maxAttempts = properties.walletNumber().maxAttempts();
    }

    public Wallet createWallet(Customer customer) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String walletNumber = numberGenerationService.generate();
            if (!walletRepository.existsByWalletNumber(walletNumber)) {
                return walletRepository.saveAndFlush(new Wallet(customer, walletNumber));
            }
        }
        throw new WalletNumberCollisionException();
    }
}
