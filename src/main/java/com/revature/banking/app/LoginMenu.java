package com.revature.banking.app;

import com.revature.banking.security.SessionContext;
import com.revature.banking.service.AccountService;
import com.revature.banking.service.AuthService;

import java.sql.SQLException;

final class LoginMenu {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LoginMenu.class);
    private final AuthService auth;
    private final AccountService accounts;
    private final CustomerMenu customerMenu;
    private final ConsoleInput in;

    LoginMenu(AuthService auth, AccountService accounts, CustomerMenu customerMenu, ConsoleInput in) {
        this.auth = auth;
        this.accounts = accounts;
        this.customerMenu = customerMenu;
        this.in = in;
    }

    void show() throws SQLException {
        in.heading("LOGIN");
        String username = in.line("Username: ");
        SessionContext session = auth.login(username, in.password("Password: "));
        if (session == null) {
            in.println("Invalid username or password.");
            return;
        }
        in.session(session);
        try {
            // Refresh maturity on entry; listing and balance enquiry also refresh it.
            accounts.list(session.customerId());
            in.println("Login successful.");
            // Pass the ID from the verified session, never from keyboard input.
            customerMenu.show(session.customerId());
        } catch (ConsoleInput.Expired e) {
            in.println("Session expired. Please log in again.");
        } finally {
            session.logout();
            in.session(null);
            try {
                auth.end(session);
            } catch (SQLException e) {
                com.revature.banking.util.SafeLog.database(LOG, "session_audit", null, e);
            }
        }
    }

}
