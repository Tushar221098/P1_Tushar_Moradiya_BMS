package com.revature.banking.exception;

public class ValidationException extends BankingException {
    public ValidationException(String code, String message) {
        super(code, message);
    }

    public ValidationException(String message) {
        this("VALIDATION", message);
    }
}
