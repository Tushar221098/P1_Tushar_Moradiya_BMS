# Secure Banking CLI

A Java 17 console application using Maven, JDBC, PostgreSQL, BCrypt, SLF4J/Logback, JUnit 5, Mockito and JaCoCo.

## Database setup

1. Create a database named `Banking_CLI` in pgAdmin.
2. Connect the Query Tool to that database and run `sql/postgres/schema.sql` as its owner.
3. For a separate application role, create a LOGIN role named `banking_app`, choose its password locally, and run `sql/postgres/grant-app-role.sql` as the owner.
4. The current `schema.sql` also corrects generated account IDs in older detail tables and adds the audit-outcome constraint if missing. Both corrections are already applied to the connected development database. No separate fix scripts are required. This is not a general migration engine.

Never drop your existing database to install this version. Existing account numbers remain usable. New accounts receive 12-digit numbers with a Luhn check digit; uniqueness is also enforced by PostgreSQL.

## Run

Open the folder containing `pom.xml` in IntelliJ and select JDK 17. Run `com.revature.banking.Main` with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` in its run configuration. Do not commit credentials.

PowerShell alternative (from the project directory):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:DB_URL = 'jdbc:postgresql://localhost:5432/Banking_CLI'
$env:DB_USERNAME = 'banking_app'
$secret = Read-Host 'Database password' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
mvn clean package
java -jar target/banking-cli.jar
Remove-Item Env:DB_PASSWORD
```

If PowerShell cannot find Maven, add your Maven installation's `bin` directory to PATH and restart the terminal. Run `mvn -version` to confirm it uses Java 17. Run Maven beside `pom.xml`, not from your home directory.

## Menu behavior

- Register, then log in. Password requirements appear before new-password entry.
- Use `0` to go back or cancel a form. In the customer menu it logs out and returns to the main menu. There is no Back option in the main menu.
- Deposit, withdrawal, repayment and settlement show numbered eligible accounts. Transfers show your source accounts and accept another customer's recipient account number.
- Check balance displays all of your balances at once. Loans show outstanding debt separately from cash.
- Create Savings/Current accounts at zero balance, then use Deposit to add simulated money.
- Term deposits are funded from your own Savings/Current account. Minimum principal is 100.00. They pay out only on or after maturity into an owned active cash account, then close.
- Simulated loans create debt without paying out cash. Repayments reduce principal; overpayments are rejected; exact repayment closes the loan. There is no interest or EMI engine in this P1 baseline.
- History contains completed money movement. `ALL` includes all transaction types, not failed attempts. Rejected attempts are in `transaction_audit`; use **14. Check transaction result** with their TX- reference.
- **15. Edit profile** updates name, email and phone only. Password changes require the current password and are audited.
- **13. Portfolio overview** separates active cash, unavailable cash in inactive Savings/Current accounts, term principal and loan debt.

## Session and configuration

`src/main/resources/app.properties` contains non-secret settings. Credentials come from environment variables.

| Setting | Current value |
| --- | --- |
| Idle timeout | 120 seconds (the user's requested change from the PDF's 300-second default) |
| Query/lock timeout | 10 seconds |
| Maximum operation amount | 1,000,000.00 |
| Minimum term deposit | 100.00 |
| Lockout | 5 failures, 15 minutes |
| BCrypt cost | 12 |
| Maximum history page size | 50 |

A prompt displays a static time snapshot, for example `[Idle 02:00] Amount:`. Submitted input refreshes activity. The snapshot does not repaint over typing. A daemon prints an expiry notice automatically at the deadline; blocking console input returns after Enter, at which point the expired operation is rejected and TIMEOUT is audited. Logout clears the session.

All application-created JDBC statements have query timeouts. PostgreSQL session-level statement/lock limits also cover autocommit reads. Financial operations use READ COMMITTED, ordered row locks, one commit, and full rollback on failure. Deadlock/lock-timeout retries use bounded randomized backoff. An unconfirmed commit is never retried automatically.

## Logs and audit

`logs/banking.log` holds rolling application diagnostics. Console logging is limited to WARN/ERROR with short messages. Driver error messages and SQL parameters are not logged; sanitized stack locations and SQLSTATE are preserved. No passwords, hashes or full account numbers belong in technical logs.

