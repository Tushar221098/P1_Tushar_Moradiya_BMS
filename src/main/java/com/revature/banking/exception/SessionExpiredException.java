package com.revature.banking.exception;

public class SessionExpiredException extends BankingException {
    public SessionExpiredException(String code, String message) {
        super(code, message);
    }

    public SessionExpiredException(String message) {
        this("SESSIONEXPIRED", message);
    }
}
