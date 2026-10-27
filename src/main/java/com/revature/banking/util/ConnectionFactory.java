package com.revature.banking.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionFactory {

    public static Connection getConnection() throws SQLException {
        String url = System.getenv("DB_URL");
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");

        if (url == null || url.isBlank()) {
            throw new IllegalStateException("DB_URL is missing");
        }

        if (username == null || username.isBlank()) {
            throw new IllegalStateException("DB_USERNAME is missing");
        }

        if (password == null || password.isEmpty()) {
            throw new IllegalStateException("DB_PASSWORD is missing");
        }

        return DriverManager.getConnection(url, username, password);
    }
}