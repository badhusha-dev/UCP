-- Flyway Migration V3: Universal Commerce Accounting Expansion
-- Reconciliation support, refund approval workflow, tokenized payment methods, customer preferences

-- 1. Extend ledger_transactions for reconciliation, customer email search, and provider reference
ALTER TABLE ledger_transactions ADD COLUMN IF NOT EXISTS customer_email VARCHAR(150);
ALTER TABLE ledger_transactions ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(20) NOT NULL DEFAULT 'RECONCILED';
ALTER TABLE ledger_transactions ADD COLUMN IF NOT EXISTS reconciliation_notes TEXT;
ALTER TABLE ledger_transactions ADD COLUMN IF NOT EXISTS payment_provider VARCHAR(50) DEFAULT 'SIMULATED_INTERNAL';
ALTER TABLE ledger_transactions ADD COLUMN IF NOT EXISTS provider_reference VARCHAR(150);

CREATE INDEX IF NOT EXISTS idx_ledger_customer_email ON ledger_transactions (customer_email);
CREATE INDEX IF NOT EXISTS idx_ledger_reconciliation ON ledger_transactions (reconciliation_status);

-- 2. Refund Requests Workflow Table
CREATE TABLE IF NOT EXISTS refund_requests (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    customer_email VARCHAR(150),
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED', -- REQUESTED, APPROVED, REJECTED, PROCESSED
    requested_by VARCHAR(150) NOT NULL,
    approved_by VARCHAR(150),
    decision_reason TEXT,
    ledger_transaction_id BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_refund_ledger FOREIGN KEY (ledger_transaction_id) REFERENCES ledger_transactions (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_refund_user_id ON refund_requests (user_id);
CREATE INDEX IF NOT EXISTS idx_refund_order_id ON refund_requests (order_id);
CREATE INDEX IF NOT EXISTS idx_refund_status ON refund_requests (status);

-- 3. Customer Accounting & Notification Preferences Table
CREATE TABLE IF NOT EXISTS customer_accounting_preferences (
    user_id BIGINT PRIMARY KEY,
    email_receipts BOOLEAN NOT NULL DEFAULT TRUE,
    statement_notifications BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Customer Tokenized Payment Methods (Non-sensitive metadata only)
CREATE TABLE IF NOT EXISTS customer_payment_methods (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(50) NOT NULL DEFAULT 'SIMULATED_GATEWAY',
    payment_token VARCHAR(100) NOT NULL,
    card_brand VARCHAR(50) NOT NULL,
    last4 VARCHAR(4) NOT NULL,
    exp_month INT NOT NULL,
    exp_year INT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pm_user_id ON customer_payment_methods (user_id);
