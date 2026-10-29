package com.nurudeen.ussd_wallet.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nurudeen.ussd_wallet.common.exception.WeakPinException;
import com.nurudeen.ussd_wallet.security.service.TransactionPinPolicy;
import org.junit.jupiter.api.Test;

class TransactionPinPolicyTests {

    @Test
    void rejectsCommonRepeatedAndSequentialPins() {
        for (String pin : new String[] {
            "0000", "1111", "1234", "4321", "123456", "000000", "2345", "9876"
        }) {
            assertThrows(WeakPinException.class, () -> TransactionPinPolicy.validateStrength(pin));
        }
    }

    @Test
    void acceptsNonObviousPin() {
        assertDoesNotThrow(() -> TransactionPinPolicy.validateStrength("4829"));
    }
}
