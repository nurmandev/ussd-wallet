package com.nurudeen.ussd_wallet.wallet.service;

import com.nurudeen.ussd_wallet.common.exception.WalletNumberCollisionException;
import com.nurudeen.ussd_wallet.common.exception.WalletAlreadyExistsException;
import com.nurudeen.ussd_wallet.config.WalletProperties;
import com.nurudeen.ussd_wallet.customer.entity.Customer;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;

@Service
public class WalletCreationService {

    private final WalletNumberGeneratorService numberGeneratorService;
    private final WalletRepository walletRepository;
    private final WalletProperties walletProperties;
    private final int maxRetries;

    public WalletCreationService(
            WalletNumberGeneratorService numberGeneratorService,
            WalletRepository walletRepository,
            WalletProperties properties) {
        this.numberGeneratorService = numberGeneratorService;
        this.walletRepository = walletRepository;
        this.walletProperties = properties;
        this.maxRetries = properties.number().maxRetries();
    }

    public Wallet createWallet(Customer customer) {
        if (walletRepository.existsByCustomer_Id(customer.getId())) {
            throw new WalletAlreadyExistsException();
        }

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            String walletNumber = numberGeneratorService.generate();
            if (!walletRepository.existsByWalletNumber(walletNumber)) {
                return walletRepository.saveAndFlush(
                        new Wallet(customer, walletNumber, walletProperties.currency()));
            }
        }
        throw new WalletNumberCollisionException();
    }
}
