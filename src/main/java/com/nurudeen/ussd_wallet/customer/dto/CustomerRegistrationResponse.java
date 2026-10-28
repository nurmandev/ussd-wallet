package com.nurudeen.ussd_wallet.customer.dto;

import com.nurudeen.ussd_wallet.customer.enums.CustomerStatus;
import com.nurudeen.ussd_wallet.wallet.dto.WalletResponse;
import java.time.Instant;
import java.util.UUID;

public record CustomerRegistrationResponse(
        UUID customerId,
        String phoneNumber,
        CustomerStatus customerStatus,
        WalletResponse wallet,
        boolean pinSetupRequired,
        Instant registeredAt) {
}
