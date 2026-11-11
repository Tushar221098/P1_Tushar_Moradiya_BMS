package com.revature.banking.model;

import com.revature.banking.exception.ValidationException;

public record BankTransaction(TransactionReference reference, TransactionType type, Long sourceId, Long destinationId,
                              Money amount, Money sourceAfter, Money destinationAfter, String description) {
    public BankTransaction {
        amount.requirePositive();
        boolean shape = switch (type) {
            case DEPOSIT -> sourceId == null && destinationId != null;
            case WITHDRAWAL -> sourceId != null && destinationId == null;
            case TRANSFER, LOAN_REPAYMENT ->
                    sourceId != null && destinationId != null && !sourceId.equals(destinationId);
        };
        if (reference == null || !shape || (sourceId != null && sourceId < 1) || (destinationId != null && destinationId < 1)
                || (sourceId != null && sourceAfter == null) || (destinationId != null && destinationAfter == null))
            throw new ValidationException("INVALID_TRANSACTION", "Invalid ledger entry.");
    }

    public TransactionStatus status() {
        return TransactionStatus.SUCCESS;
    }
}
