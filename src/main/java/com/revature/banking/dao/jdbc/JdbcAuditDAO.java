package com.revature.banking.dao.jdbc;

import com.revature.banking.dao.*;

import java.sql.*;

/**
 * Composition adapter for applications which need both audit repositories.
 */
public final class JdbcAuditDAO implements AuditDAO {
    private final LoginAuditDAO logins = new JdbcLoginAuditDAO();
    private final TransactionAuditDAO transactions = new JdbcTransactionAuditDAO();

    public void login(Connection c, Long id, String username, String outcome) throws SQLException {
        logins.login(c, id, username, outcome);
    }

    public void transaction(Connection c, long id, String ref, String event, String outcome, String code) throws SQLException {
        transactions.transaction(c, id, ref, event, outcome, code);
    }

    public OperationStatus operationStatus(Connection c, long owner, String reference) throws SQLException {
        return transactions.operationStatus(c, owner, reference);
    }
}
