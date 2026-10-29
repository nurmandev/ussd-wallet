package com.nurudeen.ussd_wallet.wallet.service;

import com.nurudeen.ussd_wallet.config.WalletProperties;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WalletNumberGeneratorService {

    private final WalletProperties.Number properties;
    private final SecureRandom secureRandom;

    @Autowired
    public WalletNumberGeneratorService(WalletProperties properties) {
        this(properties, new SecureRandom());
    }

    WalletNumberGeneratorService(WalletProperties properties, SecureRandom secureRandom) {
        this.properties = properties.number();
        this.secureRandom = secureRandom;
    }

    public String generate() {
        int randomDigits = properties.length() - properties.prefix().length();
        StringBuilder number = new StringBuilder(properties.length()).append(properties.prefix());
        for (int digit = 0; digit < randomDigits; digit++) {
            number.append(secureRandom.nextInt(10));
        }
        return number.toString();
    }
}
