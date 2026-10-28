package com.nurudeen.ussd_wallet.wallet.service;

import com.nurudeen.ussd_wallet.config.RegistrationProperties;
import java.security.SecureRandom;
import org.springframework.stereotype.Service;

@Service
public class WalletNumberGenerationService {

    private final RegistrationProperties.WalletNumber properties;
    private final SecureRandom secureRandom;

    public WalletNumberGenerationService(RegistrationProperties properties) {
        this(properties, new SecureRandom());
    }

    WalletNumberGenerationService(
            RegistrationProperties properties,
            SecureRandom secureRandom) {
        this.properties = properties.walletNumber();
        this.secureRandom = secureRandom;
    }

    public String generate() {
        StringBuilder number = new StringBuilder(
                properties.prefix().length() + properties.randomDigits());
        number.append(properties.prefix());
        number.append(secureRandom.nextInt(9) + 1);
        for (int digit = 1; digit < properties.randomDigits(); digit++) {
            number.append(secureRandom.nextInt(10));
        }
        return number.toString();
    }
}
