package com.revature.banking.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class HistoryEntry {
    private final long id;
    private final String reference;
    private final TransactionType type;
    private final BigDecimal amount;
    private final String status;
    private final String direction;
    private final OffsetDateTime time;

    public HistoryEntry(long id, String reference, TransactionType type, BigDecimal amount, String status, String direction, OffsetDateTime time) {
        this.id = id;
        this.reference = reference;
        this.type = type;
        this.amount = amount;
        this.status = status;
        this.direction = direction;
        this.time = time;
    }

    public long id() {
        return id;
    }

    public String reference() {
        return reference;
    }

    public TransactionType type() {
        return type;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String status() {
        return status;
    }

    public String direction() {
        return direction;
    }

    public OffsetDateTime time() {
        return time;
    }
}
