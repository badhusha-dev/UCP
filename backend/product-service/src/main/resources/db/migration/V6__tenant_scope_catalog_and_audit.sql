-- Assign existing catalog rows to the reserved legacy tenant before enforcing ownership.
ALTER TABLE product ADD COLUMN IF NOT EXISTS tenant_id UUID;
UPDATE product SET tenant_id = '00000000-0000-4000-8000-000000000001' WHERE tenant_id IS NULL;
ALTER TABLE product ALTER COLUMN tenant_id SET NOT NULL;
-- Older application instances that omit tenant_id keep writing to the legacy tenant during rollout.
ALTER TABLE product ALTER COLUMN tenant_id SET DEFAULT '00000000-0000-4000-8000-000000000001';

ALTER TABLE product ADD COLUMN IF NOT EXISTS sku VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS ux_product_tenant_sku ON product(tenant_id, LOWER(sku)) WHERE sku IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_product_tenant_name ON product(tenant_id, name);

CREATE TABLE IF NOT EXISTS product_audit_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    product_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_email VARCHAR(255),
    before_value TEXT,
    after_value TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_product_audit_tenant_product ON product_audit_log(tenant_id, product_id, created_at DESC);
