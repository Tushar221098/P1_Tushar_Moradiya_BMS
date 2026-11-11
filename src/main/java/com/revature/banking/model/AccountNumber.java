package com.revature.banking.model;

import com.revature.banking.exception.ValidationException;

public final class AccountNumber {
    private final String value;

    public AccountNumber(String value) {
        this(value, true);
    }

    private AccountNumber(String value, boolean generated) {
        if (value == null || !value.matches("[A-Za-z0-9]{1,34}")
                || (generated && (!value.matches("[1-9][0-9]{11}") || !validChecksum(value))))
            throw new ValidationException("INVALID_ACCOUNT_NUMBER", "Invalid account number.");
        this.value = value;
    }

    // Previous UUID, demo and numeric numbers remain readable; only new numbers require a checksum.
    public static AccountNumber persisted(String value) {
        return new AccountNumber(value, false);
    }

    public String value() {
        return value;
    }

    public static boolean validChecksum(String number) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int digit = Character.digit(number.charAt(i), 10);
            if (digit < 0) return false;
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) digit -= 9;
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    public boolean equals(Object other) {
        return other instanceof AccountNumber a && value.equals(a.value);
    }

    public int hashCode() {
        return value.hashCode();
    }

    public String toString() {
        return "****" + value.substring(Math.max(0, value.length() - 4));
    }
}
