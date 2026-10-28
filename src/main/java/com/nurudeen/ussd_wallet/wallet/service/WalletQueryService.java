package com.nurudeen.ussd_wallet.wallet.service;

import com.nurudeen.ussd_wallet.common.exception.WalletNotFoundException;
import com.nurudeen.ussd_wallet.wallet.dto.WalletResponse;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.nurudeen.ussd_wallet.wallet.repository.WalletRepository;

@Service
public class WalletQueryService {

    private final WalletRepository walletRepository;

    public WalletQueryService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional(readOnly = true)
    public WalletResponse getForCustomer(UUID customerId) {
        Wallet wallet = walletRepository.findByCustomer_Id(customerId)
                .orElseThrow(WalletNotFoundException::new);
        if (wallet.getBalance() == null) {
            throw new IllegalStateException("Wallet balance record is missing");
        }
        return WalletResponse.from(wallet, wallet.getBalance());
    }
}
