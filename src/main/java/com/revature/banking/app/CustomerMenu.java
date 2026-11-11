package com.revature.banking.app;

import com.revature.banking.validation.Validation;

import com.revature.banking.model.Account;
import com.revature.banking.model.AccountUse;
import com.revature.banking.model.AccountType;
import com.revature.banking.model.HistoryEntry;
import com.revature.banking.model.TransactionType;
import com.revature.banking.dao.AuditDAO;
import com.revature.banking.model.Customer;
import com.revature.banking.service.AccountService;
import com.revature.banking.service.AuthService;
import com.revature.banking.exception.BankingException;
import com.revature.banking.service.TransactionService;
import com.revature.banking.util.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;


final class CustomerMenu {
    private static final Logger LOG = LoggerFactory.getLogger(CustomerMenu.class);
    private final AuthService auth;
    private final AccountService accounts;
    private final TransactionService transactions;
    private final AppConfig config;
    private final ConsoleInput in;

    CustomerMenu(AuthService auth, AccountService accounts, TransactionService transactions,
                 AppConfig config, ConsoleInput in) {
        this.auth = auth;
        this.accounts = accounts;
        this.transactions = transactions;
        this.config = config;
        this.in = in;
    }

    void show(long owner) {
        int invalidChoices = 0;
        while (true) {
            in.heading("CUSTOMER MENU");
            say("""
                    1. View profile
                    2. Change password
                    3. Create account
                    4. List my accounts
                    5. Deposit
                    6. Withdraw
                    7. Transfer funds
                    8. Repay loan
                    9. Check balance
                    10. Transaction history
                    12. Settle matured term deposit
                    13. Portfolio overview
                    14. Check transaction result
                    15. Edit profile
                    0. Logout / Main menu""");
            String choice = in.line("Choice: ");
            if (choice.equals("0")) return;
            if (!choice.matches("([1-9]|10|1[2-5])")) {
                say("Choose a listed option, or 0 to log out.");
                if (++invalidChoices == 3) {
                    say("Returning to the main menu.");
                    return;
                }
                continue;
            }
            invalidChoices = 0;
            String[] headings = {"VIEW PROFILE", "CHANGE PASSWORD", "CREATE ACCOUNT",
                    "MY ACCOUNTS", "DEPOSIT", "WITHDRAW", "TRANSFER FUNDS", "REPAY LOAN",
                    "ACCOUNT BALANCE", "TRANSACTION HISTORY", "LOGOUT",
                    "SETTLE TERM DEPOSIT", "PORTFOLIO OVERVIEW", "TRANSACTION RESULT", "EDIT PROFILE"};
            if (!choice.equals("3")) in.heading(headings[Integer.parseInt(choice) - 1], 2);
            try {
                switch (choice) {
                    case "1" -> profile(owner);
                    case "2" -> changePassword(owner);
                    case "3" -> openAccount(owner);
                    case "4" -> {
                        List<Account> list = accounts.list(owner);
                        if (list.isEmpty()) say("You have no accounts yet.");
                        else showAccounts(list);
                    }
                    case "5" -> {
                        Account account = chooseAccount(owner, AccountUse.CASH, "Deposit into:");
                        receipt(transactions.deposit(owner, account.number(), in.line("Amount: ")));
                    }
                    case "6" -> {
                        Account account = chooseAccount(owner, AccountUse.CASH, "Withdraw from:");
                        receipt(transactions.withdraw(owner, account.number(), in.line("Amount: ")));
                    }
                    case "7" -> {
                        Account source = chooseAccount(owner, AccountUse.CASH, "Pay from:");
                        List<Account> ownRecipients = accounts.choices(owner, AccountUse.CASH, config.minDeposit()).stream()
                                .filter(a -> !a.number().equals(source.number())).toList();
                        if (!ownRecipients.isEmpty()) {
                            say("Your other accounts:");
                            showAccounts(ownRecipients);
                        }
                        say("Enter your other account number or another customer's account number.");
                        String to = number("Recipient account number: ");
                        receipt(transactions.transfer(owner, source.number(), to, in.line("Amount: ")));
                    }
                    case "8" -> {
                        Account loan = chooseAccount(owner, AccountUse.REPAYMENT, "Loan to repay:");
                        Account source = chooseAccount(owner, AccountUse.CASH, "Pay from:");
                        receipt(transactions.repayLoan(owner, source.number(), loan.number(), in.line("Amount: ")));
                    }
                    case "9" -> {
                        List<Account> all = accounts.list(owner);
                        if (all.isEmpty()) say("You have no accounts yet.");
                        else showAccounts(all);
                    }
                    case "10" -> history(owner);
                    case "12" -> {
                        Account deposit = chooseAccount(owner, AccountUse.SETTLEMENT, "Matured deposit:");
                        Account destination = chooseAccount(owner, AccountUse.CASH, "Pay into:");
                        receipt(transactions.settleDeposit(owner, deposit.number(), destination.number()));
                    }
                    case "13" -> portfolio(owner);
                    case "14" -> operationStatus(owner);
                    case "15" -> editProfile(owner);
                    default -> throw new AssertionError(choice);
                }
            } catch (ConsoleInput.Back e) {
                say("Returning to the customer menu.");
            } catch (BankingException e) {
                if ("NO_ACCOUNTS".equals(e.code()))
                    LOG.info("event=no_accounts code={}", e.code());
                else LOG.warn("event=request_rejected code={} reference={}", e.code(), e.reference());
                say(e.getMessage());
            } catch (SQLException e) {
                databaseError(e);
            }
        }
    }

