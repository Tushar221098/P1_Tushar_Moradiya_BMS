package com.revature.banking.model;

public record TransactionAudit(long customerId, String reference, String eventType, AuditOutcome outcome,
                               String failureCode) {
    public TransactionAudit {
        if (customerId < 1 || eventType == null || !eventType.matches("[A-Z_]{1,30}") || outcome == null
                || (failureCode != null && !failureCode.matches("[A-Z_]{1,40}")))
            throw new IllegalArgumentException("Invalid audit fields");
        if (reference != null) new TransactionReference(reference);
    }
}
