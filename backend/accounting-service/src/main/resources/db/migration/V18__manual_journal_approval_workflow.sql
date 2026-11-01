CREATE TABLE journal_drafts (
    id BIGSERIAL PRIMARY KEY,
    description TEXT NOT NULL,
    reference_type VARCHAR(120),
    reference_id VARCHAR(255),
    period_date DATE NOT NULL,
    currency CHAR(3) NOT NULL,
    idempotency_key VARCHAR(180) NOT NULL UNIQUE,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'REJECTED', 'POSTED')),
    created_by_user_id BIGINT NOT NULL,
    created_by_email VARCHAR(255) NOT NULL,
    submitted_at TIMESTAMPTZ,
    decided_at TIMESTAMPTZ,
    decided_by_user_id BIGINT,
    decided_by_email VARCHAR(255),
    decision_note TEXT,
    posted_journal_entry_id BIGINT UNIQUE REFERENCES journal_entries(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((status = 'POSTED') = (posted_journal_entry_id IS NOT NULL))
);

CREATE TABLE journal_draft_lines (
    id BIGSERIAL PRIMARY KEY,
    draft_id BIGINT NOT NULL REFERENCES journal_drafts(id) ON DELETE CASCADE,
    account_code VARCHAR(32) NOT NULL REFERENCES accounts(code),
    debit_amount NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (debit_amount >= 0),
    credit_amount NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (credit_amount >= 0),
    description TEXT,
    currency CHAR(3) NOT NULL,
    CHECK ((debit_amount > 0) <> (credit_amount > 0))
);

CREATE INDEX idx_journal_drafts_status_created ON journal_drafts(status, created_at DESC);
CREATE INDEX idx_journal_drafts_creator ON journal_drafts(created_by_user_id, created_at DESC);
CREATE INDEX idx_journal_draft_lines_draft ON journal_draft_lines(draft_id, id);
