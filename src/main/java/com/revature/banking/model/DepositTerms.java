package com.revature.banking.model;

import java.time.LocalDate;

import com.revature.banking.exception.ValidationException;

public record DepositTerms(Money principal, int months, LocalDate maturityDate) {
    public DepositTerms {
        principal.requirePositive();
        if (months < 1 || months > 120 || maturityDate == null)
            throw new ValidationException("INVALID_TERM", "Term must be 1-120 months.");
    }

    public static DepositTerms open(Money principal, int months, LocalDate today) {
        if (months < 1 || months > 120) throw new ValidationException("INVALID_TERM", "Term must be 1-120 months.");
        return new DepositTerms(principal, months, today.plusMonths(months));
    }
}
