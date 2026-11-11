package com.revature.banking.model;

import com.revature.banking.exception.ValidationException;

public record TransactionReference(String value) {
    public TransactionReference {
        if (value == null || !value.matches("TX-[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}"))
            throw new ValidationException("INVALID_REFERENCE", "Enter a valid TX- reference.");
    }

    public String toString() {
        return value;
    }
}
