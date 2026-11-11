package com.revature.banking.exception;

public class PersistenceException extends BankingException {
    public PersistenceException(String code, String message) {
        super(code, message);
    }

    public PersistenceException(String code, String message, Throwable cause) {
        super(code, message);
        initCause(cause);
    }

    public PersistenceException(String message) {
        this("PERSISTENCE", message);
    }
}
