package com.nurudeen.ussd_wallet.ussd.service;

import com.nurudeen.ussd_wallet.ussd.config.UssdProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;

@Service
public class UssdSimulatorAuthenticationService {

    private final UssdProperties properties;

    public UssdSimulatorAuthenticationService(UssdProperties properties) {
        this.properties = properties;
    }

    public void verify(String suppliedSecret) {
        String configuredSecret = properties.simulatorSecret();
        if (configuredSecret == null
                || configuredSecret.length() < 32
                || suppliedSecret == null
                || !MessageDigest.isEqual(
                        configuredSecret.getBytes(StandardCharsets.UTF_8),
                        suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new UssdGatewayAuthenticationService.GatewayAuthenticationException();
        }
    }
}
