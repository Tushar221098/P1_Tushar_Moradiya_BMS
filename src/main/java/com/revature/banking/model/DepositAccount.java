package com.revature.banking.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class DepositAccount extends Account {
    public DepositAccount(long id, long owner, String number, BigDecimal balance, AccountStatus status, LocalDate maturity) {
        super(id, owner, number, balance, status, maturity);
    }

    public AccountType type() {
        return AccountType.DEPOSIT;
    }

    public boolean canSettle(LocalDate today) {
        return (status() == AccountStatus.ACTIVE || status() == AccountStatus.MATURED)
                && maturityDate() != null && !maturityDate().isAfter(today) && money().isPositive();
    }

    public Money settle(LocalDate today) {
        if (!canSettle(today)) return super.settle(today);
        return money();
    }
}
