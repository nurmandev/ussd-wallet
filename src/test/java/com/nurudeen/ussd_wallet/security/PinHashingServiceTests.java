package com.nurudeen.ussd_wallet.security;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nurudeen.ussd_wallet.config.PinSecurityProperties;
import com.nurudeen.ussd_wallet.security.service.PinHashingService;
import org.junit.jupiter.api.Test;

class PinHashingServiceTests {

    @Test
    void storesSaltedArgon2HashesAndVerifiesWithoutReturningPlaintext() {
        PinHashingService service = new PinHashingService(new PinSecurityProperties(
                5, 15, 16, 32, 1, 16384, 2, 5, "test-pin-pepper-that-is-at-least-32-chars"));

        String firstHash = service.hash("4829");
        String secondHash = service.hash("4829");

        assertNotEquals("4829", firstHash);
        assertNotEquals(firstHash, secondHash);
        assertTrue(firstHash.startsWith("$argon2id$"));
        assertTrue(service.matches("4829", firstHash));
        assertFalse(service.matches("4828", firstHash));

        PinHashingService differentPepper = new PinHashingService(new PinSecurityProperties(
                5, 15, 16, 32, 1, 16384, 2, 5, "different-pin-pepper-that-is-over-32-chars"));
        assertFalse(differentPepper.matches("4829", firstHash));
        assertThrows(IllegalStateException.class, () -> new PinHashingService(
                new PinSecurityProperties(5, 15, 16, 32, 1, 16384, 2, 5, "")));
    }
}
