package com.revature.banking.exception;

public class BankingException extends RuntimeException {
    private final String code;

    public BankingException(String code, String message) {
        super(message);
        this.code = code;
    }

    private String reference;

    public String reference() {
        return reference;
    }

    public BankingException withReference(String reference) {
        this.reference = reference;
        return this;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + (reference == null ? "" : " Reference: " + reference);
    }

    public static BankingException of(String code, String message) {
        return switch (code) {
            case "BAD_CREDENTIALS" -> new AuthenticationException(code, message);
            case "ACCOUNT_UNAVAILABLE", "NOT_OWNER" -> new AuthorizationException(code, message);
            case "INSUFFICIENT_FUNDS" -> new InsufficientFundsException(code, message);
            case "INELIGIBLE_ACCOUNT", "INELIGIBLE_LOAN", "DEPOSIT_UNAVAILABLE" ->
                    new AccountNotEligibleException(code, message);
            case "COMMIT_UNCONFIRMED", "DATABASE_ERROR" -> new PersistenceException(code, message);
            default -> new ValidationException(code, message);
        };
    }

    public String code() {
        return code;
    }
}
