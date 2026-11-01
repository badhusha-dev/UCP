-- Flyway Migration V2: Immutable Accounting Ledger & Audit Trail
-- Universal Commerce Platform: Fixed-precision decimal accounting & idempotent transactions

CREATE TABLE IF NOT EXISTS ledger_transactions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    entry_type VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    description VARCHAR(255) NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    reference_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(150) UNIQUE NOT NULL,
    reversal_of_id BIGINT,
    reason TEXT,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ledger_reversal FOREIGN KEY (reversal_of_id) REFERENCES ledger_transactions (id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS accounting_audit_log (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT,
    admin_id BIGINT NOT NULL,
    admin_email VARCHAR(150) NOT NULL,
    action VARCHAR(50) NOT NULL,
    reason TEXT NOT NULL,
    before_value TEXT,
    after_value TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_transaction FOREIGN KEY (transaction_id) REFERENCES ledger_transactions (id) ON DELETE SET NULL
);

-- Indexes for performant filtering, user ledger lookups, and audit trail queries
CREATE INDEX IF NOT EXISTS idx_ledger_user_id ON ledger_transactions (user_id);
CREATE INDEX IF NOT EXISTS idx_ledger_created_at ON ledger_transactions (created_at);
CREATE INDEX IF NOT EXISTS idx_ledger_type_status ON ledger_transactions (transaction_type, status);
CREATE INDEX IF NOT EXISTS idx_ledger_ref ON ledger_transactions (reference_type, reference_id);
CREATE INDEX IF NOT EXISTS idx_ledger_reversal_of ON ledger_transactions (reversal_of_id);
CREATE INDEX IF NOT EXISTS idx_audit_admin_id ON accounting_audit_log (admin_id);
CREATE INDEX IF NOT EXISTS idx_audit_created_at ON accounting_audit_log (created_at);
