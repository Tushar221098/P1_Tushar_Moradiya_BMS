package com.revature.banking.model;

import java.math.BigDecimal;

public final class SavingsAccount extends Account {
    public SavingsAccount(long id, long owner, String number, BigDecimal balance, AccountStatus status) {
        super(id, owner, number, balance, status, null);
    }

    public AccountType type() {
        return AccountType.SAVINGS;
    }

    public boolean acceptsCashOperations() {
        return status() == AccountStatus.ACTIVE;
    }
}
