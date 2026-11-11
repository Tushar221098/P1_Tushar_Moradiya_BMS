package com.revature.banking.model;

import java.util.Locale;

import com.revature.banking.exception.ValidationException;

public record Email(String value) {
    public Email {
        if (value == null || value.length() > 254 || !value.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+"))
            throw new ValidationException("INVALID_EMAIL", "Enter a valid email address.");
        value = value.toLowerCase(Locale.ROOT);
    }
}
