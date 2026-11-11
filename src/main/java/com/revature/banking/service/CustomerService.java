package com.revature.banking.service;

import com.revature.banking.dao.CustomerDAO;
import com.revature.banking.model.*;
import com.revature.banking.validation.CustomerValidator;
import com.revature.banking.exception.*;
import com.revature.banking.service.event.*;
import com.revature.banking.util.JdbcTransaction;

import java.sql.Connection;
import java.sql.SQLException;

public final class CustomerService {
    private final CustomerDAO customers;
    private final JdbcTransaction tx;
    private final EventPublisher events;

    public CustomerService(CustomerDAO customers, JdbcTransaction tx, EventPublisher events) {
        this.customers = customers;
        this.tx = tx;
        this.events = events;
    }

    public Customer.Profile profile(long owner) throws SQLException {
        try (Connection c = tx.open()) {
            return customers.profile(c, owner);
        }
    }

    public void updateProfile(long owner, String name, String email, String phone) throws SQLException {
        CustomerValidator.profile(name, email, phone);
        Connection c = tx.begin();
        try {
            Customer customer = customers.lockById(c, owner);
            if (customer == null || customer.status() != CustomerStatus.ACTIVE)
                throw new AuthorizationException("ACCOUNT_UNAVAILABLE", "Profile unavailable.");
            customers.updateProfile(c, owner, name, new Email(email).value(), phone.isEmpty() ? null : phone);
            events.publish(c, new DomainEvent.ProfileChanged(owner, "PROFILE_CHANGED"));
            tx.commit(c);
        } catch (SQLException | RuntimeException e) {
            tx.rollback(c, e);
            if (e instanceof SQLException sql && "23505".equals(sql.getSQLState()))
                throw new ValidationException("DUPLICATE_CUSTOMER", "That email is already registered.");
            throw e;
        } finally {
            tx.close(c);
        }
    }
}
