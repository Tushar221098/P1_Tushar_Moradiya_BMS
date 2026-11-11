-- Demo data only. Run after schema.sql in a local copy of "Banking_CLI".
-- Optional account and ledger fixtures for customers registered locally.
-- Reserved demo usernames, account numbers and references make reruns safe.
BEGIN;

-- Register demo_avery and demo_jordan through the CLI using passwords you choose.
-- No customer credentials or password hashes are distributed in this seed file.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM customers WHERE username='demo_avery') THEN
        RAISE NOTICE 'Register demo_avery through the CLI before loading its demo accounts.';
    END IF;
END $$;

-- Look up the generated customer IDs instead of assuming they start at 1.
INSERT INTO accounts (customer_id, account_number, account_type, balance, status)
SELECT c.customer_id, d.account_number, d.account_type, d.balance, d.status
FROM (VALUES
    ('demo_avery',  'DEMO1001', 'SAVINGS', 900.00, 'ACTIVE'),
    ('demo_avery',  'DEMO1002', 'CURRENT', 350.00, 'ACTIVE'),
    ('demo_avery',  'DEMO1003', 'DEPOSIT', 300.00, 'MATURED'),
    ('demo_avery',  'DEMO1004', 'LOAN',    800.00, 'ACTIVE'),
    ('demo_jordan', 'DEMO2001', 'SAVINGS', 450.00, 'ACTIVE')
) AS d(username, account_number, account_type, balance, status)
JOIN customers c ON c.username = d.username
ON CONFLICT (account_number) DO NOTHING;

INSERT INTO deposit_details (account_id, principal, term_months, maturity_date)
SELECT account_id, 300.00, 6, CURRENT_DATE - 1
FROM accounts WHERE account_number = 'DEMO1003' AND account_type = 'DEPOSIT'
ON CONFLICT (account_id) DO NOTHING;

INSERT INTO loan_details (account_id, principal_amount, tenure_months, start_date)
SELECT account_id, 800.00, 12, CURRENT_DATE
FROM accounts WHERE account_number = 'DEMO1004' AND account_type = 'LOAN'
ON CONFLICT (account_id) DO NOTHING;

-- These entries explain the seeded cash and deposit balances.
INSERT INTO transactions (reference_number, destination_account_id, transaction_type,
                          amount, status, dest_balance_after, description, created_at)
SELECT d.reference, a.account_id, 'DEPOSIT', d.amount, 'SUCCESS', d.amount,
       'Demo opening deposit', CURRENT_TIMESTAMP - INTERVAL '2 days'
FROM (VALUES
    ('TX-00000000-0000-4000-8000-000000000001', 'DEMO1001', 1200.00),
    ('TX-00000000-0000-4000-8000-000000000002', 'DEMO1002', 350.00),
    ('TX-00000000-0000-4000-8000-000000000003', 'DEMO2001', 450.00)
) AS d(reference, account_number, amount)
JOIN accounts a ON a.account_number = d.account_number
ON CONFLICT (reference_number) DO NOTHING;

INSERT INTO transactions (reference_number, source_account_id, destination_account_id,
                          transaction_type, amount, status, source_balance_after,
                          dest_balance_after, description, created_at)
SELECT 'TX-00000000-0000-4000-8000-000000000004', source.account_id, target.account_id,
       'TRANSFER', 300.00, 'SUCCESS', 900.00, 300.00, 'Demo term deposit funding',
       CURRENT_TIMESTAMP - INTERVAL '1 day'
FROM accounts source CROSS JOIN accounts target
WHERE source.account_number = 'DEMO1001' AND target.account_number = 'DEMO1003'
ON CONFLICT (reference_number) DO NOTHING;

COMMIT;
