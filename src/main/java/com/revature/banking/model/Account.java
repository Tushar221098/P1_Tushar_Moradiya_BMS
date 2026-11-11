package com.revature.banking.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.revature.banking.exception.*;

public abstract class Account {
    private final long id;
    private final long ownerId;
    private final AccountNumber number;
    private final Money balance;
    private final AccountStatus status;
    private final LocalDate maturityDate;

    protected Account(long id, long ownerId, String number, BigDecimal balance, AccountStatus status, LocalDate maturityDate) {
        if (id < 1 || ownerId < 1 || status == null)
            throw new IllegalArgumentException("Invalid account identity or status");
        this.id = id;
        this.ownerId = ownerId;
        this.number = AccountNumber.persisted(number);
        this.balance = new Money(balance);
        this.status = status;
        this.maturityDate = maturityDate;
    }

    public static Account restore(long id, long owner, String number, AccountType type, BigDecimal balance, AccountStatus status, LocalDate maturity) {
        return switch (type) {
            case SAVINGS -> new SavingsAccount(id, owner, number, balance, status);
            case CURRENT -> new CurrentAccount(id, owner, number, balance, status);
            case DEPOSIT -> new DepositAccount(id, owner, number, balance, status, maturity);
            case LOAN -> new LoanAccount(id, owner, number, balance, status);
        };
    }

    public long id() {
        return id;
    }

    public long ownerId() {
        return ownerId;
    }

    public String number() {
        return number.value();
    }

    public abstract AccountType type();

    public BigDecimal balance() {
        return balance.value();
    }

    public Money money() {
        return balance;
    }

    public AccountStatus status() {
        return status;
    }

    public LocalDate maturityDate() {
        return maturityDate;
    }

    public boolean acceptsCashOperations() {
        return false;
    }

    public boolean canSettle(LocalDate today) {
        return false;
    }

    public boolean acceptsRepayment() {
        return false;
    }

    public void requireCash() {
        if (!acceptsCashOperations())
            throw new AccountNotEligibleException("INELIGIBLE_ACCOUNT", "Choose an active Savings or Current account.");
    }

    public Money debit(Money amount) {
        requireCash();
        amount.requirePositive();
        if (balance.compareTo(amount) < 0)
            throw new InsufficientFundsException("INSUFFICIENT_FUNDS", "Insufficient funds.");
        return balance.subtract(amount);
    }

    public Money credit(Money amount) {
        requireCash();
        return balance.add(amount.requirePositive());
    }

    public Money repay(Money amount) {
        throw new AccountNotEligibleException("INELIGIBLE_LOAN", "Choose an active loan.");
    }

    public Money settle(LocalDate today) {
        throw new AccountNotEligibleException("DEPOSIT_UNAVAILABLE", "Only a matured term deposit can be settled.");
    }

    public String toString() {
        return "Account[number=" + number + ", type=" + type() + ", status=" + status + "]";
    }
}
