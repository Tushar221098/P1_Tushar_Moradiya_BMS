package com.revature.banking.model;

public final class PasswordHash {
    private final String value;

    public PasswordHash(String value) {
        if (value == null || !value.matches("\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}"))
            throw new IllegalArgumentException("Invalid encoded password hash");
        this.value = value;
    }

    public String encoded() {
        return value;
    }

    public String toString() {
        return "[REDACTED]";
    }
}
