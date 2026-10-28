package com.nurudeen.ussd_wallet.customer.dto;

import com.nurudeen.ussd_wallet.customer.enums.CustomerStatus;
import com.nurudeen.ussd_wallet.wallet.enums.WalletStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CustomerRegistrationResponse(
        UUID customerId,
        String phoneNumber,
        CustomerStatus customerStatus,
        String walletNumber,
        WalletStatus walletStatus,
        String currency,
        Balance balance,
        boolean pinSetupRequired,
        Instant registeredAt) {

    public record Balance(
            BigDecimal ledgerBalance,
            BigDecimal availableBalance,
            BigDecimal reservedBalance) {
    }
}
