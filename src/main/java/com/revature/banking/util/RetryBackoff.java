package com.revature.banking.util;

import java.util.concurrent.ThreadLocalRandom;

import com.revature.banking.exception.PersistenceException;

@FunctionalInterface
public interface RetryBackoff {
    void pause(int attempt);

    static RetryBackoff randomized() {
        return attempt -> {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(20L, 60L) * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PersistenceException("RETRY_INTERRUPTED", "Operation interrupted. Please retry.");
            }
        };
    }
}
