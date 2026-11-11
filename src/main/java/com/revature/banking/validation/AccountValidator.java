package com.revature.banking.validation;

import com.revature.banking.model.Account;
import com.revature.banking.exception.AuthorizationException;

public final class AccountValidator {
    private AccountValidator() {
    }

    public static Account owned(Account account, long owner) {
        if (account == null || account.ownerId() != owner)
            throw new AuthorizationException("ACCOUNT_UNAVAILABLE", "Account unavailable.");
        return account;
    }
}
