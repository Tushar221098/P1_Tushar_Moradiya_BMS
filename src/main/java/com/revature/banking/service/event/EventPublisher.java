package com.revature.banking.service.event;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface EventPublisher {
    void publish(Connection connection, DomainEvent event) throws SQLException;
}
