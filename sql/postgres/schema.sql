BEGIN;
CREATE TABLE IF NOT EXISTS customers (
    customer_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    phone VARCHAR(30),
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','LOCKED','CLOSED')),
    failed_login_count INTEGER NOT NULL DEFAULT 0 CHECK (failed_login_count >= 0),
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS accounts (
    account_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(customer_id),
    -- New accounts use 12 numeric digits; retain space for existing account numbers.
    account_number VARCHAR(34) NOT NULL UNIQUE,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('SAVINGS','CURRENT','DEPOSIT','LOAN')),
    balance NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','FROZEN','MATURED','CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_accounts_customer ON accounts(customer_id);
CREATE TABLE IF NOT EXISTS deposit_details (
    account_id BIGINT PRIMARY KEY REFERENCES accounts(account_id),
    principal NUMERIC(19,2) NOT NULL CHECK (principal > 0),
    term_months INTEGER NOT NULL CHECK (term_months > 0),
    interest_rate NUMERIC(5,2) NOT NULL DEFAULT 0,
    maturity_date DATE NOT NULL
);
CREATE TABLE IF NOT EXISTS loan_details (
    account_id BIGINT PRIMARY KEY REFERENCES accounts(account_id),
    principal_amount NUMERIC(19,2) NOT NULL CHECK (principal_amount > 0),
    annual_rate NUMERIC(5,2) NOT NULL DEFAULT 0,
    tenure_months INTEGER NOT NULL CHECK (tenure_months > 0),
    start_date DATE NOT NULL
);
-- Bring existing installations into line with the shared account ID design.
ALTER TABLE loan_details ALTER COLUMN account_id DROP IDENTITY IF EXISTS;
ALTER TABLE deposit_details ALTER COLUMN account_id DROP IDENTITY IF EXISTS;
CREATE TABLE IF NOT EXISTS transactions (
    transaction_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference_number VARCHAR(40) NOT NULL UNIQUE,
    source_account_id BIGINT REFERENCES accounts(account_id),
    destination_account_id BIGINT REFERENCES accounts(account_id),
    transaction_type VARCHAR(20) NOT NULL CHECK (transaction_type IN ('DEPOSIT','WITHDRAWAL','TRANSFER','LOAN_REPAYMENT')),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','SUCCESS','FAILED','REVERSED')),
    description VARCHAR(255),
    source_balance_after NUMERIC(19,2),
    dest_balance_after NUMERIC(19,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        (transaction_type = 'DEPOSIT' AND source_account_id IS NULL AND destination_account_id IS NOT NULL)
        OR (transaction_type = 'WITHDRAWAL' AND source_account_id IS NOT NULL AND destination_account_id IS NULL)
        OR (transaction_type IN ('TRANSFER','LOAN_REPAYMENT') AND source_account_id IS NOT NULL
            AND destination_account_id IS NOT NULL AND source_account_id <> destination_account_id)
    )
);
CREATE INDEX IF NOT EXISTS idx_txn_src_time ON transactions(source_account_id, created_at, transaction_id);
CREATE INDEX IF NOT EXISTS idx_txn_dst_time ON transactions(destination_account_id, created_at, transaction_id);
CREATE TABLE IF NOT EXISTS login_audit (
    audit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT REFERENCES customers(customer_id),
    username_attempted VARCHAR(80) NOT NULL,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCESS','FAILURE','LOCKED','TIMEOUT','LOGOUT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS transaction_audit (
    audit_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(customer_id),
    reference_number VARCHAR(40),
    event_type VARCHAR(30) NOT NULL,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCESS','REJECTED','FAILED','ROLLED_BACK')),
    failure_code VARCHAR(40),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_taudit_customer ON transaction_audit(customer_id, created_at);
CREATE INDEX IF NOT EXISTS idx_taudit_customer_ref ON transaction_audit(customer_id, reference_number, audit_id);
-- CREATE TABLE IF NOT EXISTS does not add constraints to older installations.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'transaction_audit'::regclass
                     AND conname = 'transaction_audit_outcome_check') THEN
        ALTER TABLE transaction_audit ADD CONSTRAINT transaction_audit_outcome_check
            CHECK (outcome IN ('SUCCESS','REJECTED','FAILED','ROLLED_BACK'));
    END IF;
END $$;
COMMIT;
