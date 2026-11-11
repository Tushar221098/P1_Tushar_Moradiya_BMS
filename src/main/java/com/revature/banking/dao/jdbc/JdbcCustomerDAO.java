package com.revature.banking.dao.jdbc;

import com.revature.banking.model.*;

import com.revature.banking.dao.CustomerDAO;
import com.revature.banking.model.Customer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

public final class JdbcCustomerDAO implements CustomerDAO {
    private static final String SELECT = """
            SELECT customer_id, full_name, email, phone, username, password_hash,
                   status, failed_login_count, locked_until FROM customers
            """;

    @Override
    public long insert(Connection c, String name, String email, String phone,
                       String username, String hash) throws SQLException {
        String sql = """
                INSERT INTO customers (full_name,email,phone,username,password_hash)
                VALUES (?,?,?,?,?) RETURNING customer_id
                """;
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, phone);
            statement.setString(4, username);
            statement.setString(5, hash);
            try (ResultSet r = statement.executeQuery()) {
                if (r.next()) {
                    return r.getLong(1);
                }
                throw new SQLException("Customer insert returned no ID.");
            }
        }
    }

    @Override
    public Customer lockByUsername(Connection c, String username) throws SQLException {
        String sql = SELECT + "WHERE username = ? FOR UPDATE";
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setString(1, username);
            try (ResultSet r = statement.executeQuery()) {
                if (r.next()) {
                    return map(r);
                }
                return null;
            }
        }
    }

    @Override
    public Customer lockById(Connection c, long id) throws SQLException {
        String sql = SELECT + "WHERE customer_id = ? FOR UPDATE";
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setLong(1, id);
            try (ResultSet r = statement.executeQuery()) {
                if (r.next()) {
                    return map(r);
                }
                return null;
            }
        }
    }

    @Override
    public Customer.Profile profile(Connection c, long id) throws SQLException {
        String sql = """
                SELECT customer_id, full_name, email, phone, username FROM customers
                WHERE customer_id = ?
                """;
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setLong(1, id);
            try (ResultSet r = statement.executeQuery()) {
                if (r.next()) {
                    return new Customer.Profile(r.getLong(1), r.getString(2), r.getString(3),
                            r.getString(4), r.getString(5));
                }
                return null;
            }
        }
    }

    @Override
    public void authenticationState(Connection c, long id, int failures, OffsetDateTime until,
                                    CustomerStatus status) throws SQLException {
        String sql = """
                UPDATE customers SET failed_login_count=?, locked_until=?, status=?,
                updated_at=CURRENT_TIMESTAMP WHERE customer_id=?
                """;
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setInt(1, failures);
            statement.setObject(2, until);
            statement.setString(3, status.name());
            statement.setLong(4, id);
            statement.executeUpdate();
        }
    }

    @Override
    public void password(Connection c, long id, String hash) throws SQLException {
        String sql = """
                UPDATE customers SET password_hash=?, updated_at=CURRENT_TIMESTAMP WHERE customer_id=?
                """;
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setString(1, hash);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    @Override
    public void updateProfile(Connection c, long id, String name, String email, String phone) throws SQLException {
        try (PreparedStatement statement = JdbcSettings.prepare(c, "UPDATE customers SET full_name=?,email=?,phone=?,updated_at=CURRENT_TIMESTAMP WHERE customer_id=?")) {
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, phone);
            statement.setLong(4, id);
            statement.executeUpdate();
        }
    }

    private static Customer map(java.sql.ResultSet r) throws SQLException {
        return new Customer(r.getLong("customer_id"), r.getString("full_name"), r.getString("email"),
                r.getString("phone"), r.getString("username"), r.getString("password_hash"),
                CustomerStatus.valueOf(r.getString("status")), r.getInt("failed_login_count"),
                r.getObject("locked_until", OffsetDateTime.class));
    }
}
