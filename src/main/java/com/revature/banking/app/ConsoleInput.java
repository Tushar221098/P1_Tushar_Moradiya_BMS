package com.revature.banking.app;

import com.revature.banking.security.SessionContext;
import com.revature.banking.exception.BankingException;

import java.io.Console;
import java.io.InputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;

final class ConsoleInput {
    private final Console console;
    private final Scanner scanner;
    final PrintStream out;
    private final Clock clock;
    private final Duration idle;
    private final com.revature.banking.security.SessionManager sessions = new com.revature.banking.security.SessionManager();
    private String indent = "";
    private boolean backEnabled;

    ConsoleInput(Console console, InputStream in, PrintStream out, Clock clock, Duration idle) {
        this.console = console;
        this.scanner = console == null ? new Scanner(in) : null;
        this.out = out;
        this.clock = clock;
        this.idle = idle;
    }

    void session(SessionContext session) {
        if (session == null) sessions.clear();
        else sessions.start(session);
    }

    void println(String text) {
        out.println(indent + text.replace("\n", "\n" + indent));
    }

    void table(List<String> headers, List<List<String>> rows) {
        int[] widths = headers.stream().mapToInt(String::length).toArray();
        for (List<String> row : rows) {
            for (int i = 0; i < widths.length; i++) widths[i] = Math.max(widths[i], row.get(i).length());
        }
        StringBuilder border = new StringBuilder("+");
        for (int width : widths) border.append("-".repeat(width + 2)).append('+');
        println(border.toString());
        tableRow(headers, widths);
        println(border.toString());
        for (List<String> row : rows) tableRow(row, widths);
        println(border.toString());
    }

    private void tableRow(List<String> values, int[] widths) {
        StringBuilder row = new StringBuilder("|");
        for (int i = 0; i < widths.length; i++) {
            row.append(' ').append(values.get(i))
                    .append(" ".repeat(widths[i] - values.get(i).length() + 1)).append('|');
        }
        println(row.toString());
    }

    void heading(String title) {
        heading(title, 1);
    }

    void heading(String title, int level) {
        indent = "    ".repeat(level);
        backEnabled = level > 0;
        int width = Math.max(44, title.length() + 4);
        String border = "+" + "-".repeat(width) + "+";
        int left = (width - title.length()) / 2;
        out.println();
        println(border);
        println("|" + " ".repeat(left) + title
                + " ".repeat(width - left - title.length()) + "|");
        println(border);
    }

    String line(String prompt) {
        String value = raw(prompt);
        checkSession();
        if (backEnabled && value.strip().equals("0")) throw new Back();
        return value.strip();
    }

    char[] password(String prompt) {
        char[] value;
        if (console == null) {
            value = raw(prompt).toCharArray();
        } else {
            String label = formatPrompt(prompt);
            try (ExpiryNotice notice = new ExpiryNotice()) {
                value = console.readPassword("%s", label);
            }
        }
        if (value == null) throw new EndOfInput();
        try {
            checkSession();
            if (backEnabled && Arrays.equals(value, new char[]{'0'})) throw new Back();
            return value;
        } catch (Expired | Back e) {
            Arrays.fill(value, '\0');
            throw e;
        }
    }

    int integer(String prompt) {
        try {
            return Integer.parseInt(line(prompt));
        } catch (NumberFormatException e) {
            throw BankingException.of("INVALID_NUMBER", "Enter a whole number.");
        }
    }

    char[] confirmedPassword() {
        char[] password = password("New password: ");
        // Clear the first password too if confirmation fails or input closes.
        boolean matches = false;
        try {
            char[] confirmation = password("Confirm password: ");
            try {
                matches = Arrays.equals(password, confirmation);
                if (!matches) throw BankingException.of("PASSWORD_MISMATCH", "Passwords do not match.");
                return password;
            } finally {
                Arrays.fill(confirmation, '\0');
            }
        } finally {
            if (!matches) Arrays.fill(password, '\0');
        }
    }

    private String raw(String prompt) {
        String label = formatPrompt(prompt);
        String value;
        try (ExpiryNotice notice = new ExpiryNotice()) {
            if (console != null) {
                value = console.readLine("%s", label);
            } else {
                out.print(label);
                out.flush();
                value = scanner.hasNextLine() ? scanner.nextLine() : null;
            }
        }
        if (value == null) throw new EndOfInput();
        return value;
    }

    private String formatPrompt(String prompt) {
        String idleLabel = "";
        SessionContext active = sessions.current();
        if (active != null) {
            long millis = active.remaining(clock, idle).toMillis();
            if (millis == 0) checkSession();
            long seconds = (millis + 999) / 1000;
            idleLabel = String.format("[Idle %02d:%02d] ", seconds / 60, seconds % 60);
        }
        boolean menuChoice = prompt.equals("Choice: ") || prompt.equals("Account type: ")
                || prompt.equals("Choose account: ");
        String label = prompt;
        if (backEnabled && !menuChoice) {
            label = prompt.stripTrailing().replaceFirst(":$", "") + " (0 = Back): ";
        }
        return indent + idleLabel + label;
    }

    private final class ExpiryNotice implements AutoCloseable {
        private final Timer timer;
        private boolean closed;
        private final String noticeIndent = indent;

        ExpiryNotice() {
            SessionContext active = sessions.current();
            if (active == null) {
                timer = null;
                return;
            }
            timer = new Timer("session-expiry-notice", true);
            long delay = Math.max(1, active.remaining(clock, idle).toMillis() + 1);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    announce(active);
                }
            }, delay);
        }

        private synchronized void announce(SessionContext active) {
            if (!closed && active.remaining(clock, idle).isZero()) {
                out.println("\n" + noticeIndent + "Session expired. Press Enter to return to login.");
                out.flush();
            }
        }

        @Override
        public synchronized void close() {
            closed = true;
            if (timer != null) timer.cancel();
        }
    }

    private void checkSession() {
        // Check after each answer so waiting at an amount prompt cannot bypass expiry.
        try {
            new com.revature.banking.service.SessionService().requireActive(sessions.current(), clock, idle);
        } catch (com.revature.banking.exception.SessionExpiredException e) {
            throw new Expired();
        }
    }

    static final class EndOfInput extends RuntimeException {
    }

    static final class Back extends RuntimeException {
    }

    static final class Expired extends com.revature.banking.exception.SessionExpiredException {
        Expired() {
            super("SESSION_EXPIRED", "Session expired. Please log in again.");
        }
    }
}
