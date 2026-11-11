package com.revature.banking.model;

import java.math.BigDecimal;

import com.revature.banking.exception.ValidationException;

public final class LoanAccount extends Account {
    public LoanAccount(long id, long owner, String number, BigDecimal balance, AccountStatus status) {
        super(id, owner, number, balance, status, null);
    }

    public AccountType type() {
        return AccountType.LOAN;
    }

    public boolean acceptsRepayment() {
        return status() == AccountStatus.ACTIVE && money().isPositive();
    }

    public Money repay(Money amount) {
        if (!acceptsRepayment()) return super.repay(amount);
        amount.requirePositive();
        if (amount.compareTo(money()) > 0)
            throw new ValidationException("LOAN_OVERPAYMENT", "Payment exceeds outstanding principal.");
        return money().subtract(amount);
    }
}
