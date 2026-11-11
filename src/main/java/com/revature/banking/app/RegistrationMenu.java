package com.revature.banking.app;

import com.revature.banking.service.AuthService;
import com.revature.banking.validation.Validation;

import java.sql.SQLException;

final class RegistrationMenu {
    private final AuthService auth;
    private final ConsoleInput in;

    RegistrationMenu(AuthService auth, ConsoleInput in) {
        this.auth = auth;
        this.in = in;
    }

    void show() throws SQLException {
        in.heading("REGISTER");
        String name = in.line("Full name: ");
        String email = in.line("Email: ");
        String phone = in.line("Phone (optional): ");
        String username = in.line("Username: ");
        in.println(Validation.PASSWORD_REQUIREMENTS);
        char[] password = in.confirmedPassword();
        long id = auth.register(name, email, phone, username, password);
        in.println("Registration successful. Customer ID: " + id);
    }

}
