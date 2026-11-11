package com.revature.banking.service.event;

import com.revature.banking.dao.AuditDAO;
import com.revature.banking.model.*;

import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AuditSubscriber implements EventPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(AuditSubscriber.class);
    private final AuditDAO audit;

    public AuditSubscriber(AuditDAO audit) {
        this.audit = audit;
    }

    public void publish(Connection c, DomainEvent event) throws SQLException {
        if (event instanceof DomainEvent.LoginOutcome e) {
            LoginAudit record = new LoginAudit(e.customerId(), e.username(), e.outcome());
            audit.login(c, record.customerId(), record.usernameAttempted(), record.outcome().name());
            LOG.info("event=audit_staged kind=login customerId={} outcome={}", e.customerId(), e.outcome());
        } else if (event instanceof DomainEvent.FinancialOutcome e) {
            TransactionAudit record = new TransactionAudit(e.customerId(), e.reference(), e.operation(), e.outcome(), e.failureCode());
            audit.transaction(c, record.customerId(), record.reference(), record.eventType(), record.outcome().name(), record.failureCode());
            LOG.info("event=audit_staged kind={} customerId={} reference={} outcome={} code={}", e.operation(), e.customerId(), e.reference(), e.outcome(), e.failureCode());
        } else if (event instanceof DomainEvent.ProfileChanged e) {
            audit.transaction(c, e.customerId(), null, e.operation(), "SUCCESS", null);
            LOG.info("event=audit_staged kind={} customerId={} outcome=SUCCESS", e.operation(), e.customerId());
        }
    }
}
