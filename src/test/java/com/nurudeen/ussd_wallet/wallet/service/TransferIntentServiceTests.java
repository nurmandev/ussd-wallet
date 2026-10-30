package com.nurudeen.ussd_wallet.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nurudeen.ussd_wallet.common.exception.WalletTransferConflictException;
import com.nurudeen.ussd_wallet.config.TransferProperties;
import com.nurudeen.ussd_wallet.customer.entity.Customer;
import com.nurudeen.ussd_wallet.customer.enums.CustomerStatus;
import com.nurudeen.ussd_wallet.security.entity.CustomerSecurity;
import com.nurudeen.ussd_wallet.security.repository.CustomerSecurityRepository;
import com.nurudeen.ussd_wallet.wallet.dto.TransferConfirmationRequest;
import com.nurudeen.ussd_wallet.wallet.dto.TransferValidationRequest;
import com.nurudeen.ussd_wallet.wallet.dto.WalletTransferResponse;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.entity.WalletBalance;
import com.nurudeen.ussd_wallet.wallet.entity.WalletTransferIntent;
import com.nurudeen.ussd_wallet.wallet.enums.TransferIntentStatus;
import com.nurudeen.ussd_wallet.wallet.repository.WalletRepository;
import com.nurudeen.ussd_wallet.wallet.repository.WalletTransferIntentRepository;
import com.nurudeen.ussd_wallet.wallet.repository.WalletTransferRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TransferIntentServiceTests {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString("90e6bf9e-4e5e-47a8-88e8-c5e53c7a6ebc");

    private final UUID customerId = UUID.randomUUID();
    private final Customer senderCustomer = customer("+2348011111111", customerId, "Amina", "Ali");
    private final Customer recipientCustomer = customer("+2348022222222", UUID.randomUUID(), "John", "Doe");
    private final Wallet sender = wallet(senderCustomer, "1011111111");
    private final Wallet recipient = wallet(recipientCustomer, "1022222222");
    private final WalletBalance senderBalance = balance(sender);
    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final WalletTransferRepository transferRepository = mock(WalletTransferRepository.class);
    private final WalletTransferIntentRepository intentRepository =
            mock(WalletTransferIntentRepository.class);
    private final CustomerSecurityRepository securityRepository = mock(CustomerSecurityRepository.class);
    private final TransferLimitService limitService = mock(TransferLimitService.class);
    private final WalletTransferService transferService = mock(WalletTransferService.class);
    private final CustomerSecurity security = new CustomerSecurity(senderCustomer, sender);
    private TransferIntentService service;

    @BeforeEach
    void setUp() {
        security.configurePin("encoded-pin", NOW.minusSeconds(30));
        service = new TransferIntentService(
                walletRepository,
                transferRepository,
                intentRepository,
                securityRepository,
                new TransferProperties(new BigDecimal("0.01"), new BigDecimal("1000000.00")),
                limitService,
                new TransferLimitProperties(new BigDecimal("2000000.00"), 20, 300, BigDecimal.ZERO),
                transferService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(walletRepository.findByCustomer_Id(customerId)).thenReturn(Optional.of(sender));
        when(walletRepository.findByWalletNumber(recipient.getWalletNumber())).thenReturn(Optional.of(recipient));
        when(walletRepository.findAllByIdInForUpdate(anyCollection())).thenReturn(List.of(sender));
        when(walletRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(securityRepository.findById(customerId)).thenReturn(Optional.of(security));
        when(intentRepository.findByCustomerIdAndIdempotencyKey(customerId, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());
        when(intentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(limitService.configuredFee()).thenReturn(BigDecimal.ZERO.setScale(2));
        when(limitService.intentExpirySeconds()).thenReturn(300);
    }

    @Test
    void validatesTransferAndCreatesImmutableExpiringIntentWithoutVerifyingPin() {
        var response = service.createIntent(
                customerId,
                new TransferValidationRequest(
                        recipient.getWalletNumber(),
                        new BigDecimal("5000.00"),
                        "Personal transfer",
                        IDEMPOTENCY_KEY));

        assertEquals(recipient.getWalletNumber(), response.recipientWalletNumber());
        assertEquals("J*** D***", response.recipientName());
        assertEquals(new BigDecimal("5000.00"), response.amount());
        assertEquals(BigDecimal.ZERO.setScale(2), response.fee());
        assertEquals(new BigDecimal("5000.00"), response.totalDebit());
        assertEquals(TransferIntentStatus.PENDING_CONFIRMATION, response.status());
        assertEquals(NOW.plusSeconds(300), response.expiresAt());
        assertEquals(new BigDecimal("20000.00"), senderBalance.getAvailableBalance());
        verify(limitService).validateDailyLimit(sender.getId(), new BigDecimal("5000.00"), NOW);
    }

    @Test
    void rejectsReuseOfIdempotencyKeyWithDifferentDetails() {
        WalletTransferIntent prior = new WalletTransferIntent(
                UUID.randomUUID(),
                customerId,
                sender.getId(),
                recipient.getId(),
                IDEMPOTENCY_KEY,
                new BigDecimal("5000.00"),
                BigDecimal.ZERO.setScale(2),
                "NGN",
                "Different description",
                NOW,
                NOW.plusSeconds(300));
        when(intentRepository.findByCustomerIdAndIdempotencyKey(customerId, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(prior));

        assertThrows(
                WalletTransferConflictException.class,
                () -> service.createIntent(
                        customerId,
                        new TransferValidationRequest(
                                recipient.getWalletNumber(),
                                new BigDecimal("5000.00"),
                                "Changed description",
                                IDEMPOTENCY_KEY)));

        verify(intentRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmsOwnedIntentThroughCentralizedTransferServiceAndCompletesOnlyAfterSuccess() {
        WalletTransferIntent intent = new WalletTransferIntent(
                UUID.randomUUID(),
                customerId,
                sender.getId(),
                recipient.getId(),
                IDEMPOTENCY_KEY,
                new BigDecimal("5000.00"),
                BigDecimal.ZERO.setScale(2),
                "NGN",
                "Personal transfer",
                NOW,
                NOW.plusSeconds(300));
        when(intentRepository.findOwnedByIdForUpdate(intent.getId(), customerId))
                .thenReturn(Optional.of(intent));
        when(transferService.transfer(
                        eq(customerId),
                        any(),
                        eq(BigDecimal.ZERO.setScale(2)),
                        eq("Personal transfer"),
                        eq(WalletTransferService.TransferChannel.REST),
                        eq("request-1")))
                .thenReturn(new WalletTransferResponse(
                        "TXN-test-reference",
                        recipient.getWalletNumber(),
                        new BigDecimal("5000.00"),
                        "NGN",
                        NOW));

        var response = service.confirm(
                customerId,
                new TransferConfirmationRequest(intent.getId(), "4829", IDEMPOTENCY_KEY),
                WalletTransferService.TransferChannel.REST,
                "request-1");

        assertEquals("TXN-test-reference", response.reference());
        assertEquals("SUCCESSFUL", response.status());
        assertEquals(TransferIntentStatus.COMPLETED, intent.getStatus());
        assertEquals("TXN-test-reference", intent.getTransferReference());
        verify(transferService).transfer(
                eq(customerId),
                any(),
                eq(BigDecimal.ZERO.setScale(2)),
                eq("Personal transfer"),
                eq(WalletTransferService.TransferChannel.REST),
                eq("request-1"));
    }

    @Test
    void rejectsConfirmationKeyThatDoesNotMatchIntent() {
        WalletTransferIntent intent = new WalletTransferIntent(
                UUID.randomUUID(),
                customerId,
                sender.getId(),
                recipient.getId(),
                IDEMPOTENCY_KEY,
                new BigDecimal("5000.00"),
                BigDecimal.ZERO.setScale(2),
                "NGN",
                null,
                NOW,
                NOW.plusSeconds(300));
        when(intentRepository.findOwnedByIdForUpdate(intent.getId(), customerId))
                .thenReturn(Optional.of(intent));

        assertThrows(
                WalletTransferConflictException.class,
                () -> service.confirm(
                        customerId,
                        new TransferConfirmationRequest(
                                intent.getId(), "4829", UUID.randomUUID()),
                        WalletTransferService.TransferChannel.REST,
                        "request-2"));

        verify(transferService, never()).transfer(any(), any(), any(), any(), any(), any());
    }

    private static Customer customer(String phone, UUID id, String firstName, String lastName) {
        Customer customer = new Customer(phone, CustomerStatus.ACTIVE);
        ReflectionTestUtils.setField(customer, "id", id);
        ReflectionTestUtils.setField(customer, "firstName", firstName);
        ReflectionTestUtils.setField(customer, "lastName", lastName);
        return customer;
    }

    private static Wallet wallet(Customer customer, String number) {
        Wallet wallet = new Wallet(customer, number, "NGN");
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());
        return wallet;
    }

    private static WalletBalance balance(Wallet wallet) {
        WalletBalance balance = new WalletBalance(wallet);
        ReflectionTestUtils.setField(balance, "walletId", wallet.getId());
        ReflectionTestUtils.setField(balance, "ledgerBalance", new BigDecimal("20000.00"));
        ReflectionTestUtils.setField(balance, "availableBalance", new BigDecimal("20000.00"));
        ReflectionTestUtils.setField(balance, "reservedBalance", BigDecimal.ZERO.setScale(2));
        ReflectionTestUtils.setField(wallet, "balance", balance);
        return balance;
    }
}
