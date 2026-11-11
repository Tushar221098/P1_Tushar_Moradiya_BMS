package com.revature.banking.service;

import com.revature.banking.model.*;

import com.revature.banking.validation.Validation;

import com.revature.banking.exception.BankingException;

import com.revature.banking.dao.AuditDAO;
import com.revature.banking.dao.CustomerDAO;
import com.revature.banking.model.Customer;
import com.revature.banking.security.PasswordHasher;
import com.revature.banking.security.SessionContext;
import com.revature.banking.util.AppConfig;
import com.revature.banking.util.JdbcTransaction;
import com.revature.banking.service.event.*;
import com.revature.banking.model.policy.*;
import com.revature.banking.validation.CustomerValidator;

import java.util.List;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Locale;

import static com.revature.banking.model.CustomerStatus.*;

public final class AuthService {
    private final EventPublisher events;
    private final CustomerService profiles;
    private final PasswordPolicy passwordPolicy = new PasswordPolicy();
    private final LockoutPolicy lockout;
    private final CustomerDAO customers;
    private final AuditDAO audit;
    private final JdbcTransaction tx;
    private final AppConfig config;
    private final Clock clock;
    private final PasswordHasher hasher;
    private final String dummyHash;

    public AuthService(CustomerDAO customers, AuditDAO audit, JdbcTransaction tx, AppConfig config,
                       Clock clock, PasswordHasher hasher) {
        this(customers, audit, tx, config, clock, hasher,
                new InProcessEventPublisher(List.of(new AuditSubscriber(audit))));
    }

    public AuthService(CustomerDAO customers, AuditDAO audit, JdbcTransaction tx, AppConfig config,
                       Clock clock, PasswordHasher hasher, EventPublisher events) {
        this.events = events;
        this.lockout = new LockoutPolicy(config.maxFailures());
        this.profiles = new CustomerService(customers, tx, events);
        this.customers = customers;
        this.audit = audit;
        this.tx = tx;
        this.config = config;
        this.clock = clock;
        this.hasher = hasher;
        this.dummyHash = hasher.hash("Dummy!Password123".toCharArray());
    }

    public long register(String name, String email, String phone, String username, char[] password)
            throws SQLException {
        try {
            CustomerValidator.profile(name, email, phone);
            new Username(username);
            passwordPolicy.validate(password, username);
            String hash = hasher.hash(password);
            // Registration is one INSERT. PostgreSQL also enforces unique usernames and emails.
            try (Connection c = tx.open()) {
                return customers.insert(c, name, email.toLowerCase(Locale.ROOT),
                        phone.isEmpty() ? null : phone, username, hash);
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) {
                    throw BankingException.of("DUPLICATE_CUSTOMER", "That username or email is already registered.");
                }
                throw e;
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public SessionContext login(String username, char[] password) throws SQLException {
        try {
            Connection connection = tx.begin();
            try {
                SessionContext session = checkLogin(connection, username, password);
                tx.commit(connection);
                return session;
            } catch (SQLException | RuntimeException e) {
                tx.rollback(connection, e);
                throw e;
            } finally {
                tx.close(connection);
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private SessionContext checkLogin(Connection connection, String username, char[] password)
            throws SQLException {
        // Lock this customer row so two failed attempts cannot overwrite each other's counter.
        Customer customer = null;
        if (username.length() <= 80 && !com.revature.banking.validation.InputValidator.unsafeText(username)) {
            customer = customers.lockByUsername(connection, username);
        }

        // Run BCrypt even for unknown usernames so both paths do similar work.
        String hash = customer == null ? dummyHash : customer.passwordHash();
        boolean validLength = Validation.bcryptLength(password);
        boolean matches = hasher.verify(validLength ? password : new char[0], hash);
        if (customer == null) {
            events.publish(connection, new DomainEvent.LoginOutcome(null, auditUsername(username), AuditOutcome.FAILURE));
            return null;
        }

        OffsetDateTime now = clock.instant().atOffset(ZoneOffset.UTC);
        boolean hasTimedLock = customer.lockedUntil() != null;
        if (lockout.locked(customer, now)) {
            events.publish(connection, new DomainEvent.LoginOutcome(customer.id(), auditUsername(username), AuditOutcome.LOCKED));
            return null;
        }

        if (!validLength || !matches) {
            int attempts = lockout.nextFailures(customer);
            if (lockout.thresholdReached(attempts)) {
                customers.authenticationState(connection, customer.id(), attempts,
                        now.plus(config.lockDuration()), LOCKED);
                events.publish(connection, new DomainEvent.LoginOutcome(customer.id(), auditUsername(username), AuditOutcome.LOCKED));
            } else {
                customers.authenticationState(connection, customer.id(), attempts, null, ACTIVE);
                events.publish(connection, new DomainEvent.LoginOutcome(customer.id(), auditUsername(username), AuditOutcome.FAILURE));
            }
            return null;
        }

        customers.authenticationState(connection, customer.id(), 0, null, ACTIVE);
        events.publish(connection, new DomainEvent.LoginOutcome(customer.id(), auditUsername(username), AuditOutcome.SUCCESS));
        return new SessionContext(customer.id(), customer.username(), clock.instant());
    }

    public Customer.Profile profile(long id) throws SQLException {
        return profiles.profile(id);
    }

    public void updateProfile(long id, String name, String email, String phone) throws SQLException {
        profiles.updateProfile(id, name, email, phone);
    }

    public void changePassword(long id, char[] current, char[] replacement) throws SQLException {
        try {
            Connection connection = tx.begin();
            try {
                Customer customer = customers.lockById(connection, id);
                if (customer == null || customer.status() != ACTIVE || !Validation.bcryptLength(current)
                        || !hasher.verify(current, customer.passwordHash())) {
                    throw BankingException.of("BAD_CREDENTIALS", "Current password could not be verified.");
                }
                passwordPolicy.validate(replacement, customer.username());
                customers.password(connection, id, hasher.hash(replacement));
                events.publish(connection, new DomainEvent.ProfileChanged(id, "PASSWORD_CHANGED"));
                tx.commit(connection);
            } catch (SQLException | RuntimeException e) {
                tx.rollback(connection, e);
                throw e;
            } finally {
                tx.close(connection);
            }
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(replacement, '\0');
        }
    }

    public void end(SessionContext session) throws SQLException {
        if (session.end() == null) throw new IllegalArgumentException("Session is still active");
        try (Connection c = tx.open()) {
            events.publish(c, new DomainEvent.LoginOutcome(session.customerId(), auditUsername(session.username()), AuditOutcome.valueOf(session.end().name())));
        }
    }

    private static String auditUsername(String username) {
        return username.matches("[A-Za-z0-9._-]{1,80}") ? username : "[invalid]";
    }
}
