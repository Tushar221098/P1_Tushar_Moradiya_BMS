package com.revature.banking;

import com.revature.banking.security.SessionContext;
import com.revature.banking.exception.BankingException;
import com.revature.banking.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidationAndSessionTest {
    @Test
    void moneyIsExactAndInvalidInputIsRejected() {
        BigDecimal max = new BigDecimal("1000000.00");
        assertEquals(new BigDecimal("10.20"), Validation.amount("10.2", max));
        for (String bad : List.of("garbage", "", "-1", "0", "1.001", "1e3", "1000000.01")) {
            assertThrows(BankingException.class, () -> Validation.amount(bad, max), bad);
        }
    }

    @Test
    void passwordPolicyAndUtf8Length() {
        assertDoesNotThrow(() -> Validation.password("Example!Pass123".toCharArray(), "demo"));
        assertThrows(BankingException.class, () -> Validation.password("short".toCharArray(), "demo"));
        assertThrows(BankingException.class,
                () -> Validation.password("Example!Pass123".toCharArray(), "Example!Pass123"));
        assertFalse(Validation.bcryptLength("é".repeat(37).toCharArray()));
    }

    @Test
    void activityRefreshesAndExactIdleBoundaryExpires() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        SessionContext session = new SessionContext(1, "demo", start);
        Duration idle = Duration.ofMinutes(2);
        assertTrue(session.touch(Clock.fixed(start.plusSeconds(119), ZoneOffset.UTC), idle));
        assertTrue(session.touch(Clock.fixed(start.plusSeconds(238), ZoneOffset.UTC), idle));
        assertFalse(session.touch(Clock.fixed(start.plusSeconds(358), ZoneOffset.UTC), idle));
        assertEquals(SessionContext.End.TIMEOUT, session.end());
        session.logout();
        assertEquals(SessionContext.End.TIMEOUT, session.end());
    }

    @Test
    void logoutDisablesFurtherActivity() {
        SessionContext session = new SessionContext(1, "demo", Instant.now());
        session.logout();
        assertFalse(session.touch(Clock.systemUTC(), Duration.ofMinutes(5)));
        assertEquals(SessionContext.End.LOGOUT, session.end());
    }
}
