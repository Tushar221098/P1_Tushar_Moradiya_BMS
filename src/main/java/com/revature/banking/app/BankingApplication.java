package com.revature.banking.app;

import com.revature.banking.service.AccountService;
import com.revature.banking.service.AuthService;
import com.revature.banking.exception.BankingException;
import com.revature.banking.service.TransactionService;
import com.revature.banking.util.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Clock;

public final class BankingApplication {
    private static final Logger LOG = LoggerFactory.getLogger(BankingApplication.class);
    private final RegistrationMenu registrationMenu;
    private final LoginMenu loginMenu;
    private final ConsoleInput in;

    public BankingApplication(AuthService auth, AccountService accounts,
                              TransactionService transactions, AppConfig config, Clock clock) {
        in = new ConsoleInput(System.console(), System.in, System.out, clock, config.idle());
        CustomerMenu customerMenu = new CustomerMenu(auth, accounts, transactions, config, in);
        registrationMenu = new RegistrationMenu(auth, in);
        loginMenu = new LoginMenu(auth, accounts, customerMenu, in);
    }

    public void run() {
        if (System.console() == null) {
            say("This console may echo password input. Use a terminal for hidden entry.");
        }
        try {
            while (true) {
                in.heading("WELCOME TO BMS", 0);
                say("""
                        1. Register
                        2. Login
                        3. Exit""");
                try {
                    switch (in.line("Choice: ")) {
                        case "1" -> registrationMenu.show();
                        case "2" -> loginMenu.show();
                        case "3" -> {
                            say("Goodbye.");
                            return;
                        }
                        default -> say("Choose 1, 2 or 3.");
                    }
                } catch (ConsoleInput.Back e) {
                    say("Returning to the main menu.");
                } catch (BankingException e) {
                    LOG.warn("event=request_rejected code={} reference={}", e.code(), e.reference());
                    say(e.getMessage());
                } catch (SQLException e) {
                    databaseError(e);
                }
            }
        } catch (ConsoleInput.EndOfInput e) {
            say("\nInput closed. Goodbye.");
        }
    }

    private void say(String text) {
        in.println(text);
    }

    private void databaseError(SQLException e) {
        com.revature.banking.util.SafeLog.database(LOG, "database_operation", null, e);
        say("The database operation could not be completed. Please try again.");
    }
}
