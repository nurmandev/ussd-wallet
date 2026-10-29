package com.nurudeen.ussd_wallet.ussd;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nurudeen.ussd_wallet.ussd.config.UssdProperties;
import com.nurudeen.ussd_wallet.ussd.service.UssdGatewayAuthenticationService;
import com.nurudeen.ussd_wallet.ussd.service.UssdSimulatorAuthenticationService;
import org.junit.jupiter.api.Test;

class UssdSimulatorAuthenticationServiceTests {

    @Test
    void acceptsOnlyConfiguredSimulatorSecret() {
        String secret = "local-simulator-secret-that-is-long-enough-123";
        UssdSimulatorAuthenticationService service = new UssdSimulatorAuthenticationService(
                new UssdProperties("*123#", 120, 604800, 30, "gateway-secret-that-is-at-least-32-chars", secret, true));

        assertDoesNotThrow(() -> service.verify(secret));
        assertThrows(
                UssdGatewayAuthenticationService.GatewayAuthenticationException.class,
                () -> service.verify("wrong-secret"));
    }

    @Test
    void refusesToRunWithoutAnIndependentSecret() {
        UssdSimulatorAuthenticationService service = new UssdSimulatorAuthenticationService(
                new UssdProperties("*123#", 120, 604800, 30, "gateway-secret-that-is-at-least-32-chars", "", true));

        assertThrows(
                UssdGatewayAuthenticationService.GatewayAuthenticationException.class,
                () -> service.verify("anything"));
    }
}
