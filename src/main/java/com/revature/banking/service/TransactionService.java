package com.revature.banking.service;

import com.revature.banking.model.*;

import com.revature.banking.validation.Validation;

import com.revature.banking.exception.BankingException;

import com.revature.banking.dao.AccountDAO;
import com.revature.banking.dao.AuditDAO;
import com.revature.banking.dao.TransactionDAO;
import com.revature.banking.model.Account;
import com.revature.banking.model.AccountType;
import com.revature.banking.model.HistoryEntry;
import com.revature.banking.model.TransactionType;
import com.revature.banking.util.AppConfig;
import com.revature.banking.util.JdbcTransaction;
import com.revature.banking.util.ReferenceGenerator;
import com.revature.banking.util.IdentifierGenerator;
import com.revature.banking.util.RetryBackoff;
import com.revature.banking.service.event.*;
import com.revature.banking.model.policy.TransferPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static com.revature.banking.model.AccountStatus.*;
import static com.revature.banking.model.TransactionType.*;
import static com.revature.banking.service.AccountService.ZERO;

public final class TransactionService {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionService.class);
    private final IdentifierGenerator identifiers;
    private final RetryBackoff backoff;
    private final EventPublisher events;
    private final AccountDAO accounts;
    private final TransactionDAO ledger;
    private final AuditDAO audit;
    private final JdbcTransaction tx;
    private final AppConfig config;
    private final Clock clock;

    public TransactionService(AccountDAO accounts, TransactionDAO ledger, AuditDAO audit,
                              JdbcTransaction tx, AppConfig config, Clock clock) {
        this(accounts, ledger, audit, tx, config, clock, new ReferenceGenerator(), RetryBackoff.randomized(),
                new InProcessEventPublisher(List.of(new AuditSubscriber(audit))));
    }

    public TransactionService(AccountDAO accounts, TransactionDAO ledger, AuditDAO audit,
                              JdbcTransaction tx, AppConfig config, Clock clock, IdentifierGenerator identifiers,
                              RetryBackoff backoff, EventPublisher events) {
        this.identifiers = identifiers;
        this.backoff = backoff;
        this.events = events;
        this.accounts = accounts;
        this.ledger = ledger;
        this.audit = audit;
        this.tx = tx;
        this.config = config;
        this.clock = clock;
    }

    public String deposit(long owner, String number, String input)
            throws SQLException {
        return financial(owner, Operation.DEPOSIT, number, null, input, 0).reference();
    }

    private void depositInTransaction(Connection c, String ref, long owner, String number, String input)
            throws SQLException {
        BigDecimal amount = amount(input);
        Account a = lockOwned(c, owner, number);
        requireCash(a);
        BigDecimal after = a.credit(new Money(amount)).value();
        accounts.update(c, a, after, ACTIVE);
        insertLedger(c, ref, DEPOSIT, null, a.id(), amount, null, after, null);
    }

    public String withdraw(long owner, String number, String input)
            throws SQLException {
        return financial(owner, Operation.WITHDRAWAL, number, null, input, 0).reference();
    }

    private void withdrawInTransaction(Connection c, String ref, long owner, String number, String input)
            throws SQLException {
        BigDecimal amount = amount(input);
        Account a = lockOwned(c, owner, number);
        requireCash(a);
        BigDecimal after = debit(a, amount);
        accounts.update(c, a, after, ACTIVE);
        insertLedger(c, ref, WITHDRAWAL, a.id(), null, amount, after, null, null);
    }

    public String transfer(long owner, String from, String to, String input)
            throws SQLException {
        return financial(owner, Operation.TRANSFER, from, to, input, 0).reference();
    }

    private void transferInTransaction(Connection c, String ref, long owner, String from, String to, String input)
            throws SQLException {
        BigDecimal amount = amount(input);
        Pair pair = lockPair(c, owner, from, to, false);
        new TransferPolicy().validate(pair.source(), pair.destination());
        move(c, ref, TRANSFER, pair, amount, pair.destination().credit(new Money(amount)).value(),
                ACTIVE, ACTIVE, null);
    }

    public static class OpenedAccount {
        private final String number;
        private final String reference;

        public OpenedAccount(String number, String reference) {
            this.number = number;
            this.reference = reference;
        }

        public String number() {
            return number;
        }

        public String reference() {
            return reference;
        }
    }

    public OpenedAccount openDeposit(long owner, String funding, String input, int months)
            throws SQLException {
        return financial(owner, Operation.OPEN_DEPOSIT, funding, null, input, months);
    }

    private String openDepositInTransaction(Connection c, String ref, long owner, String funding, String input, int months)
            throws SQLException {
        BigDecimal principal = amount(input);
        DepositTerms terms = DepositTerms.open(new Money(principal), months, LocalDate.now(clock));
        if (principal.compareTo(config.minDeposit()) < 0) {
            throw reject("DEPOSIT_MINIMUM", "Minimum term deposit is " + config.minDeposit() + ".");
        }
        Account source = lockOwned(c, owner, funding);
        requireCash(source);
        BigDecimal sourceAfter = debit(source, principal);
        Account deposit = accounts.create(c, owner, identifiers.nextAccountNumber(),
                AccountType.DEPOSIT, principal);
        accounts.addDepositTerms(c, deposit.id(), principal, terms.months(), terms.maturityDate());
        accounts.update(c, source, sourceAfter, ACTIVE);
        insertLedger(c, ref, TRANSFER, source.id(), deposit.id(), principal,
                sourceAfter, principal, "Term deposit opening");
        return deposit.number();
    }

    public String settleDeposit(long owner, String number, String destination)
            throws SQLException {
        return financial(owner, Operation.SETTLE_DEPOSIT, number, destination, null, 0).reference();
    }

    private void settleDepositInTransaction(Connection c, String ref, long owner, String number, String destination)
            throws SQLException {
        Pair pair = lockPair(c, owner, number, destination, true);
        Account source = pair.source();
        requireCash(pair.destination());
        if (!source.canSettle(LocalDate.now(clock))) {
            throw reject("DEPOSIT_UNAVAILABLE", "Only a matured term deposit can be settled.");
        }
        BigDecimal principal = source.settle(LocalDate.now(clock)).value();
        move(c, ref, TRANSFER, pair, principal, pair.destination().credit(new Money(principal)).value(),
                CLOSED, ACTIVE, "Term deposit settlement");
    }

    public OpenedAccount openLoan(long owner, String input, int months)
            throws SQLException {
        return financial(owner, Operation.OPEN_LOAN, null, null, input, months);
    }

    private String openLoanInTransaction(Connection c, long owner, String input, int months)
            throws SQLException {
        BigDecimal principal = amount(input);
        new LoanTerms(new Money(principal), months, LocalDate.now(clock));
        Account loan = accounts.create(c, owner, identifiers.nextAccountNumber(), AccountType.LOAN, principal);
        accounts.addLoanTerms(c, loan.id(), principal, months, LocalDate.now(clock));
        return loan.number();
    }

    public String repayLoan(long owner, String funding, String number, String input)
            throws SQLException {
        return financial(owner, Operation.LOAN_REPAYMENT, funding, number, input, 0).reference();
    }

    private void repayLoanInTransaction(Connection c, String ref, long owner, String funding, String number, String input)
            throws SQLException {
        BigDecimal amount = amount(input);
        Pair pair = lockPair(c, owner, funding, number, true);
        requireCash(pair.source());
        Account loan = pair.destination();
        BigDecimal after = loan.repay(new Money(amount)).value();
        move(c, ref, LOAN_REPAYMENT, pair, amount, after, ACTIVE,
                after.compareTo(ZERO) == 0 ? CLOSED : ACTIVE, null);
    }

    public List<HistoryEntry> history(long owner, String number, TransactionType type, OffsetDateTime from,
                                      OffsetDateTime before, HistoryEntry cursor, int size) throws SQLException {
        com.revature.banking.validation.TransactionValidator.history(size, config.maxPageSize(), from, before);
        try (Connection c = tx.open()) {
            Account a = accounts.owned(c, owner, number);
            if (a == null) throw unavailable();
            return ledger.history(c, a.id(), type, from, before, cursor, size + 1);
        }
    }

    public AuditDAO.OperationStatus operationStatus(long owner, String reference) throws SQLException {
        new TransactionReference(reference);
        try (Connection c = tx.open()) {
            return audit.operationStatus(c, owner, reference);
        }
    }

    private void move(Connection c, String ref, TransactionType type, Pair pair, BigDecimal amount,
                      BigDecimal destAfter, AccountStatus sourceStatus, AccountStatus destStatus,
                      String description) throws SQLException {
        // Both balance updates and this history insert use the same JDBC connection.
        BigDecimal sourceAfter = pair.source().type() == AccountType.DEPOSIT
                ? pair.source().settle(LocalDate.now(clock)).subtract(new Money(amount)).value()
                : pair.source().debit(new Money(amount)).value();
        accounts.update(c, pair.source(), sourceAfter, sourceStatus);
        accounts.update(c, pair.destination(), destAfter, destStatus);
        insertLedger(c, ref, type, pair.source().id(), pair.destination().id(),
                amount, sourceAfter, destAfter, description);
    }

    private void insertLedger(Connection c, String reference, TransactionType type, Long source, Long destination,
                              BigDecimal amount, BigDecimal sourceAfter, BigDecimal destinationAfter, String description) throws SQLException {
        BankTransaction entry = new BankTransaction(new TransactionReference(reference), type, source, destination,
                new Money(amount), sourceAfter == null ? null : new Money(sourceAfter),
                destinationAfter == null ? null : new Money(destinationAfter), description);
        ledger.insert(c, entry.reference().value(), entry.type(), entry.sourceId(), entry.destinationId(),
                entry.amount().value(), sourceAfter, destinationAfter, entry.description());
    }

    private Account lockOwned(Connection c, long owner, String number) throws SQLException {
        Account owned = accounts.owned(c, owner, number);
        if (owned == null) throw unavailable();
        // Read the balance again under a row lock so another withdrawal cannot use the same money.
        Account locked = accounts.lock(c, owned.id());
        return com.revature.banking.validation.AccountValidator.owned(locked, owner);
    }

    private static class Pair {
        private final Account source;
        private final Account destination;

        private Pair(Account source, Account destination) {
            this.source = source;
            this.destination = destination;
        }

        private Account source() {
            return source;
        }

        private Account destination() {
            return destination;
        }
    }

    private Pair lockPair(Connection c, long owner, String from, String to, boolean bothOwned)
            throws SQLException {
        Account ownedSource = accounts.owned(c, owner, from);
        if (ownedSource == null) throw unavailable();
        long sourceId = ownedSource.id();
        Long destId;
        if (bothOwned) {
            Account ownedDest = accounts.owned(c, owner, to);
            destId = ownedDest == null ? null : ownedDest.id();
        } else {
            destId = accounts.idByNumber(c, to);
        }
        if (destId == null) throw unavailable();
        if (sourceId == destId) throw reject("SAME_ACCOUNT", "Choose two different accounts.");
        // Lock the smaller ID first in every transfer, even when the direction is reversed.
        Account first = accounts.lock(c, Math.min(sourceId, destId));
        Account second = accounts.lock(c, Math.max(sourceId, destId));
        Account source = sourceId < destId ? first : second;
        Account dest = sourceId < destId ? second : first;
        if (source == null || dest == null || source.ownerId() != owner
                || (bothOwned && dest.ownerId() != owner)) throw unavailable();
        return new Pair(source, dest);
    }

    private static BigDecimal debit(Account account, BigDecimal amount) {
        return account.debit(new Money(amount)).value();
    }

    private static void requireCash(Account account) {
        account.requireCash();
    }

    private BigDecimal amount(String input) {
        return com.revature.banking.validation.TransactionValidator.amount(input, config.maxAmount()).value();
    }

    private static BankingException unavailable() {
        return reject("ACCOUNT_UNAVAILABLE", "Account unavailable.");
    }

    private static BankingException reject(String code, String message) {
        return BankingException.of(code, message);
    }

    private enum Operation {
        DEPOSIT, WITHDRAWAL, TRANSFER, OPEN_DEPOSIT, SETTLE_DEPOSIT, OPEN_LOAN, LOAN_REPAYMENT
    }

    private OpenedAccount financial(long owner, Operation operation, String from, String to,
                                    String input, int months) throws SQLException {
        String reference = identifiers.nextTransactionReference();
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return postOnce(owner, operation, from, to, input, months, reference);
            } catch (JdbcTransaction.CommitUnconfirmed e) {
                throw new com.revature.banking.exception.PersistenceException("COMMIT_UNCONFIRMED",
                        "Outcome could not be confirmed. Check the TX- reference before retrying: " + reference, e);
            } catch (BankingException e) {
                failure(owner, reference, operation, "REJECTED", e.code());
                throw e.withReference(reference);
            } catch (SQLException e) {
                boolean rolledBack = e instanceof JdbcTransaction.RolledBack;
                // Retrying is safe only when rollback succeeded and the error was transient.
                if (rolledBack && attempt < 3 && retryable(e.getSQLState())) {
                    LOG.warn("event=transaction_retry reference={} attempt={} sqlState={}", reference, attempt, e.getSQLState());
                    backoff.pause(attempt);
                    continue;
                }
                com.revature.banking.util.SafeLog.database(LOG, "transaction_failed", reference, e);
                failure(owner, reference, operation, rolledBack ? "ROLLED_BACK" : "FAILED", "DATABASE_ERROR");
                throw e;
            }
        }
        throw new IllegalStateException("No transaction attempt completed.");
    }

    private OpenedAccount postOnce(long owner, Operation operation, String from, String to,
                                   String input, int months, String ref) throws SQLException {
        Connection c = tx.begin();
        try {
            String number = null;
            try {
                switch (operation) {
                    case DEPOSIT -> depositInTransaction(c, ref, owner, from, input);
                    case WITHDRAWAL -> withdrawInTransaction(c, ref, owner, from, input);
                    case TRANSFER -> transferInTransaction(c, ref, owner, from, to, input);
                    case OPEN_DEPOSIT -> number = openDepositInTransaction(c, ref, owner, from, input, months);
                    case SETTLE_DEPOSIT -> settleDepositInTransaction(c, ref, owner, from, to);
                    case OPEN_LOAN -> number = openLoanInTransaction(c, owner, input, months);
                    case LOAN_REPAYMENT -> repayLoanInTransaction(c, ref, owner, from, to, input);
                }
                events.publish(c, new DomainEvent.FinancialOutcome(owner, ref, operation.name(), AuditOutcome.SUCCESS, null));
            } catch (SQLException e) {
                if (tx.rollback(c, e)) throw new JdbcTransaction.RolledBack(e);
                throw e;
            } catch (RuntimeException e) {
                tx.rollback(c, e);
                throw e;
            }

            // Commit only after the balances, history and success audit have all been saved.
            tx.commit(c);
            return new OpenedAccount(number, ref);
        } finally {
            tx.close(c);
        }
    }

    private boolean retryable(String state) {
        return "40P01".equals(state) || "55P03".equals(state) || "40001".equals(state);
    }

    private void failure(long owner, String ref, Operation event, String outcome, String code) {
        // Record the failure on a fresh connection so it survives the rollback.
        try (Connection c = tx.open()) {
            events.publish(c, new DomainEvent.FinancialOutcome(owner, ref, event.name(), AuditOutcome.valueOf(outcome), code));
        } catch (SQLException e) {
            com.revature.banking.util.SafeLog.database(LOG, "audit_failed", ref, e);
        }
    }
}
