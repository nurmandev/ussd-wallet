package com.nurudeen.ussd_wallet.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nurudeen.ussd_wallet.config.RegistrationProperties;
import com.nurudeen.ussd_wallet.customer.entity.Customer;
import com.nurudeen.ussd_wallet.customer.enums.CustomerStatus;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;

class WalletCreationServiceTests {

    @Test
    void generatedWalletNumberUsesConfiguredPrefixAndRandomDigits() {
        RegistrationProperties properties = new RegistrationProperties(
                CustomerStatus.PENDING,
                new RegistrationProperties.WalletNumber("MG", 12, 3),
                new RegistrationProperties.RateLimit(10, 60));
        WalletNumberGenerationService generator = new WalletNumberGenerationService(properties);

        String walletNumber = generator.generate();

        assertEquals(14, walletNumber.length());
        assertEquals("MG", walletNumber.substring(0, 2));
        org.junit.jupiter.api.Assertions.assertTrue(walletNumber.substring(2).matches("\\d{12}"));
    }

    @Test
    void retriesCandidateWhenWalletNumberAlreadyExists() {
        WalletNumberGenerationService generator = mock(WalletNumberGenerationService.class);
        WalletRepository repository = mock(WalletRepository.class);
        RegistrationProperties properties = new RegistrationProperties(
                CustomerStatus.PENDING,
                new RegistrationProperties.WalletNumber("MG", 12, 3),
                new RegistrationProperties.RateLimit(10, 60));
        WalletCreationService service = new WalletCreationService(generator, repository, properties);
        Customer customer = new Customer("+2348012345678", CustomerStatus.PENDING);
        Wallet storedWallet = mock(Wallet.class);

        when(generator.generate())
                .thenReturn("MG123456789012")
                .thenReturn("MG987654321098");
        when(repository.existsByWalletNumber("MG123456789012")).thenReturn(true);
        when(repository.existsByWalletNumber("MG987654321098")).thenReturn(false);
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any(Wallet.class)))
                .thenReturn(storedWallet);

        assertEquals(storedWallet, service.createWallet(customer));
        verify(generator, times(2)).generate();
        verify(repository, times(2)).existsByWalletNumber(org.mockito.ArgumentMatchers.anyString());
    }
}
