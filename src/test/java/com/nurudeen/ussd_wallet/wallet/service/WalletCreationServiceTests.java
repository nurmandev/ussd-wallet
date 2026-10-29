package com.nurudeen.ussd_wallet.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nurudeen.ussd_wallet.config.WalletProperties;
import com.nurudeen.ussd_wallet.customer.entity.Customer;
import com.nurudeen.ussd_wallet.customer.enums.CustomerStatus;
import com.nurudeen.ussd_wallet.common.exception.WalletAlreadyExistsException;
import com.nurudeen.ussd_wallet.common.exception.WalletNumberCollisionException;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.repository.WalletRepository;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class WalletCreationServiceTests {

    @Test
    void generatedWalletNumberUsesConfiguredPrefixAndRandomDigits() {
        WalletProperties properties = new WalletProperties(
                new WalletProperties.Number("10", 10, 3),
                "NGN");
        WalletNumberGeneratorService generator = new WalletNumberGeneratorService(properties);

        String walletNumber = generator.generate();

        assertEquals(10, walletNumber.length());
        assertTrue(walletNumber.matches("10\\d{8}"));
    }

    @Test
    void generatorUsesSecureRandomDigitsInConfiguredLength() {
        SecureRandom deterministicSecureRandom = new SecureRandom() {
            private int next;

            @Override
            public int nextInt(int bound) {
                return (next++) % bound;
            }
        };
        WalletProperties properties = new WalletProperties(
                new WalletProperties.Number("10", 10, 3),
                "NGN");
        WalletNumberGeneratorService generator =
                new WalletNumberGeneratorService(properties, deterministicSecureRandom);

        assertEquals("1001234567", generator.generate());
    }

    @Test
    void rejectsCandidateWhenWalletNumberAlreadyExists() {
        WalletNumberGeneratorService generator = mock(WalletNumberGeneratorService.class);
        WalletRepository repository = mock(WalletRepository.class);
        WalletProperties properties = new WalletProperties(
                new WalletProperties.Number("10", 10, 3),
                "NGN");
        WalletCreationService service = new WalletCreationService(generator, repository, properties);
        Customer customer = new Customer("+2348012345678", CustomerStatus.PENDING);

        when(generator.generate()).thenReturn("1023456789");
        when(repository.existsByWalletNumber("1023456789")).thenReturn(true);

        assertThrows(WalletNumberCollisionException.class, () -> service.createWallet(customer));
        verify(generator, times(1)).generate();
        verify(repository, times(1)).existsByWalletNumber("1023456789");
        verify(repository, org.mockito.Mockito.never())
                .saveAndFlush(org.mockito.ArgumentMatchers.any(Wallet.class));
    }

    @Test
    void createsWalletWithGeneratedNumberAndConfiguredCurrency() {
        WalletNumberGeneratorService generator = mock(WalletNumberGeneratorService.class);
        WalletRepository repository = mock(WalletRepository.class);
        WalletCreationService service = new WalletCreationService(
                generator, repository, new WalletProperties(
                        new WalletProperties.Number("10", 10, 3), "NGN"));
        Customer customer = new Customer("+2348012345678", CustomerStatus.PENDING);
        Wallet savedWallet = mock(Wallet.class);

        when(generator.generate()).thenReturn("1023456789");
        when(repository.existsByWalletNumber("1023456789")).thenReturn(false);
        when(repository.saveAndFlush(argThat(wallet ->
                wallet.getWalletNumber().equals("1023456789")
                        && wallet.getCurrency().equals("NGN"))))
                .thenReturn(savedWallet);

        assertEquals(savedWallet, service.createWallet(customer));
        verify(repository).existsByCustomer_Id(customer.getId());
        verify(repository).existsByWalletNumber("1023456789");
        verify(repository).saveAndFlush(any(Wallet.class));
    }

    @Test
    void doesNotCreateSecondWalletForCustomer() {
        WalletNumberGeneratorService generator = mock(WalletNumberGeneratorService.class);
        WalletRepository repository = mock(WalletRepository.class);
        WalletCreationService service = new WalletCreationService(
                generator, repository, new WalletProperties(
                        new WalletProperties.Number("10", 10, 3), "NGN"));
        Customer customer = new Customer("+2348012345678", CustomerStatus.PENDING);
        org.springframework.test.util.ReflectionTestUtils.setField(
                customer, "id", java.util.UUID.randomUUID());
        when(repository.existsByCustomer_Id(customer.getId())).thenReturn(true);

        assertThrows(WalletAlreadyExistsException.class, () -> service.createWallet(customer));
        verify(generator, org.mockito.Mockito.never()).generate();
        verify(repository, org.mockito.Mockito.never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }
}
