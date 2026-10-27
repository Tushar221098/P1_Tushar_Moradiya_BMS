package com.revature.banking.app;

import java.util.Scanner;

public class BankingApplication {

    public void show(Scanner scn) {
        System.out.println("\nWelcome to Bank Registration Menu");

        System.out.print("Please enter your full name: ");
        String fullName = scn.nextLine().trim();

        if (fullName.isBlank()) {
            System.out.println("Full name is required.");
            return;
        }

        System.out.print("\nPlease enter your email: ");
        String email = scn.nextLine().trim();

        if (email.isBlank()) {
            System.out.println("Email is required.");
            return;
        }

        System.out.print("\nPlease enter your phone number (optional): ");
        String phone = scn.nextLine().trim();

        System.out.print("\nPlease enter your username: ");
        String username = scn.nextLine().trim();

        if (username.isBlank()) {
            System.out.println("Username is required.");
            return;
        }

        System.out.print("\nPlease enter your password: ");
        String password = scn.nextLine();

        if (password.isBlank()) {
            System.out.println("Password is required.");
            return;
        }

        System.out.print("\nPlease re-enter your password: ");
        String confirmPassword = scn.nextLine();

        if (!password.equals(confirmPassword)) {
            System.out.println("Passwords do not match. Returning to the main menu.");
            return;
        }
        System.out.println("\nYour profile has been created.");
    }
}