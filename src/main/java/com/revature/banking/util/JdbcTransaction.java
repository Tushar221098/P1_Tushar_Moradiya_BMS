package com.revature.banking.util;

import java.sql.Connection;

import com.revature.banking.dao.jdbc.JdbcSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

public class JdbcTransaction {
    private static final Logger LOG = LoggerFactory.getLogger(JdbcTransaction.class);
    private final ConnectionFactory factory;
    private final int timeoutSeconds;

    public JdbcTransaction(ConnectionFactory factory, int timeoutSeconds) {
        this.factory = factory;
        this.timeoutSeconds = timeoutSeconds;
    }

    public Connection open() throws SQLException {
        Connection c = factory.open();
        try {
            JdbcSettings.configure(c, timeoutSeconds);
            return c;
        } catch (SQLException e) {
            close(c);
            throw e;
        }
    }

    public Connection begin() throws SQLException {
        Connection connection = open();
        try {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            return connection;
        } catch (SQLException e) {
            close(connection);
            throw e;
        }
    }

    public void commit(Connection connection) throws SQLException {
        try {
            connection.commit();
        } catch (SQLException e) {
            // The database might have committed before the connection was lost.
            // Retrying at this point could apply the same operation twice.
            throw new CommitUnconfirmed(e);
        }
    }

    public boolean rollback(Connection connection, Exception originalError) {
        try {
            connection.rollback();
            return true;
        } catch (SQLException rollbackError) {
            originalError.addSuppressed(rollbackError);
            return false;
        }
    }

    public void close(Connection connection) {
        try {
            connection.close();
        } catch (SQLException e) {
            // A close failure after a successful commit must not be reported as a failed transfer.
            SafeLog.database(LOG, "connection_close", null, e);
        }
    }

    public static class RolledBack extends SQLException {
        public RolledBack(SQLException cause) {
            super("Transaction rolled back", cause.getSQLState(), cause);
        }
    }

    public static class CommitUnconfirmed extends SQLException {
        public CommitUnconfirmed(SQLException cause) {
            super("Commit could not be confirmed", cause.getSQLState(), cause);
        }
    }
}
