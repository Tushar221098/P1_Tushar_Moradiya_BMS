package com.revature.banking.util;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Properties;

public final class AppConfig {
    private final Duration idle;
    private final int querySeconds;
    private final BigDecimal maxAmount;
    private final BigDecimal minDeposit;
    private final int maxFailures;
    private final Duration lockDuration;
    private final int bcryptCost;
    private final int maxPageSize;

    public AppConfig(Duration idle, int querySeconds, BigDecimal maxAmount, BigDecimal minDeposit, int maxFailures, Duration lockDuration, int bcryptCost, int maxPageSize) {
        this.idle = idle;
        this.querySeconds = querySeconds;
        this.maxAmount = maxAmount;
        this.minDeposit = minDeposit;
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
        this.bcryptCost = bcryptCost;
        this.maxPageSize = maxPageSize;
    }

    public Duration idle() {
        return idle;
    }

    public int querySeconds() {
        return querySeconds;
    }

    public BigDecimal maxAmount() {
        return maxAmount;
    }

    public BigDecimal minDeposit() {
        return minDeposit;
    }

    public int maxFailures() {
        return maxFailures;
    }

    public Duration lockDuration() {
        return lockDuration;
    }

    public int bcryptCost() {
        return bcryptCost;
    }

    public int maxPageSize() {
        return maxPageSize;
    }

    public static AppConfig load() {
        Properties p = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream("/app.properties")) {
            if (in == null) throw new IllegalStateException("Missing app.properties.");
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read app.properties.", e);
        }
        return new AppConfig(Duration.ofSeconds(integer(p, "session.idle.timeout.seconds")),
                integer(p, "db.query.timeout.seconds"), money(p, "tx.max.amount"),
                money(p, "deposit.min.amount"), integer(p, "auth.max.failed.logins"),
                Duration.ofMinutes(integer(p, "auth.lock.minutes")),
                integer(p, "password.bcrypt.cost"), integer(p, "history.max.page.size"));
    }

    private static int integer(Properties p, String key) {
        int value = Integer.parseInt(p.getProperty(key));
        if (value < 1 || (key.equals("password.bcrypt.cost") && (value < 4 || value > 16))
                || (key.equals("history.max.page.size") && value > 50)) {
            throw new IllegalStateException("Invalid setting: " + key);
        }
        return value;
    }

    private static BigDecimal money(Properties p, String key) {
        BigDecimal value = new BigDecimal(p.getProperty(key)).setScale(2);
        if (value.signum() <= 0 || value.precision() > 19) {
            throw new IllegalStateException("Invalid setting: " + key);
        }
        return value;
    }
}