    private void profile(long owner) throws SQLException {
        Customer.Profile profile = auth.profile(owner);
        if (profile == null) {
            say("Profile unavailable.");
            return;
        }
        in.table(List.of("FIELD", "DETAILS"), List.of(
                List.of("Name", safeDisplay(profile.name())),
                List.of("Username", safeDisplay(profile.username())),
                List.of("Email", safeDisplay(profile.email())),
                List.of("Phone", profile.phone() == null ? "(none)" : safeDisplay(profile.phone()))));
    }

    private void editProfile(long owner) throws SQLException {
        String name = in.line("Full name: ");
        String email = in.line("Email: ");
        String phone = in.line("Phone (optional): ");
        auth.updateProfile(owner, name, email, phone);
        say("Profile updated.");
    }

    private static String safeDisplay(String text) {
        return text.replaceAll("[\\p{Cc}\\p{Cf}]", "?");
    }

    private void changePassword(long owner) throws SQLException {
        char[] current = in.password("Current password: ");
        try {
            say(com.revature.banking.validation.Validation.PASSWORD_REQUIREMENTS);
            char[] replacement = in.confirmedPassword();
            auth.changePassword(owner, current, replacement);
            say("Password changed.");
        } finally {
            Arrays.fill(current, '\0');
        }
    }

    private void openAccount(long owner) throws SQLException {
        while (true) {
            in.heading("CREATE ACCOUNT", 2);
            say("1. Savings\n2. Current\n3. Term deposit\n4. Simulated loan\n0. Back to customer menu");
            String choice = in.line("Account type: ");
            if (choice.equals("0")) return;
            try {
                switch (choice) {
                    case "1" -> showAccount(accounts.openCash(owner, AccountType.SAVINGS));
                    case "2" -> showAccount(accounts.openCash(owner, AccountType.CURRENT));
                    case "3" -> {
                        in.heading("OPEN TERM DEPOSIT", 3);
                        say("Choose an account to pay for this deposit.");
                        Account source = chooseAccount(owner, AccountUse.DEPOSIT_FUNDING, "Pay from:");
                        say("Minimum deposit: " + config.minDeposit());
                        TransactionService.OpenedAccount opened = transactions.openDeposit(owner, source.number(),
                                in.line("Deposit amount: "), in.integer("Duration (months): "));
                        say("Term deposit created: " + opened.number());
                        receipt(opened.reference());
                    }
                    case "4" -> {
                        say("Demo loan only. No money is paid out.");
                        TransactionService.OpenedAccount opened = transactions.openLoan(owner, in.line("Principal: "), in.integer("Tenure in months: "));
                        say("Loan record created: " + opened.number());
                        receipt(opened.reference());
                    }
                    default -> say("Unknown account type.");
                }
                return;
            } catch (ConsoleInput.Back e) {
                say("Returning to account types.");
            }
        }
    }

