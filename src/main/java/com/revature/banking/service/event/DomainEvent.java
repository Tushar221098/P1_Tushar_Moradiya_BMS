package com.revature.banking.service.event;

import com.revature.banking.model.AuditOutcome;

public sealed interface DomainEvent permits DomainEvent.LoginOutcome, DomainEvent.FinancialOutcome, DomainEvent.ProfileChanged {
    record LoginOutcome(Long customerId, String username, AuditOutcome outcome) implements DomainEvent {
    }

    record FinancialOutcome(long customerId, String reference, String operation, AuditOutcome outcome,
                            String failureCode) implements DomainEvent {
    }

    record ProfileChanged(long customerId, String operation) implements DomainEvent {
    }
}
