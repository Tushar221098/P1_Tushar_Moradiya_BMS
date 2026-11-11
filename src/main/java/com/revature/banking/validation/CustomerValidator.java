package com.revature.banking.validation;

import com.revature.banking.model.*;
import com.revature.banking.exception.ValidationException;

public final class CustomerValidator {
    private CustomerValidator() {
    }

    public static void profile(String name, String email, String phone) {
        if (name == null || name.isBlank() || name.length() > 120 || InputValidator.unsafeText(name))
            throw new ValidationException("INVALID_NAME", "Enter a name of 1-120 characters without control characters.");
        new Email(email);
        if (phone == null || (!phone.isEmpty() && !phone.matches("(?=.*[0-9])[+0-9 ()-]{3,30}")))
            throw new ValidationException("INVALID_PHONE", "Enter a valid phone number or leave blank.");
    }
}
