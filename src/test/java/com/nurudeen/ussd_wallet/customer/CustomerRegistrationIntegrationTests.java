package com.nurudeen.ussd_wallet.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nurudeen.ussd_wallet.common.exception.DuplicateCustomerException;
import com.nurudeen.ussd_wallet.customer.service.CustomerRegistrationService;
import com.nurudeen.ussd_wallet.security.principal.CustomerPrincipal;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import com.nurudeen.ussd_wallet.common.exception.OtpRecoveryUnavailableException;
import com.nurudeen.ussd_wallet.security.service.OtpVerificationService;
import com.nurudeen.ussd_wallet.security.service.PinVerificationService;
import com.nurudeen.ussd_wallet.ussd.dto.UssdGatewayRequest;
import com.nurudeen.ussd_wallet.ussd.service.UssdGatewayAuthenticationService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CustomerRegistrationIntegrationTests {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("pin.security.pepper", () -> "integration-test-pin-pepper-that-is-over-32-chars");
        registry.add("ussd.gateway-secret", () -> "integration-test-ussd-gateway-secret-over-32-characters");
        registry.add("ussd.simulator-secret", () -> "integration-test-ussd-simulator-secret-over-32-characters");
    }

    @Test
    void signedGatewayCallbacksReturnPlainTextAndReplayOriginalResponse() throws Exception {
        UssdGatewayRequest request = new UssdGatewayRequest(
                "MTN-SESSION-123",
                "*123#",
                "08012345678",
                "",
                "MTN",
                "callback-1");

        var first = postGateway(request)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("CON ")))
                .andReturn();

        UssdGatewayRequest replay = new UssdGatewayRequest(
                request.sessionId(),
                request.serviceCode(),
                request.phoneNumber(),
                "2",
                request.networkCode(),
                request.callbackId());
        postGateway(replay)
                .andExpect(status().isOk())
                .andExpect(content().string(first.getResponse().getContentAsString()))
                .andReturn();

        assertEquals(1, count("ussd_sessions"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ussd_gateway_callbacks WHERE session_id = ? AND callback_id = ?",
                Integer.class,
                request.sessionId(),
                request.callbackId()));
    }

    @Test
    void gatewayRejectsMissingOrInvalidSignature() throws Exception {
        UssdGatewayRequest request = new UssdGatewayRequest(
                "MTN-SESSION-UNTRUSTED", "*123#", "08012345678", "", "MTN", "callback-1");
        mockMvc.perform(post("/api/v1/ussd")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request))
                        .header("X-USSD-Timestamp", java.time.Instant.now().getEpochSecond())
                        .header("X-USSD-Signature", "00"))
                .andExpect(status().isUnauthorized());
        assertEquals(0, count("ussd_sessions"));
    }

    @Test
    void ussdTransferFlowVerifiesPinAndPostsOnceWithoutPersistingPinInput() throws Exception {
        var sender = registrationService.register("08012345678");
        var recipient = registrationService.register("08087654321");
        jdbcTemplate.update("UPDATE customers SET status = 'ACTIVE' WHERE id IN (?, ?)",
                sender.customerId(), recipient.customerId());
        jdbcTemplate.update(
                "UPDATE wallet_balances SET ledger_balance = 100.00, available_balance = 100.00 "
                        + "WHERE wallet_id = ?",
                sender.wallet().walletId());
        mockMvc.perform(post("/api/v1/wallet/pin/setup")
                .with(user(new CustomerPrincipal(sender.customerId())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pin":"4829","confirmPin":"4829"}
                        """)).andExpect(status().isOk());

        String sessionId = "MTN-TRANSFER-" + java.util.UUID.randomUUID();
        UssdGatewayRequest start = new UssdGatewayRequest(
                sessionId, "*123#", "08012345678", "", "MTN", "cb-start");
        postGateway(start).andExpect(status().isOk()).andExpect(content().string(
                org.hamcrest.Matchers.containsString("Check Balance")));

        postGateway(callback(sessionId, "cb-menu", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("recipient wallet")));
        postGateway(callback(sessionId, "cb-recipient", recipient.wallet().walletNumber()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("transfer amount")));
        postGateway(callback(sessionId, "cb-amount", "25.00"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1. Confirm")));
        postGateway(callback(sessionId, "cb-confirm", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("transaction PIN")));
        postGateway(callback(sessionId, "cb-pin", "4829"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("END Transfer successful!")));

        assertEquals(new java.math.BigDecimal("75.00"), jdbcTemplate.queryForObject(
                "SELECT available_balance FROM wallet_balances WHERE wallet_id = ?",
                java.math.BigDecimal.class,
                sender.wallet().walletId()));
        assertEquals(new java.math.BigDecimal("25.00"), jdbcTemplate.queryForObject(
                "SELECT available_balance FROM wallet_balances WHERE wallet_id = ?",
                java.math.BigDecimal.class,
                recipient.wallet().walletId()));
        assertFalse(jdbcTemplate.queryForObject(
                "SELECT session_data::text LIKE '%4829%' FROM ussd_sessions WHERE session_id = ?",
                Boolean.class,
                sessionId));
        assertEquals(1, count("wallet_transfers"));

        String historySession = "MTN-HISTORY-" + java.util.UUID.randomUUID();
        postGateway(new UssdGatewayRequest(
                        historySession, "*123#", "08012345678", "", "MTN", "history-start"))
                .andExpect(status().isOk());
        postGateway(callback(historySession, "history-menu", "4"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Last transaction")));
        postGateway(callback(historySession, "history-last", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("END Recent transactions")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Sent NGN 25.00")));
    }

    @Test
    void expiredUssdSessionCannotContinueItsWorkflow() throws Exception {
        UssdGatewayRequest start = new UssdGatewayRequest(
                "MTN-SESSION-EXPIRED", "*123#", "08012345678", "", "MTN", "callback-start");
        postGateway(start).andExpect(status().isOk());
        jdbcTemplate.update(
                "UPDATE ussd_sessions SET expires_at = now() - interval '1 second' WHERE session_id = ?",
                start.sessionId());

        postGateway(callback(start.sessionId(), "callback-late", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string("END Session expired. Dial again to start."));
        assertEquals("EXPIRED", jdbcTemplate.queryForObject(
                "SELECT session_status FROM ussd_sessions WHERE session_id = ?",
                String.class,
                start.sessionId()));
    }

    @Test
    void transferRequiresValidPinAndRecordsAtomicDoubleEntryTransfer() throws Exception {
        var sender = registrationService.register("08012345678");
        var recipient = registrationService.register("08087654321");
        jdbcTemplate.update("UPDATE customers SET status = 'ACTIVE' WHERE id IN (?, ?)",
                sender.customerId(), recipient.customerId());
        jdbcTemplate.update(
                "UPDATE wallet_balances SET ledger_balance = 100.00, available_balance = 100.00 "
                        + "WHERE wallet_id = ?",
                sender.wallet().walletId());
        var principal = new CustomerPrincipal(sender.customerId());
        mockMvc.perform(post("/api/v1/wallet/pin/setup")
                .with(user(principal))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pin":"4829","confirmPin":"4829"}
                        """)).andExpect(status().isOk());

        String idempotencyKey = java.util.UUID.randomUUID().toString();
        String transferRequest = """
                {"recipientWalletNumber":"%s","amount":25.00,"pin":"4829","idempotencyKey":"%s"}
                """.formatted(recipient.wallet().walletNumber(), idempotencyKey);
        var transferResult = mockMvc.perform(post("/api/v1/wallet/transfers")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.reference").value(org.hamcrest.Matchers.startsWith("TXN-")))
                .andExpect(jsonPath("$.data.pin").doesNotExist())
                .andReturn();

        assertEquals(new java.math.BigDecimal("75.00"), jdbcTemplate.queryForObject(
                "SELECT available_balance FROM wallet_balances WHERE wallet_id = ?",
                java.math.BigDecimal.class,
                sender.wallet().walletId()));
        assertEquals(new java.math.BigDecimal("25.00"), jdbcTemplate.queryForObject(
                "SELECT available_balance FROM wallet_balances WHERE wallet_id = ?",
                java.math.BigDecimal.class,
                recipient.wallet().walletId()));
        assertEquals(1, count("wallet_transfers"));
        assertEquals(2, count("wallet_transfer_entries"));
        assertEquals(new java.math.BigDecimal("25.00"), jdbcTemplate.queryForObject(
                "SELECT SUM(amount) FROM wallet_transfer_entries WHERE entry_type = 'DEBIT'",
                java.math.BigDecimal.class));
        assertEquals(new java.math.BigDecimal("25.00"), jdbcTemplate.queryForObject(
                "SELECT SUM(amount) FROM wallet_transfer_entries WHERE entry_type = 'CREDIT'",
                java.math.BigDecimal.class));

        String reference = com.jayway.jsonpath.JsonPath.read(
                transferResult.getResponse().getContentAsString(), "$.data.reference");
        mockMvc.perform(post("/api/v1/wallet/transfers")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reference").value(reference));
        assertEquals(1, count("wallet_transfers"));
        assertEquals(2, count("wallet_transfer_entries"));
    }

    @Test
    void failedOrLockedPinNeverMovesFunds() throws Exception {
        var sender = registrationService.register("08012345678");
        var recipient = registrationService.register("08087654321");
        jdbcTemplate.update("UPDATE customers SET status = 'ACTIVE' WHERE id IN (?, ?)",
                sender.customerId(), recipient.customerId());
        jdbcTemplate.update(
                "UPDATE wallet_balances SET ledger_balance = 100.00, available_balance = 100.00 "
                        + "WHERE wallet_id = ?",
                sender.wallet().walletId());
        var principal = new CustomerPrincipal(sender.customerId());
        mockMvc.perform(post("/api/v1/wallet/pin/setup")
                .with(user(principal))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pin":"4829","confirmPin":"4829"}
                        """)).andExpect(status().isOk());

        String body = """
                {"recipientWalletNumber":"%s","amount":25.00,"pin":"7392","idempotencyKey":"%s"}
                """;
        for (int attempt = 0; attempt < 4; attempt++) {
            mockMvc.perform(post("/api/v1/wallet/transfers")
                            .with(user(principal))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body.formatted(
                                    recipient.wallet().walletNumber(),
                                    java.util.UUID.randomUUID())))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/wallet/transfers")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.formatted(
                                recipient.wallet().walletNumber(),
                                java.util.UUID.randomUUID())))
                .andExpect(status().isLocked());

        assertEquals(new java.math.BigDecimal("100.00"), jdbcTemplate.queryForObject(
                "SELECT available_balance FROM wallet_balances WHERE wallet_id = ?",
                java.math.BigDecimal.class,
                sender.wallet().walletId()));
        assertEquals(0, count("wallet_transfers"));
        assertEquals(0, count("wallet_transfer_entries"));
    }

    @Test
    void concurrentTransfersCannotSpendTheSameBalanceTwice() throws Exception {
        var sender = registrationService.register("08012345678");
        var recipient = registrationService.register("08087654321");
        jdbcTemplate.update("UPDATE customers SET status = 'ACTIVE' WHERE id IN (?, ?)",
                sender.customerId(), recipient.customerId());
        jdbcTemplate.update(
                "UPDATE wallet_balances SET ledger_balance = 100.00, available_balance = 100.00 "
                        + "WHERE wallet_id = ?",
                sender.wallet().walletId());
        var principal = new CustomerPrincipal(sender.customerId());
        mockMvc.perform(post("/api/v1/wallet/pin/setup")
                .with(user(principal))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pin":"4829","confirmPin":"4829"}
                        """)).andExpect(status().isOk());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> attempt = () -> mockMvc.perform(post("/api/v1/wallet/transfers")
                            .with(user(principal))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"recipientWalletNumber":"%s","amount":80.00,"pin":"4829",\
                                    "idempotencyKey":"%s"}
                                    """.formatted(
                                    recipient.wallet().walletNumber(),
                                    java.util.UUID.randomUUID())))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            int firstStatus = first.get();
            int secondStatus = second.get();

            assertEquals(1, java.util.stream.Stream.of(firstStatus, secondStatus)
                    .filter(status -> status == 200)
                    .count());
            assertEquals(1, java.util.stream.Stream.of(firstStatus, secondStatus)
                    .filter(status -> status == 409)
                    .count());
            assertEquals(new java.math.BigDecimal("20.00"), jdbcTemplate.queryForObject(
                    "SELECT available_balance FROM wallet_balances WHERE wallet_id = ?",
                    java.math.BigDecimal.class,
                    sender.wallet().walletId()));
            assertEquals(new java.math.BigDecimal("80.00"), jdbcTemplate.queryForObject(
                    "SELECT available_balance FROM wallet_balances WHERE wallet_id = ?",
                    java.math.BigDecimal.class,
                    recipient.wallet().walletId()));
            assertEquals(1, count("wallet_transfers"));
            assertEquals(2, count("wallet_transfer_entries"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerRegistrationService registrationService;

    @Autowired
    private PinVerificationService pinVerificationService;

    @Autowired
    private UssdGatewayAuthenticationService ussdGatewayAuthenticationService;

    @MockitoBean
    private OtpVerificationService otpVerificationService;

    @BeforeEach
    void clearDatabase() {
        doThrow(new OtpRecoveryUnavailableException())
                .when(otpVerificationService)
                .requestOtp(any(), any());
        jdbcTemplate.execute(
                "TRUNCATE TABLE pin_recovery_authorizations, pin_security_audit, ledger_accounts, "
                        + "wallet_transfer_entries, wallet_transfers, wallet_balances, wallets, "
                        + "customer_security, customers CASCADE");
    }

    @Test
    void registersCustomerAndAllWalletRecordsAtomically() throws Exception {
        mockMvc.perform(post("/api/v1/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"08012345678"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.phoneNumber").value("+2348012345678"))
                .andExpect(jsonPath("$.data.customerStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.pinSetupRequired").value(true))
                .andExpect(jsonPath("$.data.wallet.walletNumber").value(org.hamcrest.Matchers.matchesPattern("10\\d{8}")))
                .andExpect(jsonPath("$.data.wallet.currency").value("NGN"))
                .andExpect(jsonPath("$.data.wallet.balance.ledgerBalance").value(0.0))
                .andExpect(jsonPath("$.data.wallet.balance.availableBalance").value(0.0))
                .andExpect(jsonPath("$.data.wallet.balance.reservedBalance").value(0.0));

        assertEquals(1, count("customers"));
        assertEquals(1, count("customer_security"));
        assertEquals(1, count("wallets"));
        assertEquals(1, count("wallet_balances"));
        assertEquals(1, count("ledger_accounts"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM customers WHERE phone_verified_at IS NOT NULL", Integer.class));
        assertEquals("NOT_SET", jdbcTemplate.queryForObject(
                "SELECT pin_status FROM customer_security", String.class));
        assertEquals("LIABILITY", jdbcTemplate.queryForObject(
                "SELECT account_type FROM ledger_accounts", String.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_accounts WHERE wallet_id = (SELECT id FROM wallets)",
                Integer.class));
        assertEquals("NGN", jdbcTemplate.queryForObject(
                "SELECT currency FROM ledger_accounts", String.class).trim());
    }

    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        register("08012345678");

        mockMvc.perform(post("/api/v1/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"+2348012345678"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        assertEquals(1, count("customers"));
        assertEquals(1, count("wallets"));
    }

    @Test
    void invalidPhoneNumberReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phoneNumber":"not-a-phone"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        assertEquals(0, count("customers"));
    }

    @Test
    void failureCreatingLedgerAccountRollsBackEveryRegistrationRecord() throws Exception {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION reject_ledger_account_insert()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'test-only ledger failure';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_ledger_account_insert_trigger
                BEFORE INSERT ON ledger_accounts
                FOR EACH ROW EXECUTE FUNCTION reject_ledger_account_insert()
                """);

        try {
            mockMvc.perform(post("/api/v1/customers/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phoneNumber":"08012345678"}
                                    """))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false));
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_ledger_account_insert_trigger ON ledger_accounts");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_ledger_account_insert()");
        }

        assertEquals(0, count("customers"));
        assertEquals(0, count("customer_security"));
        assertEquals(0, count("wallets"));
        assertEquals(0, count("wallet_balances"));
        assertEquals(0, count("ledger_accounts"));
    }

    @Test
    void failureCreatingWalletRollsBackCustomerAndSecurityRecord() throws Exception {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION reject_wallet_insert()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'test-only wallet failure';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_wallet_insert_trigger
                BEFORE INSERT ON wallets
                FOR EACH ROW EXECUTE FUNCTION reject_wallet_insert()
                """);

        try {
            mockMvc.perform(post("/api/v1/customers/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phoneNumber":"08012345678"}
                                    """))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false));
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_wallet_insert_trigger ON wallets");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_wallet_insert()");
        }

        assertEquals(0, count("customers"));
        assertEquals(0, count("customer_security"));
        assertEquals(0, count("wallets"));
        assertEquals(0, count("wallet_balances"));
        assertEquals(0, count("ledger_accounts"));
    }

    @Test
    void failureInitializingBalanceRollsBackAllRegistrationRecords() throws Exception {
        jdbcTemplate.execute("""
                CREATE OR REPLACE FUNCTION reject_wallet_balance_insert()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'test-only wallet balance failure';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_wallet_balance_insert_trigger
                BEFORE INSERT ON wallet_balances
                FOR EACH ROW EXECUTE FUNCTION reject_wallet_balance_insert()
                """);

        try {
            mockMvc.perform(post("/api/v1/customers/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phoneNumber":"08012345678"}
                                    """))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.success").value(false));
        } finally {
            jdbcTemplate.execute(
                    "DROP TRIGGER IF EXISTS reject_wallet_balance_insert_trigger ON wallet_balances");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_wallet_balance_insert()");
        }

        assertEquals(0, count("customers"));
        assertEquals(0, count("customer_security"));
        assertEquals(0, count("wallets"));
        assertEquals(0, count("wallet_balances"));
        assertEquals(0, count("ledger_accounts"));
    }

    @Test
    void concurrentRequestsForSameNumberCreateExactlyOneCustomer() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> attempt = () -> {
                try {
                    registrationService.register("08012345678");
                    return true;
                } catch (DuplicateCustomerException exception) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(attempt);
            Future<Boolean> second = executor.submit(attempt);

            assertTrue(first.get() ^ second.get());
            assertEquals(1, count("customers"));
            assertEquals(1, count("wallets"));
            assertEquals(1, count("wallet_balances"));
            assertEquals(1, count("ledger_accounts"));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ledger_accounts WHERE wallet_id = (SELECT id FROM wallets)",
                    Integer.class));
            assertEquals("NGN", jdbcTemplate.queryForObject(
                    "SELECT currency FROM ledger_accounts", String.class).trim());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void successfulRegistrationDoesNotSetPhoneVerification() throws Exception {
        register("08012345678");
        assertFalse(jdbcTemplate.queryForObject(
                "SELECT phone_verified_at IS NOT NULL FROM customers", Boolean.class));
    }

    @Test
    void authenticatedCustomerRetrievesOnlyTheirOwnWallet() throws Exception {
        var firstCustomer = registrationService.register("08012345678");
        var secondCustomer = registrationService.register("08087654321");

        mockMvc.perform(get("/api/v1/wallets/me")
                        .with(user(new CustomerPrincipal(firstCustomer.customerId())))
                        .param("customerId", secondCustomer.customerId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.walletNumber")
                        .value(firstCustomer.wallet().walletNumber()))
                .andExpect(jsonPath("$.data.walletNumber")
                        .value(org.hamcrest.Matchers.not(secondCustomer.wallet().walletNumber())));
    }

    @Test
    void walletRetrievalReturnsNotFoundWhenAuthenticatedCustomerHasNoWallet() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/me")
                        .with(user(new CustomerPrincipal(java.util.UUID.randomUUID()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void walletRetrievalRejectsAuthenticatedPrincipalWithoutCustomerIdentity() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/me").with(user("demo-user")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void walletRetrievalRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pinSetupHashesPinAndStatusNeverReturnsHash() throws Exception {
        var registration = registrationService.register("08012345678");
        var principal = new CustomerPrincipal(registration.customerId());

        mockMvc.perform(post("/api/v1/wallet/pin/setup")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pin":"4829","confirmPin":"4829"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        String encodedPin = jdbcTemplate.queryForObject(
                "SELECT pin_hash FROM customer_security WHERE customer_id = ?",
                String.class,
                registration.customerId());
        assertTrue(encodedPin.startsWith("$argon2id$"));
        assertFalse(encodedPin.equals("4829"));

        mockMvc.perform(get("/api/v1/wallet/pin/status").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pinConfigured").value(true))
                .andExpect(jsonPath("$.data.verificationLocked").value(false))
                .andExpect(jsonPath("$.data.lockExpiresAt").doesNotExist());

        mockMvc.perform(post("/api/v1/wallet/pin/setup")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pin":"7392","confirmPin":"7392"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void pinVerificationPersistsFailureCountAndLocksAfterFiveFailures() throws Exception {
        var registration = registrationService.register("08012345678");
        var principal = new CustomerPrincipal(registration.customerId());
        mockMvc.perform(post("/api/v1/wallet/pin/setup")
                .with(user(principal))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pin":"4829","confirmPin":"4829"}
                        """)).andExpect(status().isOk());

        for (int attempt = 1; attempt <= 4; attempt++) {
            mockMvc.perform(post("/api/v1/wallet/pin/verify")
                            .with(user(principal))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"pin":"7392"}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid transaction PIN"));
        }
        mockMvc.perform(post("/api/v1/wallet/pin/verify")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pin":"7392"}
                                """))
                .andExpect(status().isLocked());

        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT failed_pin_attempts FROM customer_security WHERE customer_id = ?",
                Integer.class,
                registration.customerId()));
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT locked_until > now() FROM customer_security WHERE customer_id = ?",
                Boolean.class,
                registration.customerId()));

        mockMvc.perform(post("/api/v1/wallet/pin/verify")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pin":"4829"}
                                """))
                .andExpect(status().isLocked());
    }

    @Test
    void concurrentPinFailuresCannotExceedLockoutThreshold() throws Exception {
        var registration = registrationService.register("08012345678");
        mockMvc.perform(post("/api/v1/wallet/pin/setup")
                .with(user(new CustomerPrincipal(registration.customerId())))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pin":"4829","confirmPin":"4829"}
                        """)).andExpect(status().isOk());

        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            Callable<Boolean> invalidAttempt = () -> {
                try {
                    pinVerificationService.verify(registration.customerId(), "7392");
                    return false;
                } catch (com.nurudeen.ussd_wallet.common.exception.PinVerificationLockedException exception) {
                    return true;
                } catch (com.nurudeen.ussd_wallet.common.exception.InvalidTransactionPinException exception) {
                    return false;
                }
            };
            var attempts = java.util.stream.IntStream.range(0, 10)
                    .mapToObj(index -> executor.submit(invalidAttempt))
                    .toList();
            long lockedAttempts = 0;
            for (var attempt : attempts) {
                if (attempt.get()) {
                    lockedAttempts++;
                }
            }

            assertTrue(lockedAttempts >= 5);
            assertEquals(5, jdbcTemplate.queryForObject(
                    "SELECT failed_pin_attempts FROM customer_security WHERE customer_id = ?",
                    Integer.class,
                    registration.customerId()));
            assertTrue(jdbcTemplate.queryForObject(
                    "SELECT pin_status = 'LOCKED' FROM customer_security WHERE customer_id = ?",
                    Boolean.class,
                    registration.customerId()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void successfulVerificationResetsFailureCountAndChangeRequiresCurrentPin() throws Exception {
        var registration = registrationService.register("08012345678");
        var principal = new CustomerPrincipal(registration.customerId());
        mockMvc.perform(post("/api/v1/wallet/pin/setup")
                .with(user(principal))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pin":"4829","confirmPin":"4829"}
                        """)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/wallet/pin/verify")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pin":"7392"}
                                """))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/wallet/pin/verify")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pin":"4829"}
                                """))
                .andExpect(status().isOk());

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT failed_pin_attempts FROM customer_security WHERE customer_id = ?",
                Integer.class,
                registration.customerId()));

        mockMvc.perform(post("/api/v1/wallet/pin/change")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPin":"4829","newPin":"7392","confirmPin":"7392"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/wallet/pin/verify")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pin":"7392"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void pinEndpointsRequireAuthenticatedCustomerIdentityAndRecoveryFailsClosedWithoutOtp() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/pin/status"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/wallet/pin/status").with(user("ordinary-user")))
                .andExpect(status().isForbidden());

        var registration = registrationService.register("08012345678");
        mockMvc.perform(post("/api/v1/wallet/pin/reset/request")
                        .with(user(new CustomerPrincipal(registration.customerId())))
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message")
                        .value("PIN recovery is unavailable because identity verification is not configured"));
    }

    @Test
    void recoveryAuthorizationIsOneTimeAndBoundToAuthenticatedCustomer() throws Exception {
        var registration = registrationService.register("08012345678");
        var otherRegistration = registrationService.register("08087654321");
        var principal = new CustomerPrincipal(registration.customerId());
        doNothing().when(otpVerificationService).requestOtp(
                registration.customerId(), OtpVerificationService.PIN_RECOVERY_PURPOSE);
        when(otpVerificationService.verifyAndConsume(
                eq(registration.customerId()),
                eq(OtpVerificationService.PIN_RECOVERY_PURPOSE),
                eq("123456"))).thenReturn(true, false);

        mockMvc.perform(post("/api/v1/wallet/pin/setup")
                .with(user(principal))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pin":"4829","confirmPin":"4829"}
                        """)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/wallet/pin/reset/request")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isAccepted());
        var otpResponse = mockMvc.perform(post("/api/v1/wallet/pin/reset/verify")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"otp":"123456"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(
                otpResponse.getResponse().getContentAsString(), "$.data.recoveryToken");

        mockMvc.perform(post("/api/v1/wallet/pin/reset/verify")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"otp":"123456"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/wallet/pin/reset/complete")
                        .with(user(new CustomerPrincipal(otherRegistration.customerId())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recoveryToken":"%s","newPin":"7392","confirmPin":"7392"}
                                """.formatted(token)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/wallet/pin/reset/complete")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recoveryToken":"%s","newPin":"7392","confirmPin":"7392"}
                                """.formatted(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/wallet/pin/reset/complete")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recoveryToken":"%s","newPin":"6248","confirmPin":"6248"}
                                """.formatted(token)))
                .andExpect(status().isBadRequest());
    }

    private void register(String phoneNumber) throws Exception {
        mockMvc.perform(post("/api/v1/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phoneNumber + "\"}"))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions postGateway(UssdGatewayRequest request)
            throws Exception {
        String timestamp = Long.toString(java.time.Instant.now().getEpochSecond());
        String signature = ussdGatewayAuthenticationService.sign(request, timestamp);
        String body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request);
        return mockMvc.perform(post("/api/v1/ussd")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-USSD-Timestamp", timestamp)
                .header("X-USSD-Signature", signature)
                .content(body));
    }

    private UssdGatewayRequest callback(String sessionId, String callbackId, String text) {
        return new UssdGatewayRequest(
                sessionId, "*123#", "08012345678", text, "MTN", callbackId);
    }

    private int count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }
}
