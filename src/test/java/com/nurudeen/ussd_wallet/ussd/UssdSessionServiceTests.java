package com.nurudeen.ussd_wallet.ussd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nurudeen.ussd_wallet.customer.entity.Customer;
import com.nurudeen.ussd_wallet.customer.enums.CustomerStatus;
import com.nurudeen.ussd_wallet.customer.repository.CustomerRepository;
import com.nurudeen.ussd_wallet.customer.service.PhoneNumberNormalizer;
import com.nurudeen.ussd_wallet.security.entity.CustomerSecurity;
import com.nurudeen.ussd_wallet.security.repository.CustomerSecurityRepository;
import com.nurudeen.ussd_wallet.security.service.TransactionPinManagementService;
import com.nurudeen.ussd_wallet.ussd.config.UssdProperties;
import com.nurudeen.ussd_wallet.ussd.dto.UssdGatewayRequest;
import com.nurudeen.ussd_wallet.ussd.dto.UssdResponse;
import com.nurudeen.ussd_wallet.ussd.service.UssdGatewayRateLimitService;
import com.nurudeen.ussd_wallet.ussd.service.UssdInputValidator;
import com.nurudeen.ussd_wallet.ussd.service.UssdSessionService;
import com.nurudeen.ussd_wallet.ussd.session.UssdGatewayCallbackRepository;
import com.nurudeen.ussd_wallet.ussd.session.UssdSession;
import com.nurudeen.ussd_wallet.ussd.session.UssdSessionEventRepository;
import com.nurudeen.ussd_wallet.ussd.session.UssdSessionRepository;
import com.nurudeen.ussd_wallet.ussd.session.UssdSessionStatus;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.dto.WalletBalanceResponse;
import com.nurudeen.ussd_wallet.wallet.dto.TransferConfirmationResponse;
import com.nurudeen.ussd_wallet.wallet.dto.TransferIntentResponse;
import com.nurudeen.ussd_wallet.wallet.dto.RecipientWalletResponse;
import com.nurudeen.ussd_wallet.wallet.repository.WalletRepository;
import com.nurudeen.ussd_wallet.wallet.repository.WalletTransferEntryRepository;
import com.nurudeen.ussd_wallet.wallet.service.WalletBalanceService;
import com.nurudeen.ussd_wallet.wallet.service.TransferIntentService;
import com.nurudeen.ussd_wallet.wallet.service.RecipientWalletLookupService;
import com.nurudeen.ussd_wallet.wallet.enums.TransferIntentStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UssdSessionServiceTests {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");
    private final UUID customerId = UUID.randomUUID();
    private final String phoneNumber = "+2348012345678";
    private final UssdSessionRepository sessionRepository = mock(UssdSessionRepository.class);
    private final UssdGatewayCallbackRepository callbackRepository =
            mock(UssdGatewayCallbackRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final CustomerSecurityRepository securityRepository = mock(CustomerSecurityRepository.class);
    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final WalletBalanceService walletBalanceService = mock(WalletBalanceService.class);
    private final RecipientWalletLookupService recipientWalletLookupService =
            mock(RecipientWalletLookupService.class);
    private final TransferIntentService transferIntentService = mock(TransferIntentService.class);
    private final PhoneNumberNormalizer phoneNumberNormalizer = mock(PhoneNumberNormalizer.class);
    private final Customer customer = customer();
    private final Wallet wallet = wallet();
    private final Wallet recipientWallet = recipientWallet();
    private final CustomerSecurity security = configuredSecurity();
    private final UssdSession session = new UssdSession(
            "session-1",
            phoneNumber,
            "*123#",
            "MTN",
            "main",
            "START",
            UssdSessionStatus.ACTIVE,
            NOW,
            NOW.plusSeconds(120));
    private UssdSessionService service;
    private final AtomicInteger sessionLookupCount = new AtomicInteger();

    @BeforeEach
    void setUp() {
        when(sessionRepository.findBySessionIdForUpdate("session-1"))
                .thenAnswer(ignored -> sessionLookupCount.getAndIncrement() == 0
                        ? Optional.empty()
                        : Optional.of(session));
        when(sessionRepository.createIfAbsent(
                anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(1);
        when(callbackRepository.findByIdSessionIdAndIdCallbackId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(customerRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(customer));
        when(securityRepository.findById(customerId)).thenReturn(Optional.of(security));
        when(walletRepository.findByWalletNumber(anyString())).thenReturn(Optional.of(recipientWallet));
        when(recipientWalletLookupService.lookup(any(), anyString())).thenReturn(
                new RecipientWalletResponse(
                        recipientWallet.getWalletNumber(),
                        "J*** D***",
                        recipientWallet.getStatus(),
                        recipientWallet.getCurrency()));
        when(phoneNumberNormalizer.normalize(anyString())).thenReturn(phoneNumber);
        when(walletBalanceService.getCustomerBalance(
                        customerId,
                        WalletBalanceService.Channel.USSD,
                        "session-1",
                        "cb-balance"))
                .thenReturn(new WalletBalanceResponse(
                        wallet.getWalletNumber(),
                        "NGN",
                        new java.math.BigDecimal("45500.00"),
                        new java.math.BigDecimal("40500.00"),
                        new java.math.BigDecimal("5000.00"),
                        com.nurudeen.ussd_wallet.wallet.enums.WalletStatus.ACTIVE,
                        NOW));

        service = new UssdSessionService(
                sessionRepository,
                callbackRepository,
                mock(UssdSessionEventRepository.class),
                customerRepository,
                securityRepository,
                walletRepository,
                walletBalanceService,
                recipientWalletLookupService,
                transferIntentService,
                mock(WalletTransferEntryRepository.class),
                mock(TransactionPinManagementService.class),
                phoneNumberNormalizer,
                new UssdInputValidator(new com.nurudeen.ussd_wallet.config.TransferProperties(
                        new java.math.BigDecimal("0.01"), new java.math.BigDecimal("1000000.00"))),
                new UssdGatewayRateLimitService(
                        new UssdProperties("*123#", 120, 604800, 30, "test-secret-at-least-32-characters-long", null, false)),
                new UssdProperties("*123#", 120, 604800, 30, "test-secret-at-least-32-characters-long", null, false),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void displaysDynamicMainMenuThenAdvancesToRecipientEntry() {
        UssdResponse welcome = service.process(request("", "cb-start"));
        assertEquals(UssdResponse.Type.CONTINUE, welcome.type());
        assertEquals("MAIN", session.getCurrentStep());
        assertEquals(UssdSessionStatus.WAITING_FOR_INPUT, session.getStatus());

        UssdResponse transfer = service.process(request("2", "cb-menu"));
        assertEquals("Enter recipient wallet number:", transfer.message());
        assertEquals("TRANSFER_RECIPIENT", session.getCurrentStep());
        assertEquals("MTN", session.getNetworkCode());
    }

    @Test
    void balanceMenuShowsLatestAmountsAndDoesNotRequirePinVerification() {
        when(securityRepository.findById(customerId)).thenReturn(Optional.empty());

        service.process(request("", "cb-start"));
        UssdResponse response = service.process(request("1", "cb-balance"));

        assertEquals(UssdResponse.Type.CONTINUE, response.type());
        org.junit.jupiter.api.Assertions.assertTrue(response.message().contains("Avail NGN 40,500.00"));
        org.junit.jupiter.api.Assertions.assertTrue(response.message().contains("Ledger NGN 45,500.00"));
        org.junit.jupiter.api.Assertions.assertTrue(response.message().contains("Pending NGN 5,000.00"));
        assertEquals("BALANCE_MENU", session.getCurrentStep());
        org.mockito.Mockito.verify(securityRepository, org.mockito.Mockito.never())
                .findById(customerId);
        org.mockito.Mockito.verify(walletBalanceService).getCustomerBalance(
                customerId, WalletBalanceService.Channel.USSD, "session-1", "cb-balance");
    }

    @Test
    void transferFlowConfirmsRecipientReviewsIntentAndExecutesWithPinWithoutPersistingPin() {
        UUID intentId = UUID.randomUUID();
        when(transferIntentService.createIntent(
                        org.mockito.ArgumentMatchers.eq(customerId),
                        any(com.nurudeen.ussd_wallet.wallet.dto.TransferValidationRequest.class)))
                .thenReturn(new TransferIntentResponse(
                        intentId,
                        "R*** C***",
                        recipientWallet.getWalletNumber(),
                        new java.math.BigDecimal("25.00"),
                        new java.math.BigDecimal("0.00"),
                        new java.math.BigDecimal("25.00"),
                        "NGN",
                        TransferIntentStatus.PENDING_CONFIRMATION,
                        NOW.plusSeconds(300)));
        when(transferIntentService.confirm(
                        org.mockito.ArgumentMatchers.eq(customerId),
                        any(com.nurudeen.ussd_wallet.wallet.dto.TransferConfirmationRequest.class),
                        org.mockito.ArgumentMatchers.eq(
                                com.nurudeen.ussd_wallet.wallet.service.WalletTransferService.TransferChannel.USSD),
                        org.mockito.ArgumentMatchers.eq("cb-pin")))
                .thenReturn(new TransferConfirmationResponse(
                        "SUCCESSFUL",
                        "TXN-test",
                        recipientWallet.getWalletNumber(),
                        new java.math.BigDecimal("25.00"),
                        new java.math.BigDecimal("0.00"),
                        new java.math.BigDecimal("25.00"),
                        "NGN",
                        NOW));

        service.process(request("", "cb-start-transfer"));
        service.process(request("2", "cb-transfer-menu"));
        UUID idempotencyKey = UUID.fromString(session.getSessionData().get("idempotencyKey"));
        UssdResponse recipientPrompt =
                service.process(request(recipientWallet.getWalletNumber(), "cb-recipient"));
        assertEquals("TRANSFER_RECIPIENT_CONFIRM", session.getCurrentStep());
        org.junit.jupiter.api.Assertions.assertTrue(recipientPrompt.message().contains("Confirm recipient"));

        service.process(request("1", "cb-recipient-confirm"));
        UssdResponse review = service.process(request("25.00", "cb-amount"));
        assertEquals("TRANSFER_CONFIRM", session.getCurrentStep());
        org.junit.jupiter.api.Assertions.assertTrue(review.message().contains("Fee 0.00"));
        service.process(request("1", "cb-review"));
        assertEquals("TRANSFER_PIN", session.getCurrentStep());

        UssdResponse result = service.process(request("4829", "cb-pin"));

        assertEquals(UssdResponse.Type.END, result.type());
        org.junit.jupiter.api.Assertions.assertTrue(result.message().contains("TXN-test"));
        org.junit.jupiter.api.Assertions.assertFalse(session.getSessionData().containsValue("4829"));
        org.mockito.Mockito.verify(transferIntentService).confirm(
                org.mockito.ArgumentMatchers.eq(customerId),
                org.mockito.ArgumentMatchers.argThat(confirm ->
                        confirm.transferIntentId().equals(intentId)
                                && confirm.idempotencyKey().equals(idempotencyKey)
                                && confirm.transactionPin().equals("4829")),
                org.mockito.ArgumentMatchers.eq(
                        com.nurudeen.ussd_wallet.wallet.service.WalletTransferService.TransferChannel.USSD),
                org.mockito.ArgumentMatchers.eq("cb-pin"));
    }

    private UssdGatewayRequest request(String text, String callbackId) {
        return new UssdGatewayRequest(
                "session-1", "*123#", "08012345678", text, "MTN", callbackId);
    }

    private Customer customer() {
        Customer entity = new Customer(phoneNumber, CustomerStatus.ACTIVE);
        ReflectionTestUtils.setField(entity, "id", customerId);
        return entity;
    }

    private Wallet wallet() {
        Wallet entity = new Wallet(customer, "1023456789", "NGN");
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }

    private Wallet recipientWallet() {
        Customer owner = new Customer("+2348098765432", CustomerStatus.ACTIVE);
        ReflectionTestUtils.setField(owner, "id", UUID.randomUUID());
        Wallet entity = new Wallet(owner, "1098765432", "NGN");
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }

    private CustomerSecurity configuredSecurity() {
        CustomerSecurity entity = new CustomerSecurity(customer, wallet);
        ReflectionTestUtils.setField(entity, "customerId", customerId);
        entity.configurePin("encoded", NOW.minusSeconds(30));
        return entity;
    }

}
