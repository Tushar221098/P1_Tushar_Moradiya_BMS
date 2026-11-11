package com.revature.banking.exception;

public class InsufficientFundsException extends BankingException {
    public InsufficientFundsException(String code, String message) {
        super(code, message);
    }

    public InsufficientFundsException(String message) {
        this("INSUFFICIENTFUNDS", message);
    }
}
