-- Optional manual schema. The API creates these tables when JPA_DDL_AUTO=update (the default).
-- If you run this script yourself, start the API with JPA_DDL_AUTO=validate.

CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    balance NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_accounts_balance CHECK (balance >= 0)
);

CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    transaction_type VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    description VARCHAR(255),
    reference_id VARCHAR(25) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT uk_transactions_account_reference UNIQUE (account_id, reference_id),
    CONSTRAINT ck_transactions_amount_and_type CHECK (
        amount > 0 AND transaction_type IN ('CREDIT', 'DEBIT')
    )
);
