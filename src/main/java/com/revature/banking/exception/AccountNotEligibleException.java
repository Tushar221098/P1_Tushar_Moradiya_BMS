package com.revature.banking.exception;

public class AccountNotEligibleException extends BankingException {
    public AccountNotEligibleException(String code, String message) {
        super(code, message);
    }

    public AccountNotEligibleException(String message) {
        this("ACCOUNTNOTELIGIBLE", message);
    }
}
