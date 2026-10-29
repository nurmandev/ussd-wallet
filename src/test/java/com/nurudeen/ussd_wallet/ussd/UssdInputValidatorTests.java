package com.nurudeen.ussd_wallet.ussd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nurudeen.ussd_wallet.ussd.service.UssdInputValidator;
import com.nurudeen.ussd_wallet.config.TransferProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class UssdInputValidatorTests {

    private final UssdInputValidator validator = new UssdInputValidator(new TransferProperties(
            new BigDecimal("0.01"), new BigDecimal("1000000.00")));

    @Test
    void validatesWalletNumbersAndAmounts() {
        assertEquals("1234567890", validator.walletNumber("1234567890"));
        assertEquals(new BigDecimal("25.50"), validator.amount("25.5"));
        assertEquals(new BigDecimal("99.00"), validator.amount("99"));
    }

    @Test
    void rejectsMalformedAndOutOfRangeInput() {
        assertThrows(IllegalArgumentException.class, () -> validator.walletNumber("wallet123"));
        assertThrows(IllegalArgumentException.class, () -> validator.amount("1e4"));
        assertThrows(IllegalArgumentException.class, () -> validator.amount("0"));
        assertThrows(IllegalArgumentException.class, () -> validator.amount("1000000.01"));
        assertThrows(IllegalArgumentException.class, () -> validator.input("1\n2"));
    }
}
