package com.nurudeen.ussd_wallet.wallet.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nurudeen.ussd_wallet.common.exception.CustomerIdentityUnavailableException;
import com.nurudeen.ussd_wallet.security.principal.CustomerPrincipal;
import com.nurudeen.ussd_wallet.wallet.dto.WalletBalanceResponse;
import com.nurudeen.ussd_wallet.wallet.enums.WalletStatus;
import com.nurudeen.ussd_wallet.wallet.service.WalletBalanceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

class WalletBalanceControllerTests {

    private final WalletBalanceService balanceService = mock(WalletBalanceService.class);
    private final WalletBalanceController controller = new WalletBalanceController(balanceService);
    private final UUID customerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(balanceService.getCustomerBalance(
                        customerId, WalletBalanceService.Channel.REST, null, "request-1"))
                .thenReturn(new WalletBalanceResponse(
                        "1023456789",
                        "NGN",
                        new BigDecimal("45500.00"),
                        new BigDecimal("40500.00"),
                        new BigDecimal("5000.00"),
                        WalletStatus.ACTIVE,
                        Instant.parse("2026-10-30T12:00:00Z")));
    }

    @Test
    void usesAuthenticatedCustomerPrincipalAndReturnsExistingApiEnvelope() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                new CustomerPrincipal(customerId), "unused", "ROLE_CUSTOMER");

        var response = controller.getBalance(authentication, "request-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, response.getBody().success());
        assertEquals("1023456789", response.getBody().data().walletNumber());
        verify(balanceService).getCustomerBalance(
                customerId, WalletBalanceService.Channel.REST, null, "request-1");
    }

    @Test
    void rejectsNonCustomerPrincipals() {
        TestingAuthenticationToken authentication =
                new TestingAuthenticationToken("arbitrary-user", "unused");

        assertThrows(
                CustomerIdentityUnavailableException.class,
                () -> controller.getBalance(authentication, null));
    }
}
