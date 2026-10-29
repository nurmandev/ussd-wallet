package com.nurudeen.ussd_wallet.customer.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nurudeen.ussd_wallet.common.exception.ResourceCreationException;
import com.nurudeen.ussd_wallet.common.exception.WalletNumberCollisionException;
import com.nurudeen.ussd_wallet.config.RegistrationProperties;
import com.nurudeen.ussd_wallet.config.WalletProperties;
import com.nurudeen.ussd_wallet.customer.entity.Customer;
import com.nurudeen.ussd_wallet.customer.enums.CustomerStatus;
import com.nurudeen.ussd_wallet.customer.repository.CustomerRepository;
import com.nurudeen.ussd_wallet.ledger.service.LedgerAccountCreationService;
import com.nurudeen.ussd_wallet.security.repository.CustomerSecurityRepository;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.entity.WalletBalance;
import com.nurudeen.ussd_wallet.wallet.repository.WalletBalanceRepository;
import com.nurudeen.ussd_wallet.wallet.service.WalletCreationService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class CustomerRegistrationWalletRetryTests {

    @Test
    void retriesEntireRegistrationTransactionAfterWalletNumberCollision() {
        Fixture fixture = new Fixture(2);
        Wallet wallet = wallet();
        WalletBalance balance = balance();
        when(fixture.walletCreationService.createWallet(any(Customer.class)))
                .thenThrow(new WalletNumberCollisionException())
                .thenReturn(wallet);
        when(fixture.walletBalanceRepository.saveAndFlush(any(WalletBalance.class)))
                .thenReturn(balance);
        when(fixture.customerRepository.saveAndFlush(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertNotNull(fixture.service.register("08012345678").wallet());
        verify(fixture.transactionManager, times(1)).rollback(any(TransactionStatus.class));
        verify(fixture.transactionManager, times(1)).commit(any(TransactionStatus.class));
        verify(fixture.walletCreationService, times(2)).createWallet(any(Customer.class));
    }

    @Test
    void stopsAfterConfiguredWalletNumberRetryLimit() {
        Fixture fixture = new Fixture(2);
        when(fixture.walletCreationService.createWallet(any(Customer.class)))
                .thenThrow(new WalletNumberCollisionException());
        when(fixture.customerRepository.saveAndFlush(any(Customer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(ResourceCreationException.class,
                () -> fixture.service.register("08012345678"));
        verify(fixture.transactionManager, times(2)).rollback(any(TransactionStatus.class));
        verify(fixture.transactionManager, org.mockito.Mockito.never())
                .commit(any(TransactionStatus.class));
        verify(fixture.walletCreationService, times(2)).createWallet(any(Customer.class));
    }

    private static Wallet wallet() {
        Wallet wallet = mock(Wallet.class);
        when(wallet.getWalletNumber()).thenReturn("1023456789");
        when(wallet.getCurrency()).thenReturn("NGN");
        when(wallet.getStatus()).thenReturn(com.nurudeen.ussd_wallet.wallet.enums.WalletStatus.ACTIVE);
        when(wallet.getId()).thenReturn(java.util.UUID.randomUUID());
        return wallet;
    }

    private static WalletBalance balance() {
        WalletBalance balance = mock(WalletBalance.class);
        when(balance.getLedgerBalance()).thenReturn(new BigDecimal("0.00"));
        when(balance.getAvailableBalance()).thenReturn(new BigDecimal("0.00"));
        when(balance.getReservedBalance()).thenReturn(new BigDecimal("0.00"));
        return balance;
    }

    private static final class Fixture {

        private final PlatformTransactionManager transactionManager =
                mock(PlatformTransactionManager.class);
        private final CustomerRepository customerRepository = mock(CustomerRepository.class);
        private final WalletCreationService walletCreationService = mock(WalletCreationService.class);
        private final WalletBalanceRepository walletBalanceRepository =
                mock(WalletBalanceRepository.class);
        private final CustomerRegistrationService service;

        private Fixture(int maxRetries) {
            CustomerSecurityRepository securityRepository = mock(CustomerSecurityRepository.class);
            LedgerAccountCreationService ledgerService = mock(LedgerAccountCreationService.class);
            RegistrationProperties registrationProperties = new RegistrationProperties(
                    CustomerStatus.PENDING,
                    new RegistrationProperties.RateLimit(10, 60));
            WalletProperties walletProperties = new WalletProperties(
                    new WalletProperties.Number("10", 10, maxRetries),
                    "NGN");

            when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                    .thenAnswer(ignored -> new SimpleTransactionStatus());
            doNothing().when(transactionManager).commit(any(TransactionStatus.class));
            doNothing().when(transactionManager).rollback(any(TransactionStatus.class));
            when(customerRepository.existsByPhoneNumber("+2348012345678")).thenReturn(false);

            service = new CustomerRegistrationService(
                    new NigerianPhoneNumberNormalizer(),
                    customerRepository,
                    securityRepository,
                    walletBalanceRepository,
                    ledgerService,
                    walletCreationService,
                    registrationProperties,
                    walletProperties,
                    transactionManager);
        }
    }
}
