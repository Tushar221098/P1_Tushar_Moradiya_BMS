-- Run as the schema owner after creating a LOGIN role named banking_app.
-- Target database: "Banking_CLI". Keep the role password out of SQL files.
GRANT CONNECT ON DATABASE "Banking_CLI" TO banking_app;
GRANT USAGE ON SCHEMA public TO banking_app;
GRANT SELECT, INSERT, UPDATE ON customers, accounts TO banking_app;
GRANT SELECT, INSERT ON deposit_details, loan_details, transactions, transaction_audit TO banking_app;
GRANT INSERT ON login_audit TO banking_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO banking_app;
