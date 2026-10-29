package com.nurudeen.ussd_wallet.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nurudeen.ussd_wallet.common.exception.InvalidTransactionPinException;
import com.nurudeen.ussd_wallet.common.exception.PinReuseException;
import com.nurudeen.ussd_wallet.common.exception.PinVerificationLockedException;
import com.nurudeen.ussd_wallet.config.PinSecurityProperties;
import com.nurudeen.ussd_wallet.customer.entity.Customer;
import com.nurudeen.ussd_wallet.customer.enums.CustomerStatus;
import com.nurudeen.ussd_wallet.security.entity.CustomerSecurity;
import com.nurudeen.ussd_wallet.security.entity.PinRecoveryAuthorization;
import com.nurudeen.ussd_wallet.security.enums.PinStatus;
import com.nurudeen.ussd_wallet.security.repository.CustomerSecurityRepository;
import com.nurudeen.ussd_wallet.security.repository.PinRecoveryAuthorizationRepository;
import com.nurudeen.ussd_wallet.security.repository.PinSecurityAuditRepository;
import com.nurudeen.ussd_wallet.security.service.PinHashingService;
import com.nurudeen.ussd_wallet.security.service.OtpVerificationService;
import com.nurudeen.ussd_wallet.security.service.PinVerificationService;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class PinVerificationServiceTests {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private final UUID customerId = UUID.randomUUID();
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final CustomerSecurityRepository securityRepository = mock(CustomerSecurityRepository.class);
    private final PinSecurityAuditRepository auditRepository = mock(PinSecurityAuditRepository.class);
    private final PinRecoveryAuthorizationRepository authorizationRepository =
            mock(PinRecoveryAuthorizationRepository.class);
    private final PinHashingService hashingService = mock(PinHashingService.class);
    private final OtpVerificationService otpVerificationService = mock(OtpVerificationService.class);
    private final CustomerSecurity security = createSecurity(customerId);
    private PinVerificationService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenAnswer(ignored -> new SimpleTransactionStatus());
        doNothing().when(transactionManager).commit(any(TransactionStatus.class));
        doNothing().when(transactionManager).rollback(any(TransactionStatus.class));
        when(securityRepository.findByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(security));
        service = new PinVerificationService(
                securityRepository,
                auditRepository,
                authorizationRepository,
                hashingService,
                otpVerificationService,
                new PinSecurityProperties(
                        5, 15, 16, 32, 1, 16384, 2, 5, "test-pin-pepper-that-is-at-least-32-chars"),
                transactionManager,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void locksAfterFiveConsecutiveFailuresAndPersistsThatState() {
        when(hashingService.matches("1111", "encoded")).thenReturn(false);

        for (int attempt = 1; attempt < 5; attempt++) {
            assertThrows(InvalidTransactionPinException.class, () -> service.verify(customerId, "1111"));
        }
        assertThrows(PinVerificationLockedException.class, () -> service.verify(customerId, "1111"));

        assertEquals(5, security.getFailedPinAttempts());
        assertEquals(PinStatus.LOCKED, security.getPinStatus());
        assertEquals(NOW.plusSeconds(900), security.getLockedUntil());
        assertThrows(PinVerificationLockedException.class, () -> service.verify(customerId, "4829"));
        verify(hashingService, never()).matches("4829", "encoded");
        verify(transactionManager, org.mockito.Mockito.times(6)).getTransaction(
                org.mockito.ArgumentMatchers.argThat(
                definition -> definition.getPropagationBehavior()
                        == TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    @Test
    void successfulVerificationResetsFailuresAndRecordsTimestamp() {
        security.recordFailedAttempt(5, NOW.plusSeconds(900));
        when(hashingService.matches("4829", "encoded")).thenReturn(true);

        service.verify(customerId, "4829");

        assertEquals(0, security.getFailedPinAttempts());
        assertNull(security.getLockedUntil());
        assertEquals(NOW, security.getLastSuccessfulVerification());
    }

    @Test
    void validPinWorksAfterLockExpires() {
        for (int attempt = 0; attempt < 5; attempt++) {
            security.recordFailedAttempt(5, NOW.minusSeconds(1));
        }
        when(hashingService.matches("4829", "encoded")).thenReturn(true);

        service.verify(customerId, "4829");

        assertEquals(PinStatus.ACTIVE, security.getPinStatus());
        assertEquals(0, security.getFailedPinAttempts());
    }

    @Test
    void changeVerifiesCurrentPinAndPreventsReusingIt() {
        when(hashingService.matches("4829", "encoded")).thenReturn(true);
        when(hashingService.matches("4829", "new-encoded")).thenReturn(true);

        assertThrows(PinReuseException.class,
                () -> service.verifyAndChange(customerId, "4829", "new-encoded"));
        assertEquals("encoded", security.getPinHash());
    }

    @Test
    void changeReplacesHashAndResetsFailureState() {
        PinRecoveryAuthorization authorization = new PinRecoveryAuthorization(
                customerId, "token-hash", NOW.plusSeconds(60));
        when(authorizationRepository.findUnconsumedByCustomerId(customerId))
                .thenReturn(java.util.List.of(authorization));
        security.recordFailedAttempt(5, NOW.plusSeconds(900));
        when(hashingService.matches("4829", "encoded")).thenReturn(true);
        when(hashingService.matches("4829", "new-encoded")).thenReturn(false);

        service.verifyAndChange(customerId, "4829", "new-encoded");

        assertEquals("new-encoded", security.getPinHash());
        assertEquals(0, security.getFailedPinAttempts());
        assertEquals(PinStatus.ACTIVE, security.getPinStatus());
        assertEquals(NOW, authorization.getConsumedAt());
        verify(otpVerificationService).invalidate(
                customerId, OtpVerificationService.PIN_RECOVERY_PURPOSE);
    }

    private static CustomerSecurity createSecurity(UUID customerId) {
        Customer customer = new Customer("+2348012345678", CustomerStatus.PENDING);
        ReflectionTestUtils.setField(customer, "id", customerId);
        Wallet wallet = new Wallet(customer, "1023456789", "NGN");
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());
        CustomerSecurity security = new CustomerSecurity(customer, wallet);
        ReflectionTestUtils.setField(security, "customerId", customerId);
        security.configurePin("encoded", NOW.minusSeconds(100));
        return security;
    }
}
