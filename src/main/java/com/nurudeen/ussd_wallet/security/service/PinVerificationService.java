package com.nurudeen.ussd_wallet.security.service;

import com.nurudeen.ussd_wallet.common.exception.InvalidTransactionPinException;
import com.nurudeen.ussd_wallet.common.exception.PinNotConfiguredException;
import com.nurudeen.ussd_wallet.common.exception.PinReuseException;
import com.nurudeen.ussd_wallet.common.exception.PinVerificationLockedException;
import com.nurudeen.ussd_wallet.config.PinSecurityProperties;
import com.nurudeen.ussd_wallet.security.entity.CustomerSecurity;
import com.nurudeen.ussd_wallet.security.entity.PinSecurityAudit;
import com.nurudeen.ussd_wallet.security.enums.PinSecurityEvent;
import com.nurudeen.ussd_wallet.security.enums.PinStatus;
import com.nurudeen.ussd_wallet.security.repository.CustomerSecurityRepository;
import com.nurudeen.ussd_wallet.security.repository.PinRecoveryAuthorizationRepository;
import com.nurudeen.ussd_wallet.security.repository.PinSecurityAuditRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PinVerificationService {

    private final CustomerSecurityRepository securityRepository;
    private final PinSecurityAuditRepository auditRepository;
    private final PinRecoveryAuthorizationRepository authorizationRepository;
    private final PinHashingService hashingService;
    private final OtpVerificationService otpVerificationService;
    private final PinSecurityProperties properties;
    private final Clock clock;
    private final TransactionTemplate independentTransaction;

    public PinVerificationService(
            CustomerSecurityRepository securityRepository,
            PinSecurityAuditRepository auditRepository,
            PinRecoveryAuthorizationRepository authorizationRepository,
            PinHashingService hashingService,
            OtpVerificationService otpVerificationService,
            PinSecurityProperties properties,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.securityRepository = securityRepository;
        this.auditRepository = auditRepository;
        this.authorizationRepository = authorizationRepository;
        this.hashingService = hashingService;
        this.otpVerificationService = otpVerificationService;
        this.properties = properties;
        this.clock = clock;
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void verify(UUID customerId, String suppliedPin) {
        VerificationResult result = independentTransaction.execute(
                status -> verifyInTransaction(customerId, suppliedPin, null));
        throwForFailure(result);
    }

    public void verifyAndChange(UUID customerId, String currentPin, String newEncodedPin) {
        VerificationResult result = independentTransaction.execute(
                status -> verifyInTransaction(customerId, currentPin, newEncodedPin));
        throwForFailure(result);
    }

    private VerificationResult verifyInTransaction(UUID customerId, String suppliedPin, String newEncodedPin) {
        CustomerSecurity security = securityRepository.findByCustomerIdForUpdate(customerId).orElse(null);
        if (security == null || !security.isPinConfigured()) {
            return VerificationResult.NOT_CONFIGURED;
        }

        Instant now = clock.instant();
        Instant lockedUntil = security.getLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(now)) {
            return VerificationResult.LOCKED;
        }
        if (security.getPinStatus() == PinStatus.LOCKED) {
            if (lockedUntil == null) {
                return VerificationResult.LOCKED;
            }
            security.unlockExpiredLockout();
        }

        if (!hashingService.matches(suppliedPin, security.getPinHash())) {
            boolean newlyLocked = security.recordFailedAttempt(
                    properties.maxFailedAttempts(),
                    now.plus(properties.lockoutMinutes(), ChronoUnit.MINUTES));
            auditRepository.save(new PinSecurityAudit(
                    customerId, security.getWalletId(), PinSecurityEvent.PIN_VERIFICATION_FAILED));
            if (newlyLocked) {
                auditRepository.save(new PinSecurityAudit(
                        customerId, security.getWalletId(), PinSecurityEvent.PIN_LOCKED));
                return VerificationResult.LOCKED;
            }
            return VerificationResult.INVALID;
        }

        if (newEncodedPin == null) {
            security.recordSuccessfulVerification(now);
            auditRepository.save(new PinSecurityAudit(
                    customerId, security.getWalletId(), PinSecurityEvent.PIN_VERIFICATION_SUCCESS));
            return VerificationResult.SUCCESS;
        }

        if (hashingService.matches(suppliedPin, newEncodedPin)) {
            security.recordSuccessfulVerification(now);
            auditRepository.save(new PinSecurityAudit(
                    customerId, security.getWalletId(), PinSecurityEvent.PIN_VERIFICATION_SUCCESS));
            return VerificationResult.REUSED;
        }

        otpVerificationService.invalidate(customerId, OtpVerificationService.PIN_RECOVERY_PURPOSE);
        authorizationRepository.findUnconsumedByCustomerId(customerId)
                .forEach(authorization -> authorization.consume(now));
        security.replacePin(newEncodedPin, now);
        auditRepository.save(new PinSecurityAudit(
                customerId, security.getWalletId(), PinSecurityEvent.PIN_CHANGED));
        return VerificationResult.SUCCESS;
    }

    private void throwForFailure(VerificationResult result) {
        if (result == null) {
            throw new IllegalStateException("PIN verification transaction returned no result");
        }
        switch (result) {
            case SUCCESS -> {
                return;
            }
            case INVALID -> throw new InvalidTransactionPinException();
            case LOCKED -> throw new PinVerificationLockedException();
            case NOT_CONFIGURED -> throw new PinNotConfiguredException();
            case REUSED -> throw new PinReuseException();
        }
    }

    private enum VerificationResult {
        SUCCESS,
        INVALID,
        LOCKED,
        NOT_CONFIGURED,
        REUSED
    }
}
