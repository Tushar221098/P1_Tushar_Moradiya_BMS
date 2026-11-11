package com.revature.banking.model.policy;

import com.revature.banking.model.*;

import java.time.OffsetDateTime;

public record LockoutPolicy(int maxFailures) {
    public boolean locked(Customer customer, OffsetDateTime now) {
        return customer.status() == CustomerStatus.CLOSED ||
                (customer.lockedUntil() != null && now.isBefore(customer.lockedUntil())) ||
                (customer.status() == CustomerStatus.LOCKED && customer.lockedUntil() == null);
    }

    public int nextFailures(Customer customer) {
        return customer.lockedUntil() == null ? customer.failedAttempts() + 1 : 1;
    }

    public boolean thresholdReached(int failures) {
        return failures >= maxFailures;
    }
}
