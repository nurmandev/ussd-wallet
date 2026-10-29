package com.nurudeen.ussd_wallet.security.service;

import com.nurudeen.ussd_wallet.common.exception.InvalidPinRecoveryAuthorizationException;
import com.nurudeen.ussd_wallet.common.exception.PinConfirmationMismatchException;
import com.nurudeen.ussd_wallet.common.exception.PinNotConfiguredException;
import com.nurudeen.ussd_wallet.config.PinSecurityProperties;
import com.nurudeen.ussd_wallet.security.dto.PinRecoveryAuthorizationResponse;
import com.nurudeen.ussd_wallet.security.entity.CustomerSecurity;
import com.nurudeen.ussd_wallet.security.entity.PinRecoveryAuthorization;
import com.nurudeen.ussd_wallet.security.entity.PinSecurityAudit;
import com.nurudeen.ussd_wallet.security.enums.PinSecurityEvent;
import com.nurudeen.ussd_wallet.security.repository.CustomerSecurityRepository;
import com.nurudeen.ussd_wallet.security.repository.PinRecoveryAuthorizationRepository;
import com.nurudeen.ussd_wallet.security.repository.PinSecurityAuditRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PinRecoveryService {

    private final OtpVerificationService otpVerificationService;
    private final CustomerSecurityRepository securityRepository;
    private final PinRecoveryAuthorizationRepository authorizationRepository;
    private final PinSecurityAuditRepository auditRepository;
    private final PinHashingService hashingService;
    private final PinSecurityProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final TransactionTemplate independentTransaction;

    public PinRecoveryService(
            OtpVerificationService otpVerificationService,
            CustomerSecurityRepository securityRepository,
            PinRecoveryAuthorizationRepository authorizationRepository,
            PinSecurityAuditRepository auditRepository,
            PinHashingService hashingService,
            PinSecurityProperties properties,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.otpVerificationService = otpVerificationService;
        this.securityRepository = securityRepository;
        this.authorizationRepository = authorizationRepository;
        this.auditRepository = auditRepository;
        this.hashingService = hashingService;
        this.properties = properties;
        this.clock = clock;
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void requestOtp(UUID customerId) {
        CustomerSecurity existingSecurity = securityRepository.findById(customerId)
                .orElseThrow(PinNotConfiguredException::new);
        if (!existingSecurity.isPinConfigured()) {
            throw new PinNotConfiguredException();
        }
        otpVerificationService.requestOtp(customerId, OtpVerificationService.PIN_RECOVERY_PURPOSE);
        independentTransaction.executeWithoutResult(status -> {
            CustomerSecurity security = securityRepository.findByCustomerIdForUpdate(customerId)
                    .orElseThrow(PinNotConfiguredException::new);
            if (!security.isPinConfigured()) {
                throw new PinNotConfiguredException();
            }
            auditRepository.save(new PinSecurityAudit(
                    customerId, security.getWalletId(), PinSecurityEvent.PIN_RESET_OTP_REQUESTED));
        });
    }

    public PinRecoveryAuthorizationResponse verifyOtp(UUID customerId, String otp) {
        boolean verified = otpVerificationService.verifyAndConsume(
                customerId, OtpVerificationService.PIN_RECOVERY_PURPOSE, otp);
        if (!verified) {
            throw new InvalidPinRecoveryAuthorizationException();
        }

        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String recoveryToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = sha256Hex(recoveryToken);
        independentTransaction.executeWithoutResult(status -> {
            CustomerSecurity security = securityRepository.findByCustomerIdForUpdate(customerId)
                    .orElseThrow(PinNotConfiguredException::new);
            authorizationRepository.findUnconsumedByCustomerId(customerId)
                    .forEach(authorization -> authorization.consume(clock.instant()));
            Instant now = clock.instant();
            authorizationRepository.save(new PinRecoveryAuthorization(
                    customerId,
                    tokenHash,
                    now.plus(properties.recoveryTokenMinutes(), ChronoUnit.MINUTES)));
            auditRepository.save(new PinSecurityAudit(
                    customerId, security.getWalletId(), PinSecurityEvent.PIN_RESET_OTP_VERIFIED));
        });
        return new PinRecoveryAuthorizationResponse(recoveryToken);
    }

    public void completeReset(UUID customerId, String recoveryToken, String newPin, String confirmation) {
        if (!newPin.equals(confirmation)) {
            throw new PinConfirmationMismatchException();
        }
        TransactionPinPolicy.validateStrength(newPin);
        String encodedPin = hashingService.hash(newPin);
        String tokenHash = sha256Hex(recoveryToken);
        independentTransaction.executeWithoutResult(status -> {
            CustomerSecurity security = securityRepository.findByCustomerIdForUpdate(customerId)
                    .orElseThrow(PinNotConfiguredException::new);
            Instant now = clock.instant();
            PinRecoveryAuthorization authorization = authorizationRepository
                    .findByTokenHashForUpdate(tokenHash)
                    .filter(candidate -> candidate.getCustomerId().equals(customerId))
                    .filter(candidate -> candidate.getConsumedAt() == null)
                    .filter(candidate -> candidate.getExpiresAt().isAfter(now))
                    .orElseThrow(InvalidPinRecoveryAuthorizationException::new);
            authorization.consume(now);
            authorizationRepository.findUnconsumedByCustomerId(customerId)
                    .forEach(candidate -> candidate.consume(now));
            otpVerificationService.invalidate(
                    customerId, OtpVerificationService.PIN_RECOVERY_PURPOSE);
            security.replacePin(encodedPin, now);
            auditRepository.save(new PinSecurityAudit(
                    customerId, security.getWalletId(), PinSecurityEvent.PIN_RESET));
        });
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
