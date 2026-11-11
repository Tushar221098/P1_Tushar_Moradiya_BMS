package com.revature.banking.dao.jdbc;

import com.revature.banking.dao.*;
import com.revature.banking.dao.AuditDAO.OperationStatus;

import java.sql.*;

public final class JdbcLoginAuditDAO implements LoginAuditDAO {
    @Override
    public void login(Connection c, Long id, String username, String outcome) throws SQLException {
        String sql = "INSERT INTO login_audit(customer_id,username_attempted,outcome) VALUES(?,?,?)";
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setObject(1, id);
            statement.setString(2, username);
            statement.setString(3, outcome);
            statement.executeUpdate();
        }
    }

}
