package com.revature.banking.util;

import org.slf4j.Logger;

import java.sql.SQLException;

public final class SafeLog {
    private SafeLog() {
    }

    public static void database(Logger logger, String operation, String reference, SQLException error) {
        // Driver messages can include SQL parameters. Retain stack locations, never raw messages or SQL.
        RuntimeException safe = new RuntimeException("Database failure; SQLSTATE=" + error.getSQLState());
        safe.setStackTrace(error.getStackTrace());
        if (error.getCause() != null) {
            RuntimeException cause = new RuntimeException(error.getCause().getClass().getSimpleName());
            cause.setStackTrace(error.getCause().getStackTrace());
            safe.initCause(cause);
        }
        logger.error("event={} reference={} sqlState={}", operation, reference, error.getSQLState(), safe);
    }
}
