package com.revature.banking.service.event;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public final class InProcessEventPublisher implements EventPublisher {
    private final List<EventPublisher> subscribers;

    public InProcessEventPublisher(List<EventPublisher> subscribers) {
        this.subscribers = List.copyOf(subscribers);
    }

    public void publish(Connection c, DomainEvent event) throws SQLException {
        for (EventPublisher subscriber : subscribers) subscriber.publish(c, event);
    }
}
