package com.revature.banking.model;

import java.math.BigDecimal;

public final class CurrentAccount extends Account {
    public CurrentAccount(long id, long owner, String number, BigDecimal balance, AccountStatus status) {
        super(id, owner, number, balance, status, null);
    }

    public AccountType type() {
        return AccountType.CURRENT;
    }

    public boolean acceptsCashOperations() {
        return status() == AccountStatus.ACTIVE;
    }
}
