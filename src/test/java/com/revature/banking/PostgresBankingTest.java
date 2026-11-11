package com.revature.banking;

import com.revature.banking.model.*;

import com.revature.banking.exception.BankingException;

import com.revature.banking.dao.*;
import com.revature.banking.dao.jdbc.*;
import com.revature.banking.model.Account;
import com.revature.banking.model.HistoryEntry;
import com.revature.banking.model.TransactionType;
import com.revature.banking.security.BcryptPasswordHasher;
import com.revature.banking.security.SessionContext;
import com.revature.banking.service.*;
import com.revature.banking.util.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.revature.banking.model.AccountType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;

class PostgresBankingTest {
    private static ConnectionFactory admin;
    private static ConnectionFactory database;
    private static String schema;
    private static final String PASSWORD = "Fake!BankPassword1";
    private static final AppConfig CONFIG = new AppConfig(Duration.ofMinutes(5), 5,
            new BigDecimal("1000000.00"), new BigDecimal("100.00"), 5, Duration.ofMinutes(15), 4, 50);
    private static final AtomicInteger sequence = new AtomicInteger();
    private Clock clock;
    private AccountDAO dao;
    private AuditDAO audit;
    private AuthService auth;
    private AccountService accounts;
    private TransactionService transactions;

    @BeforeAll
    static void createSchema() throws Exception {
        String url = System.getenv("TEST_DB_URL");
        if (Boolean.getBoolean("requireTestDatabase") && (url == null || url.isBlank()))
            throw new IllegalStateException("Set TEST_DB_URL to a dedicated database for -Pdatabase-tests.");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "Set TEST_DB_URL for PostgreSQL integration tests");
        String configuredAppUrl = System.getenv("DB_URL");
        if (configuredAppUrl != null && url.equals(configuredAppUrl)) {
            throw new IllegalStateException("Use a dedicated test database.");
        }
        Properties credentials = new Properties();
        credentials.setProperty("user", System.getenv().getOrDefault("TEST_DB_USERNAME", "postgres"));
        credentials.setProperty("password", System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
        admin = () -> DriverManager.getConnection(url, credentials);
        schema = "bank_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = admin.open(); Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA " + schema);
        }
        database = () -> {
            Connection c = admin.open();
            try {
                c.setSchema(schema);
                return c;
            } catch (SQLException e) {
                c.close();
                throw e;
            }
        };
        try (Connection c = database.open(); Statement s = c.createStatement()) {
            s.execute(Files.readString(Path.of("sql/postgres/schema.sql")));
        }
    }

    @AfterAll
    static void dropSchema() throws SQLException {
        if (admin != null && schema != null) {
            try (Connection c = admin.open(); Statement s = c.createStatement()) {
                s.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    @BeforeEach
    void services() {
        clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        dao = new JdbcAccountDAO();
        audit = new JdbcAuditDAO();
        configure(clock);
    }

    private void configure(Clock time) {
        JdbcTransaction tx = new JdbcTransaction(database, CONFIG.querySeconds());
        auth = new AuthService(new JdbcCustomerDAO(), audit, tx, CONFIG, time, new BcryptPasswordHasher(4));
        accounts = new AccountService(dao, tx, time);
        transactions = new TransactionService(dao, new JdbcTransactionDAO(), audit, tx, CONFIG, time);
    }

    @Test
    void registerAuthenticatesChangesPasswordAndAuditsLogout() throws Exception {
        User u = user();
        String hash = (String) scalar("SELECT password_hash FROM customers WHERE customer_id=?", u.id());
        assertTrue(hash.startsWith("$2"));
        assertNotEquals(PASSWORD, hash);
        assertEquals("Demo Person", auth.profile(u.id()).name());
        assertThrows(BankingException.class, () -> auth.register("Demo Person", "different@example.test",
                "", u.username(), PASSWORD.toCharArray()));
        assertThrows(BankingException.class, () -> auth.register("Demo Person", u.username() + "@example.test",
                "", "different", PASSWORD.toCharArray()));
        assertNull(auth.login(u.username(), "wrong".toCharArray()));
        assertNull(auth.login("unknown_user", PASSWORD.toCharArray()));
        assertNotNull(auth.login(u.username(), PASSWORD.toCharArray()));
        assertThrows(BankingException.class, () -> auth.changePassword(u.id(),
                "wrong".toCharArray(), "Better!Password2".toCharArray()));
        auth.changePassword(u.id(), PASSWORD.toCharArray(), "Better!Password2".toCharArray());
        assertNull(auth.login(u.username(), PASSWORD.toCharArray()));
        SessionContext session = auth.login(u.username(), "Better!Password2".toCharArray());
        assertEquals(u.id(), session.customerId());
        session.logout();
        auth.end(session);
        assertEquals("LOGOUT", scalar("SELECT outcome FROM login_audit WHERE customer_id=? ORDER BY audit_id DESC LIMIT 1", u.id()));
    }

    @Test
    void profileAndPasswordChangesAreAuditedAndDuplicateEmailsRollback() throws Exception {
        User u = user(), other = user();
        auth.updateProfile(u.id(), "Updated Name", "updated_" + u.id() + "@example.test", "+1 555 0100");
        assertEquals("Updated Name", auth.profile(u.id()).name());
        assertEquals("PROFILE_CHANGED", scalar("SELECT event_type FROM transaction_audit WHERE customer_id=? ORDER BY audit_id DESC LIMIT 1", u.id()));
        assertThrows(BankingException.class, () -> auth.updateProfile(u.id(), "Bad Update", auth.profile(other.id()).email(), ""));
        assertEquals("Updated Name", auth.profile(u.id()).name());
        auth.changePassword(u.id(), PASSWORD.toCharArray(), "Changed!Password23".toCharArray());
        assertEquals("PASSWORD_CHANGED", scalar("SELECT event_type FROM transaction_audit WHERE customer_id=? ORDER BY audit_id DESC LIMIT 1", u.id()));
    }

    @Test
    void cleanCustomerCliAcceptanceFlow() throws Exception {
        String username = "cli_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        ReferenceGenerator random = new ReferenceGenerator();
        String savingsNumber = random.nextAccountNumber(), currentNumber = random.nextAccountNumber();
        String depositReference = random.nextTransactionReference();
        java.util.ArrayDeque<String> plannedAccounts = new java.util.ArrayDeque<>(List.of(savingsNumber, currentNumber));
        IdentifierGenerator generator = new IdentifierGenerator() {
            private boolean firstReference = true;

            public String nextAccountNumber() {
                return plannedAccounts.isEmpty() ? random.nextAccountNumber() : plannedAccounts.remove();
            }

            public String nextTransactionReference() {
                if (firstReference) {
                    firstReference = false;
                    return depositReference;
                }
                return random.nextTransactionReference();
            }
        };
        JdbcTransaction tx = new JdbcTransaction(database, CONFIG.querySeconds());
        var events = new com.revature.banking.service.event.InProcessEventPublisher(
                List.of(new com.revature.banking.service.event.AuditSubscriber(audit)));
        AccountService cliAccounts = new AccountService(dao, tx, clock, generator);
        TransactionService cliTransactions = new TransactionService(dao, new JdbcTransactionDAO(), audit,
                tx, CONFIG, clock, generator, attempt -> {
        }, events);
        String script = String.join("\n", "1", "CLI Customer", username + "@example.test", "", username, PASSWORD, PASSWORD,
                "2", username, PASSWORD, "1", "15", "Updated CLI Customer", username + "@example.test", "",
                "3", "1", "3", "2", "5", "1", "1000", "6", "1", "10", "7", "1", currentNumber, "20",
                "3", "3", "1", "100", "1", "3", "4", "120", "6", "8", "1", "1", "40", "9",
                "10", "1", "ALL", "", "", "50", "12", "13", "14", depositReference, "0", "3", "");
        java.io.InputStream originalIn = System.in;
        java.io.PrintStream originalOut = System.out;
        java.io.ByteArrayOutputStream transcript = new java.io.ByteArrayOutputStream();
        try {
            System.setIn(new java.io.ByteArrayInputStream(script.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(new java.io.PrintStream(transcript, true, java.nio.charset.StandardCharsets.UTF_8));
            new com.revature.banking.app.BankingApplication(auth, cliAccounts, cliTransactions, CONFIG, clock).run();
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
        String text = transcript.toString(java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(Path.of("target", "acceptance-cli.txt"), text);
        assertTrue(text.contains("Registration successful"), text);
        assertTrue(text.contains("Login successful"), text);
        assertTrue(text.contains("Profile updated"), text);
        assertTrue(text.contains("Term deposit created"), text);
        assertTrue(text.contains("Loan record created"), text);
        assertTrue(text.contains("No deposits ready for payout"), text);
        assertTrue(text.contains("Goodbye."), text);
        assertFalse(text.contains("database operation could not"), text);
        long owner = ((Number) scalar("SELECT customer_id FROM customers WHERE username=?", username)).longValue();
        assertEquals(new BigDecimal("830.00"), cliAccounts.balance(owner, savingsNumber).balance());
        assertEquals(new BigDecimal("20.00"), cliAccounts.balance(owner, currentNumber).balance());
        assertEquals(new BigDecimal("80.00"), cliAccounts.portfolio(owner).debt());
        assertEquals("LOGOUT", scalar("SELECT outcome FROM login_audit WHERE customer_id=? ORDER BY audit_id DESC LIMIT 1", owner));
    }

    @Test
    void timedLockExpiresButClosedAndManualLockStayDenied() throws Exception {
        User u = user();
        for (int i = 0; i < 5; i++) assertNull(auth.login(u.username(), "wrong".toCharArray()));
        assertEquals("LOCKED", scalar("SELECT status FROM customers WHERE customer_id=?", u.id()));
        assertEquals("LOCKED", scalar("SELECT outcome FROM login_audit WHERE customer_id=? ORDER BY audit_id DESC LIMIT 1", u.id()));
        assertNull(auth.login(u.username(), PASSWORD.toCharArray()));
        configure(Clock.offset(clock, Duration.ofMinutes(15)));
        assertNotNull(auth.login(u.username(), PASSWORD.toCharArray()));
        assertEquals(0, scalar("SELECT failed_login_count FROM customers WHERE customer_id=?", u.id()));
        for (String status : List.of("LOCKED", "CLOSED")) {
            update("UPDATE customers SET status=? WHERE customer_id=?", status, u.id());
            assertNull(auth.login(u.username(), PASSWORD.toCharArray()));
        }
    }

    @Test
    void transfersBalanceAndLedgerAndDeniesForeignAccountAccess() throws Exception {
        User u = user(), other = user();
        Account from = accounts.openCash(u.id(), SAVINGS);
        Account to = accounts.openCash(other.id(), CURRENT);
        String deposit = transactions.deposit(u.id(), from.number(), "120.00");
        assertEquals("SUCCESS", transactions.operationStatus(u.id(), deposit).outcome());
        assertNull(transactions.operationStatus(other.id(), deposit));
        assertThrows(BankingException.class, () -> accounts.balance(u.id(), to.number()));
        assertThrows(BankingException.class, () -> transactions.deposit(u.id(), to.number(), "1"));
        assertThrows(BankingException.class, () -> transactions.withdraw(u.id(), from.number(), "121"));
        assertThrows(BankingException.class, () -> transactions.withdraw(u.id(), from.number(), "1.001"));
        assertThrows(BankingException.class, () -> transactions.transfer(u.id(), from.number(), from.number(), "1"));
        update("UPDATE accounts SET status='FROZEN' WHERE account_id=?", to.id());
        assertThrows(BankingException.class, () -> transactions.transfer(u.id(), from.number(), to.number(), "1"));
        update("UPDATE accounts SET status='ACTIVE' WHERE account_id=?", to.id());

        String ref = transactions.transfer(u.id(), from.number(), to.number(), "20.12");
        assertEquals(new BigDecimal("99.88"), balance(u, from));
        assertEquals(new BigDecimal("20.12"), balance(other, to));
        assertEquals("SUCCESS", scalar("SELECT status FROM transactions WHERE reference_number=?", ref));
        assertEquals(2L, scalar("SELECT count(*) FROM transactions WHERE source_account_id=? OR destination_account_id=?", from.id(), from.id()));
        assertEquals("CREDIT", transactions.history(other.id(), to.number(), null, null, null, null, 10).get(0).direction());
        assertThrows(BankingException.class, () -> transactions.history(u.id(), to.number(), null, null, null, null, 10));
    }

    @Test
    void transferRollsBackWhenLedgerInsertFailsAfterBothBalanceWrites() throws Exception {
        User u = user();
        Account from = accounts.openCash(u.id(), SAVINGS);
        Account to = accounts.openCash(u.id(), CURRENT);
        transactions.deposit(u.id(), from.number(), "50");
        update("ALTER TABLE transactions ADD CONSTRAINT reject_test_transfer CHECK (transaction_type <> 'TRANSFER') NOT VALID");
        try {
            assertThrows(JdbcTransaction.RolledBack.class,
                    () -> transactions.transfer(u.id(), from.number(), to.number(), "20"));
        } finally {
            update("ALTER TABLE transactions DROP CONSTRAINT reject_test_transfer");
        }
        assertEquals(new BigDecimal("50.00"), balance(u, from));
        assertEquals(new BigDecimal("0.00"), balance(u, to));
        assertEquals(0L, scalar("SELECT count(*) FROM transactions WHERE source_account_id=?", from.id()));
        assertEquals("ROLLED_BACK", scalar("SELECT outcome FROM transaction_audit WHERE customer_id=? AND event_type='TRANSFER'", u.id()));
    }

    @Test
    void concurrentWithdrawalsCannotOverspend() throws Exception {
        User u = user();
        Account from = accounts.openCash(u.id(), SAVINGS);
        transactions.deposit(u.id(), from.number(), "100");
        Callable<Boolean> withdraw = () -> {
            try {
                transactions.withdraw(u.id(), from.number(), "75");
                return true;
            } catch (BankingException e) {
                assertEquals("INSUFFICIENT_FUNDS", e.code());
                return false;
            }
        };
        List<Boolean> results = concurrently(withdraw, withdraw);
        assertEquals(1L, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(new BigDecimal("25.00"), balance(u, from));
    }

    @Test
    void oppositeDirectionTransfersBothCommit() throws Exception {
        User u = user();
        Account first = accounts.openCash(u.id(), SAVINGS), second = accounts.openCash(u.id(), CURRENT);
        transactions.deposit(u.id(), first.number(), "100");
        transactions.deposit(u.id(), second.number(), "100");
        concurrently(() -> transactions.transfer(u.id(), first.number(), second.number(), "40"),
                () -> transactions.transfer(u.id(), second.number(), first.number(), "30"));
        assertEquals(new BigDecimal("90.00"), balance(u, first));
        assertEquals(new BigDecimal("110.00"), balance(u, second));
    }

    @Test
    void termDepositAndLoanHaveDistinctBalanceRules() throws Exception {
        User u = user();
        Account cash = accounts.openCash(u.id(), SAVINGS);
        transactions.deposit(u.id(), cash.number(), "500");
        var deposit = transactions.openDeposit(u.id(), cash.number(), "100", 1);
        var loan = transactions.openLoan(u.id(), "120", 12);
        assertEquals(new BigDecimal("400.00"), accounts.portfolio(u.id()).availableCash());
        assertEquals(new BigDecimal("100.00"), accounts.portfolio(u.id()).termDeposits());
        assertEquals(new BigDecimal("120.00"), accounts.portfolio(u.id()).debt());
        assertThrows(BankingException.class, () -> transactions.settleDeposit(u.id(), deposit.number(), cash.number()));
        assertThrows(BankingException.class, () -> transactions.withdraw(u.id(), loan.number(), "10"));
        assertThrows(BankingException.class, () -> transactions.repayLoan(u.id(), cash.number(), loan.number(), "121"));
        transactions.repayLoan(u.id(), cash.number(), loan.number(), "40");
        assertEquals(new BigDecimal("80.00"), accounts.balance(u.id(), loan.number()).balance());
        transactions.repayLoan(u.id(), cash.number(), loan.number(), "80");
        assertEquals(AccountStatus.CLOSED, accounts.balance(u.id(), loan.number()).status());
        assertEquals("DEBT REDUCTION", transactions.history(u.id(), loan.number(), null, null, null, null, 10).get(0).direction());

        configure(Clock.offset(clock, Duration.ofDays(31)));
        assertEquals(AccountStatus.MATURED, accounts.balance(u.id(), deposit.number()).status());
        transactions.settleDeposit(u.id(), deposit.number(), cash.number());
        assertEquals(AccountStatus.CLOSED, accounts.balance(u.id(), deposit.number()).status());
        assertEquals(new BigDecimal("380.00"), balance(u, cash));
    }

    @Test
    void historyCursorHandlesTiedTimestampsAndNewerInserts() throws Exception {
        User u = user();
        Account a = accounts.openCash(u.id(), SAVINGS);
        String first = transactions.deposit(u.id(), a.number(), "1");
        String second = transactions.deposit(u.id(), a.number(), "2");
        String third = transactions.deposit(u.id(), a.number(), "3");
        update("UPDATE transactions SET created_at='2026-01-01T00:00:00Z' WHERE destination_account_id=?", a.id());
        var page = transactions.history(u.id(), a.number(), TransactionType.DEPOSIT, null, null, null, 2);
        assertEquals(List.of(third, second), page.subList(0, 2).stream().map(HistoryEntry::reference).toList());
        transactions.deposit(u.id(), a.number(), "4");
        var next = transactions.history(u.id(), a.number(), TransactionType.DEPOSIT, null, null, page.get(1), 2);
        assertEquals(List.of(first), next.stream().map(HistoryEntry::reference).toList());
    }

    @Test
    void injectionStringsStayDataAndTimeoutIsAudited() throws Exception {
        User u = user();
        String hostile = "name'); DROP TABLE customers;--";
        long id = auth.register(hostile, "injection@example.test", "", "injection_user", PASSWORD.toCharArray());
        assertEquals(hostile, auth.profile(id).name());
        assertNull(auth.login("' OR '1'='1", PASSWORD.toCharArray()));
        Account a = accounts.openCash(u.id(), SAVINGS);
        assertThrows(BankingException.class, () -> transactions.deposit(u.id(), "' OR 1=1 --", "1"));
        assertEquals(new BigDecimal("0.00"), balance(u, a));

        SessionContext session = auth.login(u.username(), PASSWORD.toCharArray());
        assertFalse(session.touch(Clock.offset(clock, CONFIG.idle()), CONFIG.idle()));
        auth.end(session);
        assertEquals("TIMEOUT", scalar("SELECT outcome FROM login_audit WHERE customer_id=? ORDER BY audit_id DESC LIMIT 1", u.id()));
    }

    @Test
    void lostCommitAcknowledgementIsNeverRetriedAndCanBeCheckedByReference() throws Exception {
        User u = user();
        Account cash = accounts.openCash(u.id(), SAVINGS);
        // Let PostgreSQL commit, then simulate losing the server response.
        Connection real = database.open();
        Connection connection = mock(Connection.class, delegatesTo(real));
        doAnswer(call -> {
            real.commit();
            throw new SQLException("Simulated lost acknowledgement", "08006");
        }).when(connection).commit();
        ConnectionFactory lostAcknowledgement = mock(ConnectionFactory.class);
        when(lostAcknowledgement.open()).thenReturn(connection);
        TransactionService simulated = new TransactionService(dao, new JdbcTransactionDAO(), audit,
                new JdbcTransaction(lostAcknowledgement, CONFIG.querySeconds()), CONFIG, clock);
        BankingException e = assertThrows(BankingException.class, () -> simulated.deposit(u.id(), cash.number(), "40"));
        assertEquals("COMMIT_UNCONFIRMED", e.code());
        verify(connection, times(1)).commit();
        verify(lostAcknowledgement, times(1)).open();
        String reference = e.getMessage().substring(e.getMessage().lastIndexOf("TX-"));
        assertEquals("SUCCESS", transactions.operationStatus(u.id(), reference).outcome());
        assertEquals(new BigDecimal("40.00"), balance(u, cash));
    }

    private static class User {
        private final long id;
        private final String username;

        User(long id, String username) {
            this.id = id;
            this.username = username;
        }

        long id() {
            return id;
        }

        String username() {
            return username;
        }
    }

    @Test
    void invalidAmountsAndInactiveAccountsNeverChangeBalancesOrHistory() throws Exception {
        User u = user(), other = user();
        Account cash = accounts.openCash(u.id(), SAVINGS), target = accounts.openCash(u.id(), CURRENT);
        transactions.deposit(u.id(), cash.number(), "200");
        for (String invalid : List.of("0", "-1", "bad", "1.001", "1000000.01")) {
            assertThrows(BankingException.class, () -> transactions.deposit(u.id(), cash.number(), invalid));
            assertThrows(BankingException.class, () -> transactions.withdraw(u.id(), cash.number(), invalid));
        }
        for (String status : List.of("FROZEN", "CLOSED")) {
            update("UPDATE accounts SET status=? WHERE account_id=?", status, target.id());
            assertThrows(BankingException.class, () -> transactions.deposit(u.id(), target.number(), "1"));
            assertThrows(BankingException.class, () -> transactions.withdraw(u.id(), target.number(), "1"));
            assertThrows(BankingException.class, () -> transactions.transfer(u.id(), cash.number(), target.number(), "1"));
        }
        assertThrows(BankingException.class, () -> transactions.withdraw(other.id(), cash.number(), "1"));
        assertThrows(BankingException.class, () -> transactions.transfer(u.id(), cash.number(), "UNKNOWN", "1"));
        assertEquals(new BigDecimal("200.00"), balance(u, cash));
        assertEquals(new BigDecimal("0.00"), balance(u, target));
        assertEquals(1L, scalar("SELECT count(*) FROM transactions WHERE source_account_id=? OR destination_account_id=?", cash.id(), cash.id()));
    }

    @Test
    void failedAccountOpeningRollsBackTermsBalancesLedgerAndAccountRows() throws Exception {
        User u = user();
        Account cash = accounts.openCash(u.id(), SAVINGS);
        transactions.deposit(u.id(), cash.number(), "500");
        for (String invalid : List.of("50", "501"))
            assertThrows(BankingException.class, () -> transactions.openDeposit(u.id(), cash.number(), invalid, 1));
        for (int months : List.of(0, 121)) {
            assertThrows(BankingException.class, () -> transactions.openDeposit(u.id(), cash.number(), "100", months));
            assertThrows(BankingException.class, () -> transactions.openLoan(u.id(), "100", months));
        }
        AuditDAO failingAudit = mock(AuditDAO.class, delegatesTo(audit));
        doThrow(new SQLException("simulated success audit failure", "23514")).when(failingAudit)
                .transaction(any(), eq(u.id()), anyString(), anyString(), eq("SUCCESS"), isNull());
        TransactionService simulated = new TransactionService(dao, new JdbcTransactionDAO(), failingAudit,
                new JdbcTransaction(database, CONFIG.querySeconds()), CONFIG, clock);
        assertThrows(JdbcTransaction.RolledBack.class, () -> simulated.openDeposit(u.id(), cash.number(), "100", 1));
        assertThrows(JdbcTransaction.RolledBack.class, () -> simulated.openLoan(u.id(), "100", 12));
        assertEquals(1L, scalar("SELECT count(*) FROM accounts WHERE customer_id=?", u.id()));
        assertEquals(new BigDecimal("500.00"), balance(u, cash));
        assertEquals(1L, scalar("SELECT count(*) FROM transactions WHERE source_account_id=? OR destination_account_id=?", cash.id(), cash.id()));
        assertEquals(2L, scalar("SELECT count(*) FROM transaction_audit WHERE customer_id=? AND outcome='ROLLED_BACK'", u.id()));
    }

    @Test
    void historyDateTypeAndOwnershipFiltersAreEnforced() throws Exception {
        User u = user(), other = user();
        Account cash = accounts.openCash(u.id(), SAVINGS);
        String deposit = transactions.deposit(u.id(), cash.number(), "100");
        String withdrawal = transactions.withdraw(u.id(), cash.number(), "10");
        update("UPDATE transactions SET created_at='2026-02-01T00:00:00Z' WHERE reference_number=?", deposit);
        update("UPDATE transactions SET created_at='2026-02-02T00:00:00Z' WHERE reference_number=?", withdrawal);
        OffsetDateTime from = OffsetDateTime.parse("2026-02-01T00:00:00Z");
        OffsetDateTime before = from.plusDays(1);
        assertEquals(List.of(deposit), transactions.history(u.id(), cash.number(), null, from, before, null, 10)
                .stream().map(HistoryEntry::reference).toList());
        assertEquals(List.of(withdrawal), transactions.history(u.id(), cash.number(), TransactionType.WITHDRAWAL, null, null, null, 10)
                .stream().map(HistoryEntry::reference).toList());
        assertThrows(BankingException.class, () -> transactions.history(other.id(), cash.number(), null, null, null, null, 10));
        assertThrows(BankingException.class, () -> transactions.history(u.id(), cash.number(), null, before, from, null, 10));
        assertThrows(BankingException.class, () -> transactions.history(u.id(), cash.number(), null, null, null, null, 51));
        assertNull(transactions.operationStatus(other.id(), deposit));
        assertEquals("SUCCESS", transactions.operationStatus(u.id(), deposit).outcome());
    }

    @Test
    void portfolioSeparatesFrozenCashTermPrincipalAndLoanDebt() throws Exception {
        User u = user();
        Account cash = accounts.openCash(u.id(), SAVINGS), frozen = accounts.openCash(u.id(), CURRENT);
        transactions.deposit(u.id(), cash.number(), "500");
        transactions.deposit(u.id(), frozen.number(), "50");
        transactions.openDeposit(u.id(), cash.number(), "100", 1);
        transactions.openLoan(u.id(), "120", 6);
        update("UPDATE accounts SET status='FROZEN' WHERE account_id=?", frozen.id());
        var portfolio = accounts.portfolio(u.id());
        assertEquals(new BigDecimal("400.00"), portfolio.availableCash());
        assertEquals(new BigDecimal("50.00"), portfolio.unavailableCash());
        assertEquals(new BigDecimal("100.00"), portfolio.termDeposits());
        assertEquals(new BigDecimal("120.00"), portfolio.debt());
    }

    @Test
    void repaymentsAndSettlementCannotUseAnotherCustomersAccounts() throws Exception {
        User u = user(), other = user();
        Account cash = accounts.openCash(u.id(), SAVINGS), foreignCash = accounts.openCash(other.id(), CURRENT);
        transactions.deposit(u.id(), cash.number(), "500");
        var foreignLoan = transactions.openLoan(other.id(), "100", 6);
        var deposit = transactions.openDeposit(u.id(), cash.number(), "100", 1);
        assertThrows(BankingException.class, () -> transactions.repayLoan(u.id(), cash.number(), foreignLoan.number(), "20"));
        configure(Clock.offset(clock, Duration.ofDays(31)));
        assertThrows(BankingException.class, () -> transactions.settleDeposit(u.id(), deposit.number(), foreignCash.number()));
        assertEquals(new BigDecimal("400.00"), balance(u, cash));
        assertEquals(new BigDecimal("100.00"), accounts.balance(u.id(), deposit.number()).balance());
        assertEquals(new BigDecimal("100.00"), accounts.balance(other.id(), foreignLoan.number()).balance());
    }

    private User user() throws SQLException {
        String username = "demo_" + sequence.incrementAndGet();
        return new User(auth.register("Demo Person", username + "@example.test", "",
                username, PASSWORD.toCharArray()), username);
    }

    @Test
    void schemaRerunRepairsLegacyConstraintsWithoutResettingData() throws Exception {
        User u = user();
        Object customersBefore = scalar("SELECT count(*) FROM customers");
        Object balancesBefore = scalar("SELECT COALESCE(sum(balance),0) FROM accounts");
        update("ALTER TABLE transaction_audit DROP CONSTRAINT IF EXISTS transaction_audit_outcome_check");
        update("ALTER TABLE loan_details ALTER COLUMN account_id ADD GENERATED ALWAYS AS IDENTITY");
        update("ALTER TABLE deposit_details ALTER COLUMN account_id ADD GENERATED ALWAYS AS IDENTITY");
        for (int i = 0; i < 2; i++) {
            try (Connection c = database.open(); Statement s = c.createStatement()) {
                s.execute(Files.readString(Path.of("sql/postgres/schema.sql")));
            }
        }
        assertEquals(customersBefore, scalar("SELECT count(*) FROM customers"));
        assertEquals(balancesBefore, scalar("SELECT COALESCE(sum(balance),0) FROM accounts"));
        assertEquals(0L, scalar("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name IN ('loan_details','deposit_details') AND column_name='account_id' AND is_identity='YES'"));
        SQLException invalid = assertThrows(SQLException.class, () -> update(
                "INSERT INTO transaction_audit(customer_id,event_type,outcome) VALUES(?, 'DEPOSIT', 'INVALID')", u.id()));
        assertEquals("23514", invalid.getSQLState());
    }

    @Test
    void optionalDemoDataCanBeLoadedTwiceWithoutResettingBalances() throws Exception {
        long owner = auth.register("Avery Demo", "seed_avery@example.test", "", "demo_avery", PASSWORD.toCharArray());
        auth.register("Jordan Demo", "seed_jordan@example.test", "", "demo_jordan", PASSWORD.toCharArray());
        try (Connection c = database.open(); Statement s = c.createStatement()) {
            s.execute(Files.readString(Path.of("sql/postgres/data.sql")));
        }
        assertEquals(4, accounts.list(owner).size());
        transactions.deposit(owner, "DEMO1001", "10");
        long rows = ((Number) scalar("SELECT count(*) FROM transactions")).longValue();
        try (Connection c = database.open(); Statement s = c.createStatement()) {
            s.execute(Files.readString(Path.of("sql/postgres/data.sql")));
        }
        assertEquals(4, accounts.list(owner).size());
        assertEquals(new BigDecimal("910.00"), accounts.balance(owner, "DEMO1001").balance());
        assertEquals(rows, ((Number) scalar("SELECT count(*) FROM transactions")).longValue());
    }

    @Test
    void cliInvalidInputBackAndPasswordMismatchDoNotCreatePartialCustomers() throws Exception {
        long before = ((Number) scalar("SELECT count(*) FROM customers")).longValue();
        String script = String.join("\n", "bad-choice", "1", "0", "2", "0",
                "1", "CLI Test", "cancel@example.test", "", "cli_mismatch", PASSWORD, "Different!Password12",
                "1", "CLI Test", "bad-email", "", "cli_invalid", PASSWORD, PASSWORD,
                "2", "unknown_cli_user", "wrong-password", "3", "");
        java.io.InputStream originalIn = System.in;
        java.io.PrintStream originalOut = System.out;
        java.io.ByteArrayOutputStream transcript = new java.io.ByteArrayOutputStream();
        try {
            System.setIn(new java.io.ByteArrayInputStream(script.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(new java.io.PrintStream(transcript, true, java.nio.charset.StandardCharsets.UTF_8));
            new com.revature.banking.app.BankingApplication(auth, accounts, transactions, CONFIG, clock).run();
        } finally { System.setIn(originalIn); System.setOut(originalOut); }
        String text = transcript.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.contains("Passwords do not match"), text);
        assertTrue(text.contains("Enter a valid email"), text);
        assertTrue(text.contains("Invalid username or password"), text);
        assertTrue(text.contains("Goodbye"), text);
        assertEquals(before, ((Number) scalar("SELECT count(*) FROM customers")).longValue());
    }

    private BigDecimal balance(User user, Account a) throws SQLException {
        return accounts.balance(user.id(), a.number()).balance();
    }

    private static <T> List<T> concurrently(Callable<T> left, Callable<T> right) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            Future<T> a = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return left.call();
            });
            Future<T> b = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return right.call();
            });
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private static Object scalar(String sql, Object... params) throws SQLException {
        try (Connection c = database.open(); PreparedStatement s = prepare(c, sql, params);
             ResultSet r = s.executeQuery()) {
            assertTrue(r.next());
            return r.getObject(1);
        }
    }

    private static void update(String sql, Object... params) throws SQLException {
        try (Connection c = database.open(); PreparedStatement s = prepare(c, sql, params)) {
            s.executeUpdate();
        }
    }

    private static PreparedStatement prepare(Connection c, String sql, Object[] params) throws SQLException {
        PreparedStatement s = c.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) s.setObject(i + 1, params[i]);
        return s;
    }
}
