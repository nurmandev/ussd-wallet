package com.nurudeen.ussd_wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nurudeen.ussd_wallet.customer.service.NigerianPhoneNumberNormalizer;
import org.junit.jupiter.api.Test;

class UssdWalletApplicationTests {

    private final NigerianPhoneNumberNormalizer normalizer = new NigerianPhoneNumberNormalizer();

    @Test
    void normalizesLocalNigerianNumber() {
        assertEquals("+2348012345678", normalizer.normalize(" 08012345678 "));
    }

    @Test
    void normalizesNigerianNumberWithCountryCode() {
        assertEquals("+2348012345678", normalizer.normalize("2348012345678"));
        assertEquals("+2348012345678", normalizer.normalize("+2348012345678"));
    }
}
