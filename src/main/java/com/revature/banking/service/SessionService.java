package com.revature.banking.service;

import com.revature.banking.security.SessionContext;
import com.revature.banking.exception.SessionExpiredException;

import java.time.Clock;
import java.time.Duration;

public final class SessionService {
    public void requireActive(SessionContext session, Clock clock, Duration idle) {
        if (session != null && !session.touch(clock, idle))
            throw new SessionExpiredException("SESSION_EXPIRED", "Session expired. Please log in again.");
    }
}
