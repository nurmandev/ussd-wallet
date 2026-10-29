package com.nurudeen.ussd_wallet.security.service;

import com.nurudeen.ussd_wallet.common.exception.WeakPinException;
import java.util.Set;

public final class TransactionPinPolicy {

    private static final Set<String> COMMON_PINS = Set.of(
            "0000", "1111", "1234", "4321", "123456", "000000",
            "2580", "0852", "1212", "1122", "6969", "7777");

    private TransactionPinPolicy() {
    }

    public static void validateStrength(String pin) {
        if (COMMON_PINS.contains(pin) || isRepeatedDigit(pin) || isSequential(pin)) {
            throw new WeakPinException();
        }
    }

    private static boolean isRepeatedDigit(String pin) {
        return pin.chars().distinct().count() == 1;
    }

    private static boolean isSequential(String pin) {
        if (pin.length() < 4) {
            return false;
        }
        int firstStep = Math.floorMod(pin.charAt(1) - pin.charAt(0), 10);
        if (firstStep != 1 && firstStep != 9) {
            return false;
        }
        for (int index = 2; index < pin.length(); index++) {
            if (Math.floorMod(pin.charAt(index) - pin.charAt(index - 1), 10) != firstStep) {
                return false;
            }
        }
        return true;
    }
}
