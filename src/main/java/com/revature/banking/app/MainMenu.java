package com.revature.banking.app;

import java.util.Scanner;

public class MainMenu {
    public void show() {
        Scanner scn = new Scanner(System.in);
        boolean running = true;

        while(running){
            System.out.println("\nWelcome to Banking CLI Application: " +
                    "\n|====MAIN MENU====|" +
                    "\n\t1: Register" +
                    "\n\t2: Login" +
                    "\n\t3: Exit" +
                    "\nEnter your choice: ");
            String choice = scn.nextLine();

            switch (choice) {
                case "1":
                    System.out.println("Registration selected.");
                    BankingApplication registrationMenu = new BankingApplication();
                    registrationMenu.show(scn);
                    break;
                case "2":
                    System.out.println("Login selected.");
                    break;
                case "3":
                    System.out.println("Thank you. Goodbye!");
                    running = false;
                    break;
                default:
                    System.out.println("Invalid choice! Please select 1, 2, or 3.");
                    break;
            }
        }
    }
}
