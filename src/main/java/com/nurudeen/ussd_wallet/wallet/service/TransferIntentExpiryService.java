package com.nurudeen.ussd_wallet.wallet.service;

import com.nurudeen.ussd_wallet.wallet.repository.WalletTransferIntentRepository;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferIntentExpiryService {

    private final WalletTransferIntentRepository intentRepository;
    private final Clock clock;

    public TransferIntentExpiryService(
            WalletTransferIntentRepository intentRepository, Clock clock) {
        this.intentRepository = intentRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${TRANSFER_INTENT_EXPIRY_SCAN_MILLIS:60000}")
    @Transactional
    public void expirePendingIntents() {
        intentRepository.expirePendingBefore(clock.instant());
    }
}
