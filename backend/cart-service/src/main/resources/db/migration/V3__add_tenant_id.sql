-- Cart Service V3: Add tenant isolation
ALTER TABLE carts ADD COLUMN IF NOT EXISTS tenant_id UUID NULL;
CREATE INDEX IF NOT EXISTS idx_carts_tenant_id ON carts(tenant_id);
