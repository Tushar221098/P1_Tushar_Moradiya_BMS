package com.revature.banking.exception;

public class AuthorizationException extends BankingException {
    public AuthorizationException(String code, String message) {
        super(code, message);
    }

    public AuthorizationException(String message) {
        this("AUTHORIZATION", message);
    }
}
