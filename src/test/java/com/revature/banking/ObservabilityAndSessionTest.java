package com.revature.banking;

import com.revature.banking.model.*;
import com.revature.banking.security.*;
import com.revature.banking.service.SessionService;
import com.revature.banking.service.event.*;
import com.revature.banking.exception.SessionExpiredException;
import com.revature.banking.dao.AuditDAO;
import com.revature.banking.util.SafeLog;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.sql.*;
import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ObservabilityAndSessionTest {
    @Test
    void sqlDiagnosticsKeepStackLocationsWithoutDriverSecrets() {
        Logger logger = (Logger) LoggerFactory.getLogger("safe-log-test");
        ListAppender<ILoggingEvent> capture = new ListAppender<>();
        capture.start();
        logger.addAppender(capture);
        try {
            SQLException error = new SQLException("password=never-print-this account=123456789012", "08006");
            SafeLog.database(logger, "connection_failed", "TX-example", error);
            ILoggingEvent entry = capture.list.get(0);
            assertTrue(entry.getFormattedMessage().contains("08006"));
            assertFalse(entry.getFormattedMessage().contains("never-print-this"));
            assertFalse(entry.getThrowableProxy().getMessage().contains("never-print-this"));
            assertTrue(entry.getThrowableProxy().getStackTraceElementProxyArray().length > 0);
        } finally {
            logger.detachAppender(capture);
            capture.stop();
        }
    }

    @Test
    void synchronousEventPublicationReachesTheAuditSubscriber() throws Exception {
        AuditDAO audit = mock(AuditDAO.class);
        Connection c = mock(Connection.class);
        EventPublisher events = new InProcessEventPublisher(List.of(new AuditSubscriber(audit)));
        events.publish(c, new DomainEvent.ProfileChanged(3, "PASSWORD_CHANGED"));
        verify(audit).transaction(c, 3, null, "PASSWORD_CHANGED", "SUCCESS", null);
    }

    @Test
    void sessionManagerClearsIdentityAndExpiredSessionsCannotRefresh() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        SessionContext session = new SessionContext(1, "demo", start);
        SessionManager manager = new SessionManager();
        manager.start(session);
        assertSame(session, manager.current());
        assertThrows(SessionExpiredException.class, () -> new SessionService().requireActive(session,
                Clock.fixed(start.plusSeconds(120), ZoneOffset.UTC), Duration.ofMinutes(2)));
        assertEquals(SessionContext.End.TIMEOUT, session.end());
        manager.clear();
        assertNull(manager.current());
        assertThrows(IllegalArgumentException.class, () -> manager.start(session));
    }

    @Test
    void auditObjectsRejectArbitraryTechnicalDetails() {
        LoginAudit login = new LoginAudit(null, "unsafe username\n", AuditOutcome.FAILURE);
        assertEquals("[invalid]", login.usernameAttempted());
        assertThrows(IllegalArgumentException.class,
                () -> new TransactionAudit(1, null, "TRANSFER", AuditOutcome.FAILED, "SQL password=secret"));
    }
}
