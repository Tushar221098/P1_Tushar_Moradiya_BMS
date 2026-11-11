package com.revature.banking.dao.jdbc;

import com.revature.banking.model.*;

import com.revature.banking.dao.AccountDAO;
import com.revature.banking.model.Account;
import com.revature.banking.model.AccountType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

public final class JdbcAccountDAO implements AccountDAO {
    private static final String SELECT = """
            SELECT a.account_id, a.customer_id, a.account_number, a.account_type,
                   a.balance, a.status, d.maturity_date
            FROM accounts a LEFT JOIN deposit_details d ON d.account_id=a.account_id
            """;

    @Override
    public Account create(Connection c, long owner, String number, AccountType type, BigDecimal balance)
            throws SQLException {
        String sql = """
                INSERT INTO accounts(customer_id,account_number,account_type,balance)
                VALUES(?,?,?,?) RETURNING account_id
                """;
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setLong(1, owner);
            statement.setString(2, number);
            statement.setString(3, type.name());
            statement.setBigDecimal(4, balance);
            try (ResultSet r = statement.executeQuery()) {
                if (r.next()) {
                    return Account.restore(r.getLong(1), owner, number, type, balance,
                            AccountStatus.ACTIVE, null);
                }
                throw new SQLException("Account insert returned no ID.");
            }
        }
    }

    @Override
    public Account owned(Connection c, long owner, String number) throws SQLException {
        String sql = SELECT + "WHERE a.customer_id=? AND a.account_number=?";
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setLong(1, owner);
            statement.setString(2, number);
            try (ResultSet r = statement.executeQuery()) {
                if (r.next()) {
                    return map(r);
                }
                return null;
            }
        }
    }

    @Override
    public List<Account> list(Connection c, long owner) throws SQLException {
        List<Account> results = new ArrayList<>();
        String sql = SELECT + "WHERE a.customer_id=? ORDER BY a.account_id";
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setLong(1, owner);
            try (ResultSet r = statement.executeQuery()) {
                while (r.next()) {
                    results.add(map(r));
                }
            }
        }
        return results;
    }

    @Override
    public Long idByNumber(Connection c, String number) throws SQLException {
        String sql = "SELECT account_id FROM accounts WHERE account_number=?";
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setString(1, number);
            try (ResultSet r = statement.executeQuery()) {
                if (r.next()) {
                    return r.getLong(1);
                }
                return null;
            }
        }
    }

    @Override
    public Account lock(Connection c, long id) throws SQLException {
        String sql = SELECT + "WHERE a.account_id=? FOR UPDATE OF a";
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
    public void update(Connection c, Account account, BigDecimal balance, AccountStatus status)
            throws SQLException {
        String sql = """
                UPDATE accounts SET balance=?, status=?, updated_at=CURRENT_TIMESTAMP WHERE account_id=?
                """;
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setBigDecimal(1, balance);
            statement.setString(2, status.name());
            statement.setLong(3, account.id());
            statement.executeUpdate();
        }
    }

    @Override
    public void addDepositTerms(Connection c, long id, BigDecimal principal, int months, LocalDate maturity)
            throws SQLException {
        String sql = "INSERT INTO deposit_details(account_id,principal,term_months,maturity_date) VALUES(?,?,?,?)";
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setLong(1, id);
            statement.setBigDecimal(2, principal);
            statement.setInt(3, months);
            statement.setObject(4, maturity);
            statement.executeUpdate();
        }
    }

    @Override
    public void addLoanTerms(Connection c, long id, BigDecimal principal, int months, LocalDate start)
            throws SQLException {
        String sql = "INSERT INTO loan_details(account_id,principal_amount,tenure_months,start_date) VALUES(?,?,?,?)";
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setLong(1, id);
            statement.setBigDecimal(2, principal);
            statement.setInt(3, months);
            statement.setObject(4, start);
            statement.executeUpdate();
        }
    }

    @Override
    public void matureDeposits(Connection c, long owner, LocalDate today) throws SQLException {
        String sql = """
                UPDATE accounts a SET status='MATURED', updated_at=CURRENT_TIMESTAMP
                FROM deposit_details d WHERE d.account_id=a.account_id AND d.maturity_date<=?
                    AND a.customer_id=? AND a.account_type='DEPOSIT' AND a.status='ACTIVE'
                """;
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setObject(1, today);
            statement.setLong(2, owner);
            statement.executeUpdate();
        }
    }

    private static Account map(ResultSet r) throws SQLException {
        return Account.restore(r.getLong(1), r.getLong(2), r.getString(3),
                AccountType.valueOf(r.getString(4)), r.getBigDecimal(5),
                AccountStatus.valueOf(r.getString(6)), r.getObject(7, LocalDate.class));
    }
}
