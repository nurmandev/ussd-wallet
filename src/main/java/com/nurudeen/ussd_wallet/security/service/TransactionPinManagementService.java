package com.nurudeen.ussd_wallet.security.service;

import com.nurudeen.ussd_wallet.common.exception.PinAlreadyConfiguredException;
import com.nurudeen.ussd_wallet.common.exception.PinConfirmationMismatchException;
import com.nurudeen.ussd_wallet.common.exception.PinNotConfiguredException;
import com.nurudeen.ussd_wallet.security.dto.PinStatusResponse;
import com.nurudeen.ussd_wallet.security.entity.CustomerSecurity;
import com.nurudeen.ussd_wallet.security.entity.PinSecurityAudit;
import com.nurudeen.ussd_wallet.security.enums.PinSecurityEvent;
import com.nurudeen.ussd_wallet.security.enums.PinStatus;
import com.nurudeen.ussd_wallet.security.repository.CustomerSecurityRepository;
import com.nurudeen.ussd_wallet.security.repository.PinSecurityAuditRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransactionPinManagementService {

    private final CustomerSecurityRepository securityRepository;
    private final PinSecurityAuditRepository auditRepository;
    private final PinHashingService hashingService;
    private final PinVerificationService verificationService;
    private final Clock clock;
    private final TransactionTemplate independentTransaction;

    public TransactionPinManagementService(
            CustomerSecurityRepository securityRepository,
            PinSecurityAuditRepository auditRepository,
            PinHashingService hashingService,
            PinVerificationService verificationService,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.securityRepository = securityRepository;
        this.auditRepository = auditRepository;
        this.hashingService = hashingService;
        this.verificationService = verificationService;
        this.clock = clock;
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void setup(UUID customerId, String pin, String confirmation) {
        requireMatchingPins(pin, confirmation);
        validateStrength(pin);
        String encodedPin = hashingService.hash(pin);
        independentTransaction.executeWithoutResult(status -> {
            CustomerSecurity security = securityRepository.findByCustomerIdForUpdate(customerId)
                    .orElseThrow(PinNotConfiguredException::new);
            if (security.isPinConfigured()) {
                throw new PinAlreadyConfiguredException();
            }
            Instant now = clock.instant();
            security.configurePin(encodedPin, now);
            auditRepository.save(new PinSecurityAudit(
                    customerId, security.getWalletId(), PinSecurityEvent.PIN_SETUP));
        });
    }

    public void change(UUID customerId, String currentPin, String newPin, String confirmation) {
        requireMatchingPins(newPin, confirmation);
        validateStrength(newPin);
        String encodedPin = hashingService.hash(newPin);
        verificationService.verifyAndChange(customerId, currentPin, encodedPin);
    }

    public PinStatusResponse status(UUID customerId) {
        CustomerSecurity security = securityRepository.findById(customerId).orElse(null);
        if (security == null) {
            return new PinStatusResponse(false, false, null);
        }
        Instant lockedUntil = security.getLockedUntil();
        boolean locked = security.getPinStatus() == PinStatus.LOCKED
                && (lockedUntil == null || lockedUntil.isAfter(clock.instant()));
        return new PinStatusResponse(
                security.isPinConfigured(),
                locked,
                locked ? lockedUntil : null);
    }

    private void requireMatchingPins(String pin, String confirmation) {
        if (!pin.equals(confirmation)) {
            throw new PinConfirmationMismatchException();
        }
    }

    private void validateStrength(String pin) {
        TransactionPinPolicy.validateStrength(pin);
    }
}