Audit rows record login outcomes, financial outcomes, profile edits and password changes. A synchronous `EventPublisher` dispatches immutable events to an audit subscriber. Success audit is staged in the same transaction as the change, so both commit together. Rejected/failed work is audited on a fresh connection after rollback. The `audit_staged` log label describes staging, not proof of commit. Uncertain outcomes can be checked by TX- reference.

## Tests and coverage

Without database configuration:

```powershell
mvn clean verify
```

This runs 31 non-database tests. The PostgreSQL suite requires a dedicated test database.

Create `Banking_CLI_test` in pgAdmin. Give the test role CONNECT and CREATE privileges on that database; do not use the application database as the test target.

```powershell
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/Banking_CLI_test'
$env:TEST_DB_USERNAME = 'your_test_role'
$testSecret = Read-Host 'Test database password' -AsSecureString
$env:TEST_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $testSecret).Password
mvn -Pdatabase-tests clean verify
Remove-Item Env:TEST_DB_PASSWORD
```

The database-tests profile fails when test settings are missing rather than silently accepting a partial run. The suite creates a fresh, randomly named schema, runs the actual schema SQL, and removes that schema afterwards. It tests real rollback, concurrent withdrawals/transfers, lockout, ownership, profile/password auditing, history, maturity, repayment and lost commit acknowledgement. Its CLI acceptance test registers a customer and exercises the menus through logout.

Latest full verification: **51 tests passed, 0 failures/errors, 0 skipped** (31 non-database and 20 PostgreSQL). JaCoCo measured **87.9% line coverage** and **72.8% branch coverage** across the application, without excluding presentation or infrastructure classes. These values describe that run, not a promise of complete correctness.

Reports generated by `verify`:

- `target/surefire-reports`: individual test results.
- `target/site/jacoco/index.html`: interactive coverage report.
- `target/site/jacoco/jacoco.xml`: machine-readable coverage.
- `target/acceptance-cli.txt`: transcript from the automated CLI acceptance test (fake customers only).

Coverage uses the official [JaCoCo Maven plugin](https://www.jacoco.org/jacoco/trunk/doc/maven.html).

## Optional local demonstration data

No shared passwords or password hashes are distributed in the seed SQL. To use its fixtures, register `demo_avery` and `demo_jordan` through the CLI with passwords you choose, then run `sql/postgres/data.sql` in a local demonstration database. If those usernames do not exist, their fixture rows are not inserted. The reserved `DEMO...` account numbers are legacy fixtures, not the format for new accounts. The script is optional and is not part of production setup.

## Code structure and design

| Package | Responsibility |
| --- | --- |
| `app` | Input, numbered navigation, boxed headings and tables |
| `model`, `model.policy` | Account hierarchy, immutable money/identifiers/terms/audit records, eligibility and financial invariants |
| `service`, `service.event` | Use cases, transaction orchestration, profile/session services, synchronous event delivery |
| `dao`, `dao.jdbc` | Repository contracts, parameterized SQL, mapping, login/financial audit repositories, statement settings |
| `security` | BCrypt, session context and session manager |
| `validation` | Input, customer, account and transaction validation |
| `exception` | Typed domain/security/validation/persistence failures |
| `util` | Configuration, connection/transaction lifecycle, identifier generation, backoff and safe diagnostics |

Account changes return new `Money` values; there is no public balance setter. JDBC restores the correct Savings/Current/Deposit/Loan subtype. Domain classes reject invalid operations; the service repeats authoritative checks under row locks. The menu does not decide funds or maturity rules. Identifier generation, events, Clock and backoff are injectable for tests.

Intentional design refinements: PostgreSQL is the selected supported dialect; history uses stable cursor pagination instead of OFFSET; audit events use compact immutable outcome records rather than one class per event name; mandatory success audit shares the money transaction. See `FUNCTIONALITY_CHECK.md` for full verification results, `DOCUMENT_ALIGNMENT_REVIEW.md` for traceability to the PDF and `DEMO_AND_SELLING_POINTS.md` for evaluation steps.

## Submission

Review the implementation and be prepared to explain password hashing, ownership checks, transaction rollback, retries and account-type rules during evaluation. Keep a meaningful, reviewed Git history.
