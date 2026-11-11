package com.revature.banking.dao;

import java.sql.Connection;
import java.sql.SQLException;

public interface TransactionAuditDAO {
    void transaction(Connection c, long id, String reference, String event, String outcome, String failureCode) throws SQLException;

    AuditDAO.OperationStatus operationStatus(Connection c, long owner, String reference) throws SQLException;
}
