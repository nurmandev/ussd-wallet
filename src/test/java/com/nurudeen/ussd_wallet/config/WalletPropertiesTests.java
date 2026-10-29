package com.nurudeen.ussd_wallet.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WalletPropertiesTests {

    @Test
    void rejectsPrefixThatIsNotShorterThanWalletNumberLength() {
        assertThrows(IllegalArgumentException.class, () -> new WalletProperties(
                new WalletProperties.Number("10", 2, 5),
                "NGN"));
    }

    @Test
    void usesTenDigitWalletNumberDefaults() {
        WalletProperties properties = new WalletProperties(null, null);

        assertEquals("10", properties.number().prefix());
        assertEquals(10, properties.number().length());
        assertEquals(5, properties.number().maxRetries());
        assertEquals("NGN", properties.currency());
    }
}
