package com.nurudeen.ussd_wallet.security.service;

import com.nurudeen.ussd_wallet.common.exception.OtpRecoveryUnavailableException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(OtpVerificationService.class)
public class DisabledOtpVerificationService implements OtpVerificationService {

    @Override
    public void requestOtp(UUID authenticatedCustomerId, String purpose) {
        throw new OtpRecoveryUnavailableException();
    }

    @Override
    public void invalidate(UUID authenticatedCustomerId, String purpose) {
        // This provider cannot issue challenges, so there is nothing to revoke.
    }

    @Override
    public boolean verifyAndConsume(UUID authenticatedCustomerId, String purpose, String otp) {
        throw new OtpRecoveryUnavailableException();
    }
}
