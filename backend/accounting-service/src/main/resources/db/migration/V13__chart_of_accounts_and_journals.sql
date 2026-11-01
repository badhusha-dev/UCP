CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    account_type VARCHAR(30) NOT NULL CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    normal_balance VARCHAR(10) NOT NULL CHECK (normal_balance IN ('DEBIT','CREDIT')),
    parent_code VARCHAR(20),
    description TEXT,
    is_cash_account BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_accounts_parent FOREIGN KEY (parent_code) REFERENCES accounts(code)
);

CREATE TABLE journal_entries (
    id BIGSERIAL PRIMARY KEY,
    entry_number VARCHAR(30) UNIQUE NOT NULL,
    description TEXT NOT NULL,
    reference_type VARCHAR(50),
    reference_id VARCHAR(100),
    period_date DATE NOT NULL,
    posted_by VARCHAR(150) NOT NULL,
    idempotency_key VARCHAR(150) UNIQUE NOT NULL,
    is_reversal BOOLEAN NOT NULL DEFAULT FALSE,
    reversal_of_id BIGINT REFERENCES journal_entries(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE journal_lines (
    id BIGSERIAL PRIMARY KEY,
    entry_id BIGINT NOT NULL REFERENCES journal_entries(id),
    account_code VARCHAR(20) NOT NULL REFERENCES accounts(code),
    debit_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (debit_amount >= 0),
    credit_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (credit_amount >= 0),
    description TEXT,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    CONSTRAINT ck_journal_line_one_sided CHECK ((debit_amount = 0 AND credit_amount > 0) OR (credit_amount = 0 AND debit_amount > 0))
);

CREATE INDEX idx_journal_entries_period_date ON journal_entries(period_date);
CREATE INDEX idx_journal_lines_entry_id ON journal_lines(entry_id);
CREATE UNIQUE INDEX uq_journal_reversal_source ON journal_entries(reversal_of_id) WHERE reversal_of_id IS NOT NULL;

ALTER TABLE accounting_audit_log ALTER COLUMN admin_id DROP NOT NULL;
ALTER TABLE accounting_audit_log ADD COLUMN entity_type VARCHAR(30) NOT NULL DEFAULT 'LEDGER_TRANSACTION';
ALTER TABLE accounting_audit_log ADD COLUMN entity_id BIGINT;
CREATE INDEX idx_accounting_audit_entity ON accounting_audit_log(entity_type, entity_id, created_at DESC);
