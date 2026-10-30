package com.nurudeen.ussd_wallet.wallet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DevelopmentTransferNotificationListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(DevelopmentTransferNotificationListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransferCompleted(WalletTransferCompletedEvent event) {
        LOGGER.info("Transfer notification hook invoked after commit for reference {}", event.reference());
    }
}
