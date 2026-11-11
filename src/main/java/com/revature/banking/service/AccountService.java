package com.revature.banking.service;

import com.revature.banking.model.*;

import com.revature.banking.exception.BankingException;

import com.revature.banking.dao.AccountDAO;
import com.revature.banking.model.Account;
import com.revature.banking.model.AccountType;
import com.revature.banking.util.JdbcTransaction;
import com.revature.banking.util.ReferenceGenerator;
import com.revature.banking.util.IdentifierGenerator;
import com.revature.banking.model.policy.AccountEligibilityPolicy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

public final class AccountService {
    static final BigDecimal ZERO = new BigDecimal("0.00");
    private final IdentifierGenerator identifiers;
    private final AccountEligibilityPolicy eligibility = new AccountEligibilityPolicy();
    private final AccountDAO dao;
    private final JdbcTransaction tx;
    private final Clock clock;

    public AccountService(AccountDAO dao, JdbcTransaction tx, Clock clock) {
        this(dao, tx, clock, new ReferenceGenerator());
    }

    public AccountService(AccountDAO dao, JdbcTransaction tx, Clock clock, IdentifierGenerator identifiers) {
        this.identifiers = identifiers;
        this.dao = dao;
        this.tx = tx;
        this.clock = clock;
    }

    public Account openCash(long owner, AccountType type) throws SQLException {
        if (!type.spendable()) throw BankingException.of("INVALID_TYPE", "Choose Savings or Current.");
        String number = identifiers.nextAccountNumber();
        try (Connection connection = tx.open()) {
            return dao.create(connection, owner, number, type, ZERO);
        }
    }

    public List<Account> list(long owner) throws SQLException {
        try (Connection c = tx.open()) {
            dao.matureDeposits(c, owner, LocalDate.now(clock));
            return dao.list(c, owner);
        }
    }

    public List<Account> choices(long owner, AccountUse use, BigDecimal minimumDeposit) throws SQLException {
        List<Account> all = list(owner);
        List<Account> result = all.stream().filter(a -> eligibility.eligible(a, use, today(), new Money(minimumDeposit))).toList();
        if (result.isEmpty()) {
            String message = switch (use) {
                case ALL -> "You have no accounts yet.";
                case CASH -> "Create an active Savings or Current account first.";
                case DEPOSIT_FUNDING -> "Add at least " + minimumDeposit + " to a Savings or Current account first.";
                case REPAYMENT -> "No active loans with an outstanding balance.";
                case SETTLEMENT -> {
                    String maturity = all.stream().filter(a -> a.type() == AccountType.DEPOSIT && a.maturityDate() != null
                            && a.maturityDate().isAfter(today())).map(a -> a.maturityDate().toString()).sorted().findFirst().orElse(null);
                    yield maturity == null ? "No deposits ready for payout." : "No deposits ready for payout. Next maturity: " + maturity;
                }
            };
            throw BankingException.of("NO_ACCOUNTS", message);
        }
        return result;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public Account balance(long owner, String number) throws SQLException {
        try (Connection c = tx.open()) {
            dao.matureDeposits(c, owner, LocalDate.now(clock));
            Account account = dao.owned(c, owner, number);
            if (account == null) throw BankingException.of("ACCOUNT_UNAVAILABLE", "Account unavailable.");
            return account;
        }
    }

    public static final class Portfolio {
        private final BigDecimal availableCash;
        private final BigDecimal termDeposits;
        private final BigDecimal debt;
        private final BigDecimal unavailableCash;

        public Portfolio(BigDecimal availableCash, BigDecimal termDeposits, BigDecimal debt, BigDecimal unavailableCash) {
            this.availableCash = availableCash;
            this.termDeposits = termDeposits;
            this.debt = debt;
            this.unavailableCash = unavailableCash;
        }

        public BigDecimal availableCash() {
            return availableCash;
        }

        public BigDecimal termDeposits() {
            return termDeposits;
        }

        public BigDecimal debt() {
            return debt;
        }

        public BigDecimal unavailableCash() {
            return unavailableCash;
        }
    }

    public Portfolio portfolio(long owner) throws SQLException {
        BigDecimal cash = ZERO, deposits = ZERO, debt = ZERO, unavailable = ZERO;
        for (Account a : list(owner)) {
            if (a.type().spendable()) {
                if (a.status() == AccountStatus.ACTIVE) cash = cash.add(a.balance());
                else unavailable = unavailable.add(a.balance());
            } else if (a.type() == AccountType.DEPOSIT) {
                deposits = deposits.add(a.balance());
            } else {
                debt = debt.add(a.balance());
            }
        }
        return new Portfolio(cash, deposits, debt, unavailable);
    }
}
