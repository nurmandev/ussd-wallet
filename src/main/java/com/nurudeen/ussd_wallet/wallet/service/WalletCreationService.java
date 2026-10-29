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

    public WalletCreationService(
            WalletNumberGeneratorService numberGeneratorService,
            WalletRepository walletRepository,
            WalletProperties properties) {
        this.numberGeneratorService = numberGeneratorService;
        this.walletRepository = walletRepository;
        this.walletProperties = properties;
    }

    public Wallet createWallet(Customer customer) {
        if (walletRepository.existsByCustomer_Id(customer.getId())) {
            throw new WalletAlreadyExistsException();
        }

        String walletNumber = numberGeneratorService.generate();
        if (walletRepository.existsByWalletNumber(walletNumber)) {
            throw new WalletNumberCollisionException();
        }
        return walletRepository.saveAndFlush(
                new Wallet(customer, walletNumber, walletProperties.currency()));
    }
}
