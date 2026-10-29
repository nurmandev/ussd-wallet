package com.nurudeen.ussd_wallet.ussd.service;

import com.nurudeen.ussd_wallet.ussd.config.UssdProperties;
import com.nurudeen.ussd_wallet.ussd.dto.UssdGatewayRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class UssdGatewayAuthenticationService {

    private static final Duration SIGNATURE_WINDOW = Duration.ofMinutes(5);
    private final UssdProperties properties;
    private final Clock clock;

    public UssdGatewayAuthenticationService(UssdProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void verify(UssdGatewayRequest request, String timestampHeader, String signatureHeader) {
        if (properties.gatewaySecret() == null || properties.gatewaySecret().length() < 32) {
            throw new GatewayAuthenticationException();
        }
        try {
            Instant timestamp = Instant.ofEpochSecond(Long.parseLong(timestampHeader));
            if (Duration.between(timestamp, clock.instant()).abs().compareTo(SIGNATURE_WINDOW) > 0) {
                throw new GatewayAuthenticationException();
            }
            String expected = sign(request, timestampHeader);
            byte[] expectedBytes = HexFormat.of().parseHex(expected);
            byte[] providedBytes = HexFormat.of().parseHex(signatureHeader);
            if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
                throw new GatewayAuthenticationException();
            }
        } catch (RuntimeException exception) {
            if (exception instanceof GatewayAuthenticationException authException) {
                throw authException;
            }
            throw new GatewayAuthenticationException();
        }
    }

    public String sign(UssdGatewayRequest request, String timestampHeader) {
        String canonical = String.join("\n",
                timestampHeader,
                request.sessionId(),
                request.callbackId(),
                request.serviceCode(),
                request.phoneNumber(),
                request.networkCode(),
                request.text() == null ? "" : request.text());
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.gatewaySecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 is required by the Java runtime", exception);
        }
    }

    public static class GatewayAuthenticationException extends RuntimeException {

        public GatewayAuthenticationException() {
            super("USSD gateway authentication failed");
        }
    }
}
