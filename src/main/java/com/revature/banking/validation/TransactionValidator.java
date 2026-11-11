package com.revature.banking.validation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.revature.banking.model.Money;
import com.revature.banking.exception.ValidationException;

public final class TransactionValidator {
    private TransactionValidator() {
    }

    public static Money amount(String input, BigDecimal maximum) {
        return new Money(Validation.amount(input, maximum)).requirePositive();
    }

    public static void history(int size, int maximum, OffsetDateTime from, OffsetDateTime before) {
        if (size < 1 || size > maximum || (from != null && before != null && !from.isBefore(before)))
            throw new ValidationException("INVALID_HISTORY_FILTER", "Check the page size and date range.");
    }
}
