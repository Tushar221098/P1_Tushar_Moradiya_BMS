package com.revature.banking.dao.jdbc;

import java.sql.*;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class JdbcSettings {
    private static final Map<Connection, Integer> TIMEOUTS = Collections.synchronizedMap(new WeakHashMap<>());

    private JdbcSettings() {
    }

    public static void configure(Connection c, int seconds) throws SQLException {
        TIMEOUTS.put(c, seconds);
        try (PreparedStatement s = c.prepareStatement("SELECT set_config('lock_timeout', ?, false), set_config('statement_timeout', ?, false)")) {
            s.setQueryTimeout(seconds);
            s.setString(1, seconds + "s");
            s.setString(2, seconds + "s");
            s.execute();
        }
    }

    public static PreparedStatement prepare(Connection c, String sql) throws SQLException {
        PreparedStatement statement = c.prepareStatement(sql);
        try {
            statement.setQueryTimeout(TIMEOUTS.getOrDefault(c, 10));
            return statement;
        } catch (SQLException e) {
            statement.close();
            throw e;
        }
    }
}
