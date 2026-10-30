package com.nurudeen.ussd_wallet.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nurudeen.ussd_wallet.common.exception.WalletNotFoundException;
import com.nurudeen.ussd_wallet.customer.entity.Customer;
import com.nurudeen.ussd_wallet.customer.enums.CustomerStatus;
import com.nurudeen.ussd_wallet.wallet.dto.WalletBalanceResponse;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.entity.WalletBalance;
import com.nurudeen.ussd_wallet.wallet.enums.WalletStatus;
import com.nurudeen.ussd_wallet.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WalletBalanceServiceTests {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    private final UUID customerId = UUID.randomUUID();
    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final WalletBalanceRateLimitService rateLimitService =
            mock(WalletBalanceRateLimitService.class);
    private final WalletBalanceAuditService auditService = mock(WalletBalanceAuditService.class);
    private final Customer customer = new Customer("+2348012345678", CustomerStatus.ACTIVE);
    private final Wallet wallet = new Wallet(customer, "1023456789", "NGN");
    private final WalletBalance balance = new WalletBalance(wallet);
    private WalletBalanceService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(customer, "id", customerId);
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(balance, "walletId", wallet.getId());
        ReflectionTestUtils.setField(balance, "ledgerBalance", new BigDecimal("45500.00"));
        ReflectionTestUtils.setField(balance, "availableBalance", new BigDecimal("40500.00"));
        ReflectionTestUtils.setField(balance, "reservedBalance", new BigDecimal("5000.00"));
        ReflectionTestUtils.setField(balance, "updatedAt", NOW);
        ReflectionTestUtils.setField(wallet, "balance", balance);
        when(walletRepository.findByCustomer_Id(customerId)).thenReturn(Optional.of(wallet));
        when(rateLimitService.allow(any(), any())).thenReturn(true);
        service = new WalletBalanceService(
                walletRepository,
                rateLimitService,
                auditService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void returnsAuthoritativeProjectionAndMapsReservedFundsToPendingWithoutSubtractingTwice() {
        WalletBalanceResponse response = service.getCustomerBalance(
                customerId, WalletBalanceService.Channel.REST, null, "request-1");

        assertEquals("1023456789", response.walletNumber());
        assertEquals("NGN", response.currency());
        assertEquals(new BigDecimal("45500.00"), response.ledgerBalance());
        assertEquals(new BigDecimal("40500.00"), response.availableBalance());
        assertEquals(new BigDecimal("5000.00"), response.pendingAmount());
        assertEquals(WalletStatus.ACTIVE, response.walletStatus());
        assertEquals(NOW, response.lastUpdated());
        verify(auditService).record(
                customerId, wallet.getId(), null, WalletBalanceService.Channel.REST, "request-1", "SUCCESS");
    }

    @Test
    void rejectsUnavailableWalletAndAuditsTheOutcome() {
        ReflectionTestUtils.setField(wallet, "status", WalletStatus.FROZEN);

        assertThrows(
                WalletUnavailableException.class,
                () -> service.getCustomerBalance(
                        customerId, WalletBalanceService.Channel.USSD, "session-1", "callback-1"));

        verify(auditService).record(
                customerId,
                wallet.getId(),
                "session-1",
                WalletBalanceService.Channel.USSD,
                "callback-1",
                "WALLET_UNAVAILABLE");
    }

    @Test
    void rejectsMissingWalletAndAuditsTheOutcome() {
        when(walletRepository.findByCustomer_Id(customerId)).thenReturn(Optional.empty());

        assertThrows(
                WalletNotFoundException.class,
                () -> service.getCustomerBalance(
                        customerId, WalletBalanceService.Channel.REST, null, "request-2"));

        verify(auditService).record(
                customerId, null, null, WalletBalanceService.Channel.REST, "request-2", "WALLET_NOT_FOUND");
    }

    @Test
    void rateLimitsByCustomerAcrossEnquiryChannels() {
        when(rateLimitService.allow(customerId, NOW)).thenReturn(false);

        assertThrows(
                WalletBalanceRateLimitExceededException.class,
                () -> service.getCustomerBalance(
                        customerId, WalletBalanceService.Channel.REST, null, "request-3"));

        verify(auditService).record(
                customerId, null, null, WalletBalanceService.Channel.REST, "request-3", "RATE_LIMITED");
    }
}
