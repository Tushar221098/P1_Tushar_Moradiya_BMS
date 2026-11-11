package com.revature.banking.dao;

import java.sql.Connection;
import java.sql.SQLException;

public interface AuditDAO extends LoginAuditDAO, TransactionAuditDAO {

    public static final class OperationStatus {
        private final String event;
        private final String outcome;
        private final String failureCode;
        private final java.time.OffsetDateTime time;

        public OperationStatus(String event, String outcome, String failureCode, java.time.OffsetDateTime time) {
            this.event = event;
            this.outcome = outcome;
            this.failureCode = failureCode;
            this.time = time;
        }

        public String event() {
            return event;
        }

        public String outcome() {
            return outcome;
        }

        public String failureCode() {
            return failureCode;
        }

        public java.time.OffsetDateTime time() {
            return time;
        }
    }

    void login(Connection c, Long id, String username, String outcome) throws SQLException;

    void transaction(Connection c, long id, String ref, String event, String outcome,
                     String code) throws SQLException;

    OperationStatus operationStatus(Connection c, long owner, String ref) throws SQLException;
}
