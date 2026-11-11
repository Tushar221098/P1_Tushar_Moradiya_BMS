package com.revature.banking;

import com.revature.banking.app.BankingApplication;
import com.revature.banking.dao.jdbc.JdbcAccountDAO;
import com.revature.banking.dao.jdbc.JdbcAuditDAO;
import com.revature.banking.dao.jdbc.JdbcCustomerDAO;
import com.revature.banking.dao.jdbc.JdbcTransactionDAO;
import com.revature.banking.dao.AccountDAO;
import com.revature.banking.dao.AuditDAO;
import com.revature.banking.security.BcryptPasswordHasher;
import com.revature.banking.service.AccountService;
import com.revature.banking.service.AuthService;
import com.revature.banking.service.TransactionService;
import com.revature.banking.util.AppConfig;
import com.revature.banking.util.ConnectionFactory;
import com.revature.banking.util.JdbcTransaction;

import java.time.Clock;

public final class Main {
    public static void main(String[] args) {
        AppConfig config;
        ConnectionFactory connections;
        try {
            config = AppConfig.load();
            connections = ConnectionFactory.fromEnvironment();
        } catch (IllegalStateException | IllegalArgumentException | ArithmeticException e) {
            System.err.println("Startup failed. Check DB_URL, DB_USERNAME, DB_PASSWORD and app.properties.");
            System.exit(1);
            return;
        }

        Clock clock = Clock.systemUTC();
        JdbcTransaction tx = new JdbcTransaction(connections, config.querySeconds());
        AccountDAO accountDAO = new JdbcAccountDAO();
        AuditDAO audit = new JdbcAuditDAO();
        AuthService auth = new AuthService(new JdbcCustomerDAO(), audit, tx, config, clock,
                new BcryptPasswordHasher(config.bcryptCost()));
        AccountService accounts = new AccountService(accountDAO, tx, clock);
        TransactionService transactions = new TransactionService(accountDAO, new JdbcTransactionDAO(),
                audit, tx, config, clock);
        new BankingApplication(auth, accounts, transactions, config, clock).run();
    }
}
