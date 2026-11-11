package com.revature.banking.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

@FunctionalInterface
public interface ConnectionFactory {
    Connection open() throws SQLException;

    static ConnectionFactory fromEnvironment() {
        String url = System.getenv("DB_URL");
        if (url == null || url.isBlank()) {
            url = "jdbc:postgresql://localhost:5432/Banking_CLI";
        }
        Properties props = new Properties();
        props.setProperty("user", required("DB_USERNAME"));
        props.setProperty("password", required("DB_PASSWORD"));
        props.setProperty("connectTimeout", "5");
        String databaseUrl = url;
        return new ConnectionFactory() {
            @Override
            public Connection open() throws SQLException {
                return DriverManager.getConnection(databaseUrl, props);
            }
        };
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " is missing.");
        return value;
    }
}
