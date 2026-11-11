package com.revature.banking.exception;

public class AuthenticationException extends BankingException {
    public AuthenticationException(String code, String message) {
        super(code, message);
    }

    public AuthenticationException(String message) {
        this("AUTHENTICATION", message);
    }
}
