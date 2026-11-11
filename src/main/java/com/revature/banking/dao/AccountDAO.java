package com.revature.banking.dao;

import com.revature.banking.model.*;

import com.revature.banking.model.Account;
import com.revature.banking.model.AccountType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public interface AccountDAO {
    Account create(Connection c, long owner, String number, AccountType type, BigDecimal balance)
            throws SQLException;

    Account owned(Connection c, long owner, String number) throws SQLException;

    List<Account> list(Connection c, long owner) throws SQLException;

    Long idByNumber(Connection c, String number) throws SQLException;

    Account lock(Connection c, long id) throws SQLException;

    void update(Connection c, Account account, BigDecimal balance, AccountStatus status)
            throws SQLException;

    void addDepositTerms(Connection c, long id, BigDecimal principal, int months, LocalDate maturity)
            throws SQLException;

    void addLoanTerms(Connection c, long id, BigDecimal principal, int months, LocalDate start)
            throws SQLException;

    void matureDeposits(Connection c, long owner, LocalDate today) throws SQLException;
}
