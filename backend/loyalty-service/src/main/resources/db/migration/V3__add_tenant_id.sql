-- Loyalty Service V3: Add tenant isolation
ALTER TABLE loyalty_accounts DROP CONSTRAINT IF EXISTS loyalty_accounts_user_id_key;
ALTER TABLE loyalty_accounts ADD COLUMN IF NOT EXISTS tenant_id UUID NULL;
CREATE INDEX IF NOT EXISTS idx_loyalty_acc_tenant_id ON loyalty_accounts(tenant_id);
-- Per-tenant uniqueness: each user has at most one loyalty account per tenant
CREATE UNIQUE INDEX IF NOT EXISTS idx_loyalty_acc_user_tenant
    ON loyalty_accounts(user_id, tenant_id) WHERE tenant_id IS NOT NULL;
