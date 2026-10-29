package com.nurudeen.ussd_wallet.wallet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nurudeen.ussd_wallet.common.exception.InsufficientWalletFundsException;
import com.nurudeen.ussd_wallet.common.exception.InvalidTransactionPinException;
import com.nurudeen.ussd_wallet.config.TransferProperties;
import com.nurudeen.ussd_wallet.customer.entity.Customer;
import com.nurudeen.ussd_wallet.customer.enums.CustomerStatus;
import com.nurudeen.ussd_wallet.security.service.PinVerificationService;
import com.nurudeen.ussd_wallet.wallet.dto.WalletTransferRequest;
import com.nurudeen.ussd_wallet.wallet.dto.WalletTransferResponse;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.entity.WalletBalance;
import com.nurudeen.ussd_wallet.wallet.entity.WalletTransfer;
import com.nurudeen.ussd_wallet.wallet.repository.WalletBalanceRepository;
import com.nurudeen.ussd_wallet.wallet.repository.WalletRepository;
import com.nurudeen.ussd_wallet.wallet.repository.WalletTransferEntryRepository;
import com.nurudeen.ussd_wallet.wallet.repository.WalletTransferRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class WalletTransferServiceTests {

    private final UUID customerId = UUID.randomUUID();
    private final WalletRepository walletRepository = org.mockito.Mockito.mock(WalletRepository.class);
    private final WalletBalanceRepository balanceRepository =
            org.mockito.Mockito.mock(WalletBalanceRepository.class);
    private final WalletTransferRepository transferRepository =
            org.mockito.Mockito.mock(WalletTransferRepository.class);
    private final WalletTransferEntryRepository entryRepository =
            org.mockito.Mockito.mock(WalletTransferEntryRepository.class);
    private final PinVerificationService pinVerificationService =
            org.mockito.Mockito.mock(PinVerificationService.class);
    private final EntityManager entityManager = org.mockito.Mockito.mock(EntityManager.class);
    private final Wallet sender = createWallet(customerId, "1012345678");
    private final Wallet recipient = createWallet(UUID.randomUUID(), "1098765432");
    private final WalletBalance senderBalance = createBalance(sender, "100.00");
    private final WalletBalance recipientBalance = createBalance(recipient, "20.00");
    private WalletTransferService service;

    @BeforeEach
    void setUp() {
        service = new WalletTransferService(
                walletRepository,
                balanceRepository,
                transferRepository,
                entryRepository,
                pinVerificationService,
                entityManager,
                new TransferProperties(
                        new BigDecimal("0.01"), new BigDecimal("1000000.00")));
        when(walletRepository.findWalletForTransfer(customerId)).thenReturn(Optional.of(sender));
        when(walletRepository.findByWalletNumber(recipient.getWalletNumber()))
                .thenReturn(Optional.of(recipient));
        when(walletRepository.findAllByIdInForUpdate(anyCollection()))
                .thenReturn(List.of(sender, recipient));
        when(balanceRepository.findAllByWalletIdInForUpdate(anyCollection()))
                .thenReturn(List.of(senderBalance, recipientBalance));
        when(transferRepository.findBySenderWalletIdAndIdempotencyKey(any(), any()))
                .thenReturn(Optional.empty());
        when(transferRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            WalletTransfer transfer = invocation.getArgument(0);
            ReflectionTestUtils.setField(transfer, "createdAt", Instant.parse("2026-10-29T12:00:00Z"));
            return transfer;
        });
    }

    @Test
    void verifiesPinBeforeLockingAndAtomicallyDebitsCreditsAndRecordsTransfer() {
        WalletTransferResponse response = service.transfer(customerId, request("25.00"));

        assertEquals(new BigDecimal("75.00"), senderBalance.getAvailableBalance());
        assertEquals(new BigDecimal("75.00"), senderBalance.getLedgerBalance());
        assertEquals(new BigDecimal("45.00"), recipientBalance.getAvailableBalance());
        assertEquals(new BigDecimal("45.00"), recipientBalance.getLedgerBalance());
        assertTrue(response.reference().startsWith("TXN-"));
        assertEquals(new BigDecimal("25.00"), response.amount());
        verify(entryRepository).saveAll(any());

        InOrder order = inOrder(pinVerificationService, walletRepository, balanceRepository);
        order.verify(pinVerificationService).verify(customerId, "4829");
        order.verify(walletRepository).findAllByIdInForUpdate(anyCollection());
        order.verify(balanceRepository).findAllByWalletIdInForUpdate(anyCollection());
    }

    @Test
    void invalidPinPreventsBalanceLocksAndFinancialWrites() {
        doThrow(new InvalidTransactionPinException())
                .when(pinVerificationService).verify(customerId, "4829");

        assertThrows(InvalidTransactionPinException.class, () -> service.transfer(customerId, request("25.00")));

        verify(walletRepository, never()).findAllByIdInForUpdate(anyCollection());
        verify(balanceRepository, never()).findAllByWalletIdInForUpdate(anyCollection());
        verify(transferRepository, never()).saveAndFlush(any());
        verify(entryRepository, never()).saveAll(any());
        assertEquals(new BigDecimal("100.00"), senderBalance.getAvailableBalance());
        assertEquals(new BigDecimal("20.00"), recipientBalance.getAvailableBalance());
    }

    @Test
    void insufficientFundsPreventTransferAndEntries() {
        assertThrows(
                InsufficientWalletFundsException.class,
                () -> service.transfer(customerId, request("101.00")));

        verify(transferRepository, never()).saveAndFlush(any());
        verify(entryRepository, never()).saveAll(any());
        assertEquals(new BigDecimal("100.00"), senderBalance.getAvailableBalance());
        assertEquals(new BigDecimal("20.00"), recipientBalance.getAvailableBalance());
    }

    private WalletTransferRequest request(String amount) {
        return new WalletTransferRequest(
                recipient.getWalletNumber(),
                new BigDecimal(amount),
                "4829",
                UUID.randomUUID());
    }

    private static Wallet createWallet(UUID ownerId, String walletNumber) {
        Customer customer = new Customer("+2348012345678", CustomerStatus.ACTIVE);
        ReflectionTestUtils.setField(customer, "id", ownerId);
        Wallet wallet = new Wallet(customer, walletNumber, "NGN");
        ReflectionTestUtils.setField(wallet, "id", UUID.randomUUID());
        return wallet;
    }

    private static WalletBalance createBalance(Wallet wallet, String amount) {
        WalletBalance balance = new WalletBalance(wallet);
        ReflectionTestUtils.setField(balance, "walletId", wallet.getId());
        ReflectionTestUtils.setField(balance, "ledgerBalance", new BigDecimal(amount));
        ReflectionTestUtils.setField(balance, "availableBalance", new BigDecimal(amount));
        ReflectionTestUtils.setField(balance, "reservedBalance", BigDecimal.ZERO.setScale(2));
        return balance;
    }
}
