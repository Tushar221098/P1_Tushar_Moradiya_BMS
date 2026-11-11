package com.revature.banking.model;

import java.time.LocalDate;

import com.revature.banking.exception.ValidationException;

public record LoanTerms(Money principal, int months, LocalDate startDate) {
    public LoanTerms {
        principal.requirePositive();
        if (months < 1 || months > 120 || startDate == null)
            throw new ValidationException("INVALID_TERM", "Tenure must be 1-120 months.");
    }
}
