package com.revature.banking.dao;

import java.sql.Connection;
import java.sql.SQLException;

public interface LoginAuditDAO {
    void login(Connection c, Long id, String username, String outcome) throws SQLException;
}
