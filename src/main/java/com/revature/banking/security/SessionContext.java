package com.revature.banking.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public final class SessionContext {
    public enum End {LOGOUT, TIMEOUT}

    private final long customerId;
    private final String username;
    private final Instant createdAt;
    private volatile Instant lastActivity;
    private volatile End end;

    public SessionContext(long customerId, String username, Instant now) {
        this.customerId = customerId;
        this.username = username;
        this.createdAt = now;
        this.lastActivity = now;
    }

    public long customerId() {
        return customerId;
    }

    public String username() {
        return username;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public End end() {
        return end;
    }

    public Duration remaining(Clock clock, Duration idle) {
        if (end != null) return Duration.ZERO;
        Duration remaining = Duration.between(clock.instant(), lastActivity.plus(idle));
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public synchronized boolean touch(Clock clock, Duration idle) {
        if (end != null) return false;
        Instant now = clock.instant();
        if (!now.isBefore(lastActivity.plus(idle))) {
            end = End.TIMEOUT;
            return false;
        }
        lastActivity = now;
        return true;
    }

    public synchronized void logout() {
        if (end == null) end = End.LOGOUT;
    }
}
