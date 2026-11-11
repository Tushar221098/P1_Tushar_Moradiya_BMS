package com.revature.banking.dao;

import com.revature.banking.model.*;

import com.revature.banking.model.Customer;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;

public interface CustomerDAO {
    long insert(Connection c, String name, String email, String phone,
                String username, String hash) throws SQLException;

    Customer lockByUsername(Connection c, String username) throws SQLException;

    Customer lockById(Connection c, long id) throws SQLException;

    Customer.Profile profile(Connection c, long id) throws SQLException;

    void authenticationState(Connection c, long id, int failures, OffsetDateTime until,
                             CustomerStatus status) throws SQLException;

    void updateProfile(Connection c, long id, String name, String email, String phone) throws SQLException;

    void password(Connection c, long id, String hash) throws SQLException;
}
