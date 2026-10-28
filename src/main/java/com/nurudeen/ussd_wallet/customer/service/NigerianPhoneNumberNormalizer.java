package com.nurudeen.ussd_wallet.customer.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.nurudeen.ussd_wallet.common.exception.InvalidPhoneNumberException;
import org.springframework.stereotype.Service;

@Service
public class NigerianPhoneNumberNormalizer implements PhoneNumberNormalizer {

    private static final String NIGERIA_REGION = "NG";
    private static final int NIGERIA_COUNTRY_CODE = 234;

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    @Override
    public String normalize(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new InvalidPhoneNumberException();
        }

        String candidate = phoneNumber.strip();
        if (!candidate.matches("[+0-9()\\s-]+")) {
            throw new InvalidPhoneNumberException();
        }
        if (candidate.startsWith("234")) {
            candidate = "+" + candidate;
        }

        try {
            Phonenumber.PhoneNumber parsed = phoneNumberUtil.parse(candidate, NIGERIA_REGION);
            if (parsed.getCountryCode() != NIGERIA_COUNTRY_CODE
                    || !phoneNumberUtil.isValidNumberForRegion(parsed, NIGERIA_REGION)) {
                throw new InvalidPhoneNumberException();
            }
            return phoneNumberUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException exception) {
            throw new InvalidPhoneNumberException();
        }
    }
}
