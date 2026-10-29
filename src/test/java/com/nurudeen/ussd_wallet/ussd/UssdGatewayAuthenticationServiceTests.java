package com.nurudeen.ussd_wallet.ussd;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nurudeen.ussd_wallet.ussd.config.UssdProperties;
import com.nurudeen.ussd_wallet.ussd.dto.UssdGatewayRequest;
import com.nurudeen.ussd_wallet.ussd.service.UssdGatewayAuthenticationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UssdGatewayAuthenticationServiceTests {

    private static final String SECRET = "a-test-gateway-shared-secret-longer-than-32-characters";
    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private final UssdGatewayAuthenticationService service =
            new UssdGatewayAuthenticationService(
                    new UssdProperties("*123#", 120, 604800, 30, SECRET, null, false),
                    Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsValidFreshSignatureAndRejectsTamperedPayload() {
        String timestamp = Long.toString(NOW.getEpochSecond());
        UssdGatewayRequest request = request("1");
        String signature = service.sign(request, timestamp);

        assertDoesNotThrow(() -> service.verify(request, timestamp, signature));
        assertThrows(
                UssdGatewayAuthenticationService.GatewayAuthenticationException.class,
                () -> service.verify(request("2"), timestamp, signature));
    }

    @Test
    void rejectsExpiredSignature() {
        String timestamp = Long.toString(NOW.minusSeconds(301).getEpochSecond());
        UssdGatewayRequest request = request("");
        String signature = service.sign(request, timestamp);

        assertThrows(
                UssdGatewayAuthenticationService.GatewayAuthenticationException.class,
                () -> service.verify(request, timestamp, signature));
    }

    private UssdGatewayRequest request(String text) {
        return new UssdGatewayRequest(
                "MTN-SESSION-1", "*123#", "08012345678", text, "MTN", "callback-1");
    }
}
