package com.revature.banking.dao;

import com.revature.banking.model.HistoryEntry;
import com.revature.banking.model.TransactionType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

public interface TransactionDAO {
    void insert(Connection c, String ref, TransactionType type, Long source, Long dest,
                BigDecimal amount, BigDecimal sourceAfter, BigDecimal destAfter, String description)
            throws SQLException;

    List<HistoryEntry> history(Connection c, long accountId, TransactionType type,
                               OffsetDateTime from, OffsetDateTime before,
                               HistoryEntry cursor, int limit) throws SQLException;
}
