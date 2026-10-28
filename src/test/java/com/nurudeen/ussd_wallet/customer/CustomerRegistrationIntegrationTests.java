package com.nurudeen.ussd_wallet.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nurudeen.ussd_wallet.common.exception.DuplicateCustomerException;
import com.nurudeen.ussd_wallet.customer.service.CustomerRegistrationService;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerRegistrationService registrationService;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE ledger_accounts, wallet_balances, wallets, customer_security, customers CASCADE");
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
                .andExpect(jsonPath("$.data.balance.ledgerBalance").value(0.0))
                .andExpect(jsonPath("$.data.balance.availableBalance").value(0.0))
                .andExpect(jsonPath("$.data.balance.reservedBalance").value(0.0));

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

    private void register(String phoneNumber) throws Exception {
        mockMvc.perform(post("/api/v1/customers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phoneNumber + "\"}"))
                .andExpect(status().isCreated());
    }

    private int count(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }
}
