package com.nurudeen.ussd_wallet.security.service;

import com.nurudeen.ussd_wallet.config.PinSecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PinHashingService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final Argon2PasswordEncoder encoder;
    private final SecretKeySpec pepperKey;

    public PinHashingService(PinSecurityProperties properties) {
        if (properties.pepper() == null || properties.pepper().length() < 32) {
            throw new IllegalStateException("PIN_PEPPER must contain at least 32 characters");
        }
        this.encoder = new Argon2PasswordEncoder(
                properties.argon2SaltLength(),
                properties.argon2HashLength(),
                properties.argon2Parallelism(),
                properties.argon2MemoryKib(),
                properties.argon2Iterations());
        this.pepperKey = new SecretKeySpec(
                properties.pepper().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    public String hash(String pin) {
        return encoder.encode(pepperedPin(pin));
    }

    public boolean matches(String pin, String encodedPin) {
        return encoder.matches(pepperedPin(pin), encodedPin);
    }

    private String pepperedPin(String pin) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(pepperKey);
            return Base64.getEncoder().encodeToString(mac.doFinal(pin.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 is required by the Java runtime", exception);
        }
    }
}
