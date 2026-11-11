package com.revature.banking.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.revature.banking.exception.ValidationException;

public record Money(BigDecimal value) implements Comparable<Money> {
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        if (value == null || value.signum() < 0 || value.scale() > 2)
            throw new ValidationException("INVALID_AMOUNT", "Money must be non-negative with up to two decimals.");
        value = value.setScale(2, RoundingMode.UNNECESSARY);
    }

    public Money add(Money other) {
        return new Money(value.add(other.value));
    }

    public Money subtract(Money other) {
        return new Money(value.subtract(other.value));
    }

    public boolean isPositive() {
        return value.signum() > 0;
    }

    public Money requirePositive() {
        if (!isPositive()) throw new ValidationException("INVALID_AMOUNT", "Amount must be positive.");
        return this;
    }

    public int compareTo(Money other) {
        return value.compareTo(other.value);
    }

    public String toString() {
        return value.toPlainString();
    }
}
