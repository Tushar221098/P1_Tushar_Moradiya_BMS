package com.revature.banking.model;

import com.revature.banking.exception.ValidationException;

public record Username(String value) {
    public Username {
        if (value == null || !value.matches("[A-Za-z0-9._-]{3,30}"))
            throw new ValidationException("INVALID_USERNAME", "Use 3-30 letters, digits, dots, underscores or hyphens.");
    }
}
