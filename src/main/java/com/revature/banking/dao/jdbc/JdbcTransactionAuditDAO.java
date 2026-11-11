package com.revature.banking.dao.jdbc;

import com.revature.banking.dao.*;
import com.revature.banking.dao.AuditDAO.OperationStatus;

import java.sql.*;

public final class JdbcTransactionAuditDAO implements TransactionAuditDAO {
    @Override
    public void transaction(Connection c, long id, String ref, String event, String outcome,
                            String code) throws SQLException {
        String sql = """
                INSERT INTO transaction_audit(customer_id,reference_number,event_type,outcome,failure_code)
                VALUES(?,?,?,?,?)
                """;
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setLong(1, id);
            statement.setString(2, ref);
            statement.setString(3, event);
            statement.setString(4, outcome);
            statement.setString(5, code);
            statement.executeUpdate();
        }
    }

    @Override
    public OperationStatus operationStatus(Connection c, long owner, String ref) throws SQLException {
        String sql = """
                SELECT event_type,outcome,failure_code,created_at FROM transaction_audit
                WHERE customer_id=? AND reference_number=? ORDER BY audit_id DESC LIMIT 1
                """;
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setLong(1, owner);
            statement.setString(2, ref);
            try (ResultSet r = statement.executeQuery()) {
                if (r.next()) {
                    return new OperationStatus(r.getString(1), r.getString(2), r.getString(3),
                            r.getObject(4, java.time.OffsetDateTime.class));
                }
                return null;
            }
        }
    }
}
