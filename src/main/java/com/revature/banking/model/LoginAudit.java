package com.revature.banking.model;

public record LoginAudit(Long customerId, String usernameAttempted, AuditOutcome outcome) {
    public LoginAudit {
        if (outcome == null) throw new IllegalArgumentException("Audit outcome is required");
        if (usernameAttempted == null || !usernameAttempted.matches("[A-Za-z0-9._-]{1,80}"))
            usernameAttempted = "[invalid]";
    }
}
