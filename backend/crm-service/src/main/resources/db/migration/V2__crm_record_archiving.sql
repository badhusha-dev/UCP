ALTER TABLE crm_companies ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE crm_tasks ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE crm_segments ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_crm_companies_active_name ON crm_companies(archived, name);
CREATE INDEX IF NOT EXISTS idx_crm_tasks_active_due ON crm_tasks(archived, completed, due_at);
CREATE INDEX IF NOT EXISTS idx_crm_segments_active_name ON crm_segments(active, name);
