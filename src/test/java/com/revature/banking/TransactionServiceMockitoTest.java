package com.revature.banking;

import com.revature.banking.model.*;

import com.revature.banking.dao.AccountDAO;
import com.revature.banking.dao.AuditDAO;
import com.revature.banking.dao.TransactionDAO;
import com.revature.banking.model.Account;
import com.revature.banking.model.AccountType;
import com.revature.banking.model.TransactionType;
import com.revature.banking.exception.BankingException;
import com.revature.banking.service.TransactionService;
import com.revature.banking.util.AppConfig;
import com.revature.banking.util.ConnectionFactory;
import com.revature.banking.util.JdbcTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionServiceMockitoTest {
    private static final long OWNER = 1;
    private Connection connection;
    private Connection auditConnection;
    private AccountDAO accounts;
    private TransactionDAO history;
    private AuditDAO audit;
    private TransactionService service;

    @BeforeEach
    void setUp() throws SQLException {
        // Mock the database boundary. The actual service and transaction code still run.
        connection = mock(Connection.class);
        auditConnection = mock(Connection.class);
        ConnectionFactory factory = mock(ConnectionFactory.class);
        when(factory.open()).thenReturn(connection, auditConnection);
        when(connection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
        when(auditConnection.prepareStatement(anyString())).thenReturn(mock(PreparedStatement.class));
        accounts = mock(AccountDAO.class);
        history = mock(TransactionDAO.class);
        audit = mock(AuditDAO.class);
        service = new TransactionService(accounts, history, audit, new JdbcTransaction(factory, 5),
                AppConfig.load(), Clock.systemUTC());
    }

    @Test
    void transferCommitsAfterBothBalancesAndHistoryAreWritten() throws SQLException {
        Account from = account(20, OWNER, "FROM", "100.00");
        Account to = account(10, 2, "TO", "20.00");
        prepareTransfer(from, to);

        String reference = service.transfer(OWNER, "FROM", "TO", "30");

        // Smaller ID is locked first, even when it is the receiving account.
        InOrder order = inOrder(accounts, history, audit, connection);
        order.verify(accounts).lock(connection, 10L);
        order.verify(accounts).lock(connection, 20L);
        order.verify(accounts).update(connection, from, money("70.00"), AccountStatus.ACTIVE);
        order.verify(accounts).update(connection, to, money("50.00"), AccountStatus.ACTIVE);
        order.verify(history).insert(connection, reference, TransactionType.TRANSFER, 20L, 10L,
                money("30.00"), money("70.00"), money("50.00"), null);
        order.verify(audit).transaction(connection, OWNER, reference, "TRANSFER", "SUCCESS", null);
        order.verify(connection).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void withdrawalAboveBalanceIsRejectedWithoutWritingHistory() throws SQLException {
        Account from = account(20, OWNER, "FROM", "100.00");
        when(accounts.owned(connection, OWNER, "FROM")).thenReturn(from);
        when(accounts.lock(connection, from.id())).thenReturn(from);

        BankingException error = assertThrows(BankingException.class,
                () -> service.withdraw(OWNER, "FROM", "150"));

        assertEquals("INSUFFICIENT_FUNDS", error.code());
        verify(accounts, never()).update(any(), any(), any(), any());
        verifyNoInteractions(history);
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test
    void historyWriteFailureRollsBackInsteadOfCommitting() throws SQLException {
        prepareTransfer(account(20, OWNER, "FROM", "100.00"), account(10, 2, "TO", "20.00"));
        doThrow(new SQLException("Simulated insert failure", "23514")).when(history)
                .insert(eq(connection), anyString(), eq(TransactionType.TRANSFER),
                        eq(20L), eq(10L), any(), any(), any(), isNull());

        assertThrows(JdbcTransaction.RolledBack.class, () -> service.transfer(OWNER, "FROM", "TO", "30"));

        verify(connection).rollback();
        verify(connection, never()).commit();
        verify(audit).transaction(eq(auditConnection), eq(OWNER), anyString(),
                eq("TRANSFER"), eq("ROLLED_BACK"), eq("DATABASE_ERROR"));
    }

    @Test
    void someoneElsesAccountCannotBeUsedAsTransferSource() throws SQLException {
        when(accounts.owned(connection, OWNER, "FROM")).thenReturn(null);

        BankingException error = assertThrows(BankingException.class,
                () -> service.transfer(OWNER, "FROM", "TO", "1"));

        assertEquals("ACCOUNT_UNAVAILABLE", error.code());
        verify(accounts, never()).lock(any(), anyLong());
        verifyNoInteractions(history);
        verify(connection, never()).commit();
    }

    private void prepareTransfer(Account from, Account to) throws SQLException {
        when(accounts.owned(connection, OWNER, from.number())).thenReturn(from);
        when(accounts.idByNumber(connection, to.number())).thenReturn(to.id());
        when(accounts.lock(connection, from.id())).thenReturn(from);
        when(accounts.lock(connection, to.id())).thenReturn(to);
    }

    @Test
    void deadlockRetriesTheWholeTransactionWithBackoffAndTheSameReference() throws SQLException {
        Account from = account(20, OWNER, "FROM", "100.00");
        Account to = account(10, 2, "TO", "20.00");
        prepareTransfer(from, to);
        doThrow(new SQLException("deadlock", "40P01")).doNothing().when(history)
                .insert(eq(connection), anyString(), any(), any(), any(), any(), any(), any(), any());
        var waits = new java.util.ArrayList<Integer>();
        var published = new java.util.ArrayList<com.revature.banking.service.event.DomainEvent>();
        String reference = "TX-00000000-0000-4000-8000-000000000001";
        var generator = new com.revature.banking.util.IdentifierGenerator() {
            public String nextAccountNumber() {
                return "123456789015";
            }

            public String nextTransactionReference() {
                return reference;
            }
        };
        TransactionService retrying = new TransactionService(accounts, history, audit,
                new JdbcTransaction(() -> connection, 5), AppConfig.load(), Clock.systemUTC(),
                generator, waits::add, (c, event) -> published.add(event));

        assertEquals(reference, retrying.transfer(OWNER, "FROM", "TO", "10"));
        assertEquals(java.util.List.of(1), waits);
        verify(connection).rollback();
        verify(connection).commit();
        verify(history, times(2)).insert(eq(connection), eq(reference), any(), any(), any(), any(), any(), any(), any());
        assertEquals(1, published.size());
    }

    private Account account(long id, long owner, String number, String balance) {
        return Account.restore(id, owner, number, AccountType.SAVINGS, money(balance), AccountStatus.ACTIVE, null);
    }

    private BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }
}
