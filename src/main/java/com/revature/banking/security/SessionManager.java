package com.revature.banking.security;

public final class SessionManager {
    private volatile SessionContext current;

    public SessionContext current() {
        return current;
    }

    public void start(SessionContext session) {
        if (session == null || session.end() != null)
            throw new IllegalArgumentException("An active session is required");
        current = session;
    }

    public void clear() {
        current = null;
    }
}
