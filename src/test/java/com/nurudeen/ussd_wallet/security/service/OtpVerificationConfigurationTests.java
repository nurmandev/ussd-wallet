package com.nurudeen.ussd_wallet.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.nurudeen.ussd_wallet.common.exception.OtpRecoveryUnavailableException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class OtpVerificationConfigurationTests {

    @Test
    void registersFailClosedProviderWhenNoOtpProviderExists() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(DisabledOtpVerificationService.class);
            context.refresh();

            assertThat(context.getBeansOfType(OtpVerificationService.class)).hasSize(1);
            OtpVerificationService otpService = context.getBean(OtpVerificationService.class);
            assertThat(otpService).isInstanceOf(DisabledOtpVerificationService.class);
            assertThatCode(() -> otpService.invalidate(
                            UUID.randomUUID(), OtpVerificationService.PIN_RECOVERY_PURPOSE))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void failsOtpRecoveryWhenProviderIsDisabled() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(DisabledOtpVerificationService.class)) {
            OtpVerificationService otpService = context.getBean(OtpVerificationService.class);
            org.junit.jupiter.api.Assertions.assertThrows(
                    OtpRecoveryUnavailableException.class,
                    () -> otpService.requestOtp(UUID.randomUUID(), OtpVerificationService.PIN_RECOVERY_PURPOSE));
        }
    }
}
