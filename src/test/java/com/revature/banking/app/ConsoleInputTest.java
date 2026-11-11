package com.revature.banking.app;

import com.revature.banking.security.SessionContext;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleInputTest {
    @Test
    void backCancelsTextAndPasswordPromptsInSubmenus() {
        ConsoleInput in = new ConsoleInput(null, new ByteArrayInputStream("0\n0\n".getBytes()),
                new PrintStream(new ByteArrayOutputStream()), Clock.systemUTC(), Duration.ofMinutes(2));
        in.heading("REGISTER");
        assertThrows(ConsoleInput.Back.class, () -> in.line("Full name: "));
        assertThrows(ConsoleInput.Back.class, () -> in.password("New password: "));
    }

    @Test
    void mainMenuDoesNotEnableBackNavigation() {
        ConsoleInput in = new ConsoleInput(null, new ByteArrayInputStream("0\n".getBytes()),
                new PrintStream(new ByteArrayOutputStream()), Clock.systemUTC(), Duration.ofMinutes(2));
        in.heading("WELCOME TO BMS", 0);
        assertEquals("0", in.line("Choice: "));
    }

    @Test
    void menuChoiceIsAcceptedOnceAndFollowingPasswordIsPreserved() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleInput in = new ConsoleInput(null,
                new ByteArrayInputStream("2\nExample!Pass123\n".getBytes()), new PrintStream(output),
                Clock.fixed(start, ZoneOffset.UTC), Duration.ofMinutes(2));
        in.session(new SessionContext(1, "demo", start));

        assertEquals("2", in.line("Choice: "));
        assertArrayEquals("Example!Pass123".toCharArray(), in.password("Current password: "));
        assertFalse(output.toString().contains("\r" + "Session"));
    }

    @Test
    void passwordPromptDoesNotRedrawWhileWaitingForInput() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayInputStream input = new ByteArrayInputStream("Example!Pass123\n".getBytes()) {
            @Override
            public synchronized int read(byte[] bytes, int offset, int length) {
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                return super.read(bytes, offset, length);
            }
        };
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        ConsoleInput in = new ConsoleInput(null, input, new PrintStream(output),
                Clock.fixed(start, ZoneOffset.UTC), Duration.ofMinutes(2));
        in.session(new SessionContext(1, "demo", start));

        assertArrayEquals("Example!Pass123".toCharArray(), in.password("Current password: "));
        assertEquals(1, output.toString().split("Idle ", -1).length - 1);
        assertTrue(output.toString().contains("[Idle 02:00] Current password:"));
        assertFalse(output.toString().contains("Example!Pass123"));
    }

    @Test
    void passwordStillRejectsAnExpiredSession() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        ConsoleInput in = new ConsoleInput(null, new ByteArrayInputStream("Example!Pass123\n".getBytes()),
                new PrintStream(new ByteArrayOutputStream()),
                Clock.fixed(start.plusSeconds(120), ZoneOffset.UTC), Duration.ofMinutes(2));
        SessionContext session = new SessionContext(1, "demo", start);
        in.session(session);

        assertThrows(ConsoleInput.Expired.class, () -> in.password("Current password: "));
        assertEquals(SessionContext.End.TIMEOUT, session.end());
    }

    @Test
    void eofDuringPasswordConfirmationAndTextInputExitsSafely() {
        ConsoleInput text = new ConsoleInput(null, new ByteArrayInputStream(new byte[0]),
                new PrintStream(new ByteArrayOutputStream()), Clock.systemUTC(), Duration.ofMinutes(2));
        assertThrows(ConsoleInput.EndOfInput.class, () -> text.line("Choice: "));
        ConsoleInput password = new ConsoleInput(null, new ByteArrayInputStream("Example!Pass123\n".getBytes()),
                new PrintStream(new ByteArrayOutputStream()), Clock.systemUTC(), Duration.ofMinutes(2));
        assertThrows(ConsoleInput.EndOfInput.class, password::confirmedPassword);
    }
}