    private Account chooseAccount(long owner, AccountUse use, String prompt) throws SQLException {
        List<Account> choices = accounts.choices(owner, use, config.minDeposit());
        say(prompt);
        List<List<String>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < choices.size(); i++) {
            Account a = choices.get(i);
            rows.add(List.of(Integer.toString(i + 1), safeDisplay(a.number()), a.type().name(),
                    a.balance().toPlainString()));
        }
        in.table(List.of("CHOICE", "ACCOUNT NUMBER", "TYPE", "BALANCE / DEBT"), rows);
        say("0. Back");
        while (true) {
            int choice = in.integer("Choose account: ");
            if (choice >= 1 && choice <= choices.size()) return choices.get(choice - 1);
            say("Choose 1 to " + choices.size() + ", or 0 for Back.");
        }
    }

    private void showAccount(Account a) {
        showAccounts(List.of(a));
    }

    private void showAccounts(List<Account> accounts) {
        in.table(List.of("ACCOUNT NUMBER", "TYPE", "STATUS", "BALANCE", "LOAN OUTSTANDING", "MATURITY"),
                accounts.stream().map(a -> List.of(safeDisplay(a.number()), a.type().name(),
                        a.status().name(), a.type() == AccountType.LOAN ? "-" : a.balance().toPlainString(),
                        a.type() == AccountType.LOAN ? a.balance().toPlainString() : "-",
                        a.maturityDate() == null ? "-" : a.maturityDate().toString())).toList());
    }

    private void portfolio(long owner) throws SQLException {
        AccountService.Portfolio portfolio = accounts.portfolio(owner);
        in.table(List.of("CATEGORY", "AMOUNT"), List.of(
                List.of("Available cash", portfolio.availableCash().toPlainString()),
                List.of("Unavailable cash", portfolio.unavailableCash().toPlainString()),
                List.of("Term deposit principal (including matured)", portfolio.termDeposits().toPlainString()),
                List.of("Outstanding loan principal", portfolio.debt().toPlainString())));
    }

    private void operationStatus(long owner) throws SQLException {
        say("Check whether a transaction succeeded after an error.");
        say("Enter the TX- reference shown on your receipt or error message.");
        AuditDAO.OperationStatus status = transactions.operationStatus(owner, in.line("TX- reference: "));
        if (status == null) {
            say("No outcome recorded for this reference in your account. This does not confirm a rollback.");
            return;
        }
        in.table(List.of("EVENT", "OUTCOME", "TIME", "FAILURE CODE"),
                List.of(List.of(safeDisplay(status.event()), safeDisplay(status.outcome()),
                        status.time().toString(), status.failureCode() == null ? "-" : safeDisplay(status.failureCode()))));
    }

    private void history(long owner) throws SQLException {
        say("Completed transactions only. Rejected attempts: option 14 with the TX- reference.");
        String number = chooseAccount(owner, AccountUse.ALL, "History for:").number();
        String typeInput = in.line("Type (ALL/DEPOSIT/WITHDRAWAL/TRANSFER/LOAN_REPAYMENT): ");
        TransactionType type;
        try {
            type = typeInput.isEmpty() || typeInput.equalsIgnoreCase("ALL") ? null : TransactionType.valueOf(typeInput.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw BankingException.of("INVALID_TYPE", "Unknown transaction type.");
        }
        OffsetDateTime from = date("From UTC date (YYYY-MM-DD, optional): ");
        OffsetDateTime before = date("Before UTC date, exclusive (YYYY-MM-DD, optional): ");
        int pageSize = in.integer("Page size (1-" + config.maxPageSize() + "): ");
        HistoryEntry cursor = null;
        while (true) {
            List<HistoryEntry> entries = transactions.history(owner, number, type, from, before, cursor, pageSize);
            if (entries.isEmpty()) {
                say("No matching transactions.");
                return;
            }
            int count = Math.min(entries.size(), pageSize);
            in.table(List.of("TIME", "REFERENCE", "TYPE", "DIRECTION", "AMOUNT", "STATUS"),
                    entries.subList(0, count).stream().map(e -> List.of(e.time().toString(),
                            safeDisplay(e.reference()), e.type().toString(), e.direction().toString(),
                            e.amount().toPlainString(), safeDisplay(e.status()))).toList());
            if (entries.size() <= pageSize || !in.line("Next page? [y/N] ").equalsIgnoreCase("y")) return;
            cursor = entries.get(count - 1);
        }
    }

    private OffsetDateTime date(String prompt) {
        String text = in.line(prompt);
        if (text.isEmpty()) return null;
        try {
            return LocalDate.parse(text).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw BankingException.of("INVALID_DATE", "Enter a valid date as YYYY-MM-DD.");
        }
    }

    private String number(String prompt) {
        String number = in.line(prompt);
        if (number.isEmpty() || number.length() > 34) {
            throw BankingException.of("ACCOUNT_UNAVAILABLE", "Account unavailable.");
        }
        return number;
    }

    private void receipt(String reference) {
        say("Operation completed. Reference: " + reference);
    }

    private void say(String text) {
        in.println(text);
    }

    private void databaseError(SQLException e) {
        com.revature.banking.util.SafeLog.database(LOG, "database_operation", null, e);
        say("The database operation could not be completed. Please try again.");
    }
}
