-- Tenant scoping for accounting (UCP Phase 2/3).
-- Every existing row belongs to the legacy tenant, which is also the column default, so callers that do not yet pass a tenant
-- (Kafka order events, internal supplier callbacks, platform tokens) keep writing where they always did.
-- Business keys that were globally unique become unique per tenant, and child rows must share their parent's tenant
-- (composite foreign keys), so a line can never point at another tenant's journal, draft or account.

ALTER TABLE ledger_transactions        ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE accounting_audit_log       ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE refund_requests            ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE accounts                   ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE journal_entries            ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE journal_lines              ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE journal_drafts             ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE journal_draft_lines        ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE invoices                   ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE financial_report_snapshots ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE accounting_imports         ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE accounting_periods         ADD COLUMN tenant_id UUID NOT NULL DEFAULT '00000000-0000-4000-8000-000000000001';

-- Foreign keys that reference the globally unique accounts.code must go before that key can be replaced.
ALTER TABLE accounts            DROP CONSTRAINT fk_accounts_parent;
ALTER TABLE journal_lines       DROP CONSTRAINT journal_lines_account_code_fkey;
ALTER TABLE journal_draft_lines DROP CONSTRAINT journal_draft_lines_account_code_fkey;
ALTER TABLE journal_lines       DROP CONSTRAINT journal_lines_entry_id_fkey;
ALTER TABLE journal_draft_lines DROP CONSTRAINT journal_draft_lines_draft_id_fkey;
ALTER TABLE journal_entries     DROP CONSTRAINT journal_entries_reversal_of_id_fkey;
ALTER TABLE journal_drafts      DROP CONSTRAINT journal_drafts_posted_journal_entry_id_fkey;

-- Per-tenant business keys.
ALTER TABLE accounts            DROP CONSTRAINT accounts_code_key;
ALTER TABLE journal_entries     DROP CONSTRAINT journal_entries_entry_number_key;
ALTER TABLE journal_entries     DROP CONSTRAINT journal_entries_idempotency_key_key;
ALTER TABLE journal_drafts      DROP CONSTRAINT journal_drafts_idempotency_key_key;
ALTER TABLE invoices            DROP CONSTRAINT invoices_invoice_number_key;
ALTER TABLE invoices            DROP CONSTRAINT invoices_idempotency_key_key;
ALTER TABLE accounting_periods  DROP CONSTRAINT accounting_periods_period_name_key;
ALTER TABLE ledger_transactions DROP CONSTRAINT ledger_transactions_idempotency_key_key;

ALTER TABLE accounts            ADD CONSTRAINT uq_accounts_tenant_code UNIQUE (tenant_id, code);
ALTER TABLE journal_entries     ADD CONSTRAINT uq_journal_entries_tenant_number UNIQUE (tenant_id, entry_number);
ALTER TABLE journal_entries     ADD CONSTRAINT uq_journal_entries_tenant_idempotency UNIQUE (tenant_id, idempotency_key);
ALTER TABLE journal_entries     ADD CONSTRAINT uq_journal_entries_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE journal_drafts      ADD CONSTRAINT uq_journal_drafts_tenant_idempotency UNIQUE (tenant_id, idempotency_key);
ALTER TABLE journal_drafts      ADD CONSTRAINT uq_journal_drafts_tenant_id UNIQUE (tenant_id, id);
ALTER TABLE invoices            ADD CONSTRAINT uq_invoices_tenant_number UNIQUE (tenant_id, invoice_number);
ALTER TABLE invoices            ADD CONSTRAINT uq_invoices_tenant_idempotency UNIQUE (tenant_id, idempotency_key);
ALTER TABLE accounting_periods  ADD CONSTRAINT uq_accounting_periods_tenant_name UNIQUE (tenant_id, period_name);
ALTER TABLE ledger_transactions ADD CONSTRAINT uq_ledger_tenant_idempotency UNIQUE (tenant_id, idempotency_key);

-- Same-tenant references.
ALTER TABLE accounts            ADD CONSTRAINT fk_accounts_parent_tenant FOREIGN KEY (tenant_id, parent_code) REFERENCES accounts (tenant_id, code);
ALTER TABLE journal_lines       ADD CONSTRAINT fk_journal_lines_account_tenant FOREIGN KEY (tenant_id, account_code) REFERENCES accounts (tenant_id, code);
ALTER TABLE journal_lines       ADD CONSTRAINT fk_journal_lines_entry_tenant FOREIGN KEY (tenant_id, entry_id) REFERENCES journal_entries (tenant_id, id);
ALTER TABLE journal_entries     ADD CONSTRAINT fk_journal_entries_reversal_tenant FOREIGN KEY (tenant_id, reversal_of_id) REFERENCES journal_entries (tenant_id, id);
ALTER TABLE journal_draft_lines ADD CONSTRAINT fk_journal_draft_lines_account_tenant FOREIGN KEY (tenant_id, account_code) REFERENCES accounts (tenant_id, code);
ALTER TABLE journal_draft_lines ADD CONSTRAINT fk_journal_draft_lines_draft_tenant FOREIGN KEY (tenant_id, draft_id) REFERENCES journal_drafts (tenant_id, id) ON DELETE CASCADE;
ALTER TABLE journal_drafts      ADD CONSTRAINT fk_journal_drafts_posted_tenant FOREIGN KEY (tenant_id, posted_journal_entry_id) REFERENCES journal_entries (tenant_id, id);

CREATE INDEX idx_ledger_tenant_created ON ledger_transactions (tenant_id, created_at DESC);
CREATE INDEX idx_audit_tenant_created ON accounting_audit_log (tenant_id, created_at DESC);
CREATE INDEX idx_refund_tenant_status ON refund_requests (tenant_id, status);
CREATE INDEX idx_journal_entries_tenant_period ON journal_entries (tenant_id, period_date);
CREATE INDEX idx_journal_drafts_tenant_status ON journal_drafts (tenant_id, status, created_at DESC);
CREATE INDEX idx_invoices_tenant_created ON invoices (tenant_id, created_at DESC);
CREATE INDEX idx_accounting_periods_tenant_dates ON accounting_periods (tenant_id, start_date, end_date);
