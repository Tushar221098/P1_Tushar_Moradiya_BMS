package com.revature.banking.model;

public enum AccountType {
    SAVINGS, CURRENT, DEPOSIT, LOAN;

    public boolean spendable() {
        return this == SAVINGS || this == CURRENT;
    }
}
