package com.nurudeen.ussd_wallet.customer.service;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nurudeen.ussd_wallet.common.exception.InvalidPhoneNumberException;
import org.junit.jupiter.api.Test;

class NigerianPhoneNumberNormalizerTests {

    private final NigerianPhoneNumberNormalizer normalizer = new NigerianPhoneNumberNormalizer();

    @Test
    void rejectsNumbersWithLetters() {
        assertThrows(InvalidPhoneNumberException.class, () -> normalizer.normalize("080CALL1234"));
    }

    @Test
    void rejectsNumbersThatAreTooShortOrTooLong() {
        assertThrows(InvalidPhoneNumberException.class, () -> normalizer.normalize("080123"));
        assertThrows(InvalidPhoneNumberException.class, () -> normalizer.normalize("080123456789012"));
    }

    @Test
    void rejectsNonNigerianInternationalNumbers() {
        assertThrows(InvalidPhoneNumberException.class, () -> normalizer.normalize("+14155552671"));
    }
}
