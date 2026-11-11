package com.revature.banking.dao.jdbc;

import com.revature.banking.dao.TransactionDAO;
import com.revature.banking.model.HistoryEntry;
import com.revature.banking.model.TransactionType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public final class JdbcTransactionDAO implements TransactionDAO {
    @Override
    public void insert(Connection c, String ref, TransactionType type, Long source, Long dest,
                       BigDecimal amount, BigDecimal sourceAfter, BigDecimal destAfter, String description)
            throws SQLException {
        String sql = """
                INSERT INTO transactions(reference_number,source_account_id,destination_account_id,
                    transaction_type,amount,status,source_balance_after,dest_balance_after,description)
                VALUES (?,?,?,?,?,'SUCCESS',?,?,?)
                """;
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql)) {
            statement.setString(1, ref);
            statement.setObject(2, source);
            statement.setObject(3, dest);
            statement.setString(4, type.name());
            statement.setBigDecimal(5, amount);
            statement.setBigDecimal(6, sourceAfter);
            statement.setBigDecimal(7, destAfter);
            statement.setString(8, description);
            statement.executeUpdate();
        }
    }

    @Override
    public List<HistoryEntry> history(Connection c, long accountId, TransactionType type,
                                      OffsetDateTime from, OffsetDateTime before,
                                      HistoryEntry cursor, int limit) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT transaction_id, reference_number, transaction_type, amount, status, created_at,
                       CASE WHEN destination_account_id=? AND transaction_type='LOAN_REPAYMENT'
                            THEN 'DEBT REDUCTION' WHEN destination_account_id=? THEN 'CREDIT'
                            ELSE 'DEBIT' END AS direction
                FROM transactions WHERE (source_account_id=? OR destination_account_id=?)
                """);
        List<Object> args = new ArrayList<>(List.of(accountId, accountId, accountId, accountId));
        if (type != null) {
            sql.append(" AND transaction_type=?");
            args.add(type.name());
        }
        if (from != null) {
            sql.append(" AND created_at>=?");
            args.add(from);
        }
        if (before != null) {
            sql.append(" AND created_at<?");
            args.add(before);
        }
        // Start the next page after the last row already shown, including when dates tie.
        if (cursor != null) {
            sql.append(" AND (created_at,transaction_id)<(?,?)");
            args.add(cursor.time());
            args.add(cursor.id());
        }
        sql.append(" ORDER BY created_at DESC, transaction_id DESC LIMIT ?");
        args.add(limit);
        List<HistoryEntry> results = new ArrayList<>();
        try (PreparedStatement statement = JdbcSettings.prepare(c, sql.toString())) {
            // Only fixed SQL clauses are appended above. Filter values stay in parameters.
            for (int i = 0; i < args.size(); i++) {
                statement.setObject(i + 1, args.get(i));
            }
            try (ResultSet r = statement.executeQuery()) {
                while (r.next()) {
                    results.add(new HistoryEntry(r.getLong(1), r.getString(2),
                            TransactionType.valueOf(r.getString(3)), r.getBigDecimal(4), r.getString(5),
                            r.getString("direction"), r.getObject(6, OffsetDateTime.class)));
                }
            }
        }
        return results;
    }
}
