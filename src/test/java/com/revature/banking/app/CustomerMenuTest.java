package com.revature.banking.app;

import com.revature.banking.model.*;

import com.revature.banking.model.Account;
import com.revature.banking.model.AccountType;
import com.revature.banking.service.AccountService;
import com.revature.banking.dao.AccountDAO;
import com.revature.banking.dao.TransactionDAO;
import com.revature.banking.dao.AuditDAO;
import com.revature.banking.util.JdbcTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;

import com.revature.banking.service.TransactionService;
import com.revature.banking.util.AppConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerMenuTest {
    @Test
    void numberedChoicesSelectAccountsForMoneyActions() throws Exception {
        Account cash = Account.restore(1, 7, "123456789012", AccountType.SAVINGS,
                new BigDecimal("500.00"), AccountStatus.ACTIVE, null);
        Account loan = Account.restore(2, 7, "123456789013", AccountType.LOAN,
                new BigDecimal("200.00"), AccountStatus.ACTIVE, null);
        Account deposit = Account.restore(3, 7, "123456789014", AccountType.DEPOSIT,
                new BigDecimal("100.00"), AccountStatus.MATURED, LocalDate.of(2020, 1, 1));
        Fixture fixture = new Fixture();
        when(fixture.dao.list(fixture.connection, 7)).thenReturn(List.of(cash, loan, deposit));
        for (Account account : List.of(cash, loan, deposit)) {
            when(fixture.dao.owned(fixture.connection, 7, account.number())).thenReturn(account);
            when(fixture.dao.lock(fixture.connection, account.id())).thenReturn(account);
        }
        Account recipient = Account.restore(4, 9, "999999999999", AccountType.CURRENT,
                BigDecimal.ZERO, AccountStatus.ACTIVE, null);
        when(fixture.dao.idByNumber(fixture.connection, recipient.number())).thenReturn(4L);
        when(fixture.dao.lock(fixture.connection, 4)).thenReturn(recipient);
        // Every selection is a short menu index, including the transfer source.
        String script = "5\n1\n20\n6\n1\n10\n7\n1\n999999999999\n5\n"
                + "8\n1\n1\n30\n9\n12\n1\n1\n0\n";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleInput input = new ConsoleInput(null, new ByteArrayInputStream(script.getBytes()),
                new PrintStream(output), Clock.systemUTC(), Duration.ofMinutes(2));
        CustomerMenu menu = new CustomerMenu(null, fixture.accounts, fixture.transactions,
                AppConfig.load(), input);

        assertThrows(ConsoleInput.Back.class, () -> menu.show(7));
        for (String event : List.of("DEPOSIT", "WITHDRAWAL", "TRANSFER", "LOAN_REPAYMENT", "SETTLE_DEPOSIT")) {
            verify(fixture.audit).transaction(eq(fixture.connection), eq(7L), anyString(),
                    eq(event), eq("SUCCESS"), isNull());
        }
        assertFalse(output.toString().contains("database operation could not"));
        assertTrue(output.toString().contains("CHOICE"));
        assertFalse(output.toString().contains("11. Logout"));
    }

    @Test
    void noEligibleAccountsSkipsTheTransaction() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.dao.list(fixture.connection, 7)).thenReturn(List.of());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleInput input = new ConsoleInput(null, new ByteArrayInputStream("5\n0\n".getBytes()),
                new PrintStream(output), Clock.systemUTC(), Duration.ofMinutes(2));
        CustomerMenu menu = new CustomerMenu(null, fixture.accounts, fixture.transactions,
                AppConfig.load(), input);
        assertThrows(ConsoleInput.Back.class, () -> menu.show(7));
        verifyNoInteractions(fixture.audit);
        assertTrue(output.toString().contains("Create an active Savings or Current account first."));
    }

    @Test
    void allHistoryFilterIncludesEveryCompletedTransactionType() throws Exception {
        Fixture fixture = new Fixture();
        Account cash = Account.restore(1, 7, "123456789012", AccountType.SAVINGS,
                BigDecimal.TEN, AccountStatus.ACTIVE, null);
        when(fixture.dao.list(fixture.connection, 7)).thenReturn(List.of(cash));
        when(fixture.dao.owned(fixture.connection, 7, cash.number())).thenReturn(cash);
        ConsoleInput input = new ConsoleInput(null,
                new ByteArrayInputStream("10\n1\nALL\n\n\n10\n0\n".getBytes()),
                new PrintStream(new ByteArrayOutputStream()), Clock.systemUTC(), Duration.ofMinutes(2));
        CustomerMenu menu = new CustomerMenu(null, fixture.accounts, fixture.transactions, AppConfig.load(), input);
        assertThrows(ConsoleInput.Back.class, () -> menu.show(7));
        verify(fixture.ledger).history(fixture.connection, 1, null, null, null, null, 11);
    }

    @Test
    void rejectedLoanOverpaymentShowsItsAuditReference() throws Exception {
        Fixture fixture = new Fixture();
        Account cash = Account.restore(1, 7, "123456789012", AccountType.SAVINGS,
                new BigDecimal("500.00"), AccountStatus.ACTIVE, null);
        Account loan = Account.restore(2, 7, "123456789013", AccountType.LOAN,
                new BigDecimal("100.00"), AccountStatus.ACTIVE, null);
        when(fixture.dao.list(fixture.connection, 7)).thenReturn(List.of(cash, loan));
        for (Account a : List.of(cash, loan)) {
            when(fixture.dao.owned(fixture.connection, 7, a.number())).thenReturn(a);
            when(fixture.dao.lock(fixture.connection, a.id())).thenReturn(a);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleInput input = new ConsoleInput(null,
                new ByteArrayInputStream("8\n1\n1\n200\n0\n".getBytes()),
                new PrintStream(output), Clock.systemUTC(), Duration.ofMinutes(2));
        CustomerMenu menu = new CustomerMenu(null, fixture.accounts, fixture.transactions, AppConfig.load(), input);
        assertThrows(ConsoleInput.Back.class, () -> menu.show(7));
        verify(fixture.audit).transaction(eq(fixture.connection), eq(7L), anyString(),
                eq("LOAN_REPAYMENT"), eq("REJECTED"), eq("LOAN_OVERPAYMENT"));
        verifyNoInteractions(fixture.ledger);
        assertTrue(output.toString().contains("Reference: TX-"));
    }

    @Test
    void unmaturedDepositShowsMaturityDateWithoutAttemptingPayout() throws Exception {
        Fixture fixture = new Fixture();
        Account deposit = Account.restore(3, 7, "123456789014", AccountType.DEPOSIT,
                new BigDecimal("100.00"), AccountStatus.ACTIVE, LocalDate.of(2099, 1, 1));
        when(fixture.dao.list(fixture.connection, 7)).thenReturn(List.of(deposit));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleInput input = new ConsoleInput(null, new ByteArrayInputStream("12\n0\n".getBytes()),
                new PrintStream(output), Clock.systemUTC(), Duration.ofMinutes(2));
        CustomerMenu menu = new CustomerMenu(null, fixture.accounts, fixture.transactions, AppConfig.load(), input);
        assertThrows(ConsoleInput.Back.class, () -> menu.show(7));
        assertTrue(output.toString().contains("2099-01-01"));
        assertTrue(output.toString().contains("No deposits ready for payout"));
        verifyNoInteractions(fixture.audit, fixture.ledger);
    }

    private static class Fixture {
        final Connection connection = mock(Connection.class);
        final AccountDAO dao = mock(AccountDAO.class);
        final AuditDAO audit = mock(AuditDAO.class);
        final TransactionDAO ledger = mock(TransactionDAO.class);
        final AccountService accounts;
        final TransactionService transactions;

        Fixture() throws Exception {
            when(connection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
            JdbcTransaction tx = new JdbcTransaction(() -> connection, 5);
            Clock clock = Clock.systemUTC();
            accounts = new AccountService(dao, tx, clock);
            transactions = new TransactionService(dao, ledger, audit,
                    tx, AppConfig.load(), clock);
        }
    }

}
