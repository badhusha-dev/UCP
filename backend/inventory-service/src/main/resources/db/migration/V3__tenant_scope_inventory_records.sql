-- Assign existing inventory-owned data to the reserved tenant before enforcing ownership.
ALTER TABLE inventory_item ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE stock_reservation ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE stock_movement ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE back_in_stock_alert ADD COLUMN IF NOT EXISTS tenant_id UUID;

UPDATE inventory_item SET tenant_id = '00000000-0000-4000-8000-000000000001' WHERE tenant_id IS NULL;
UPDATE stock_reservation SET tenant_id = '00000000-0000-4000-8000-000000000001' WHERE tenant_id IS NULL;
UPDATE stock_movement SET tenant_id = '00000000-0000-4000-8000-000000000001' WHERE tenant_id IS NULL;
UPDATE back_in_stock_alert SET tenant_id = '00000000-0000-4000-8000-000000000001' WHERE tenant_id IS NULL;

ALTER TABLE inventory_item ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE stock_reservation ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE stock_movement ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE back_in_stock_alert ALTER COLUMN tenant_id SET NOT NULL;

-- Older application instances that omit tenant_id continue writing to legacy during a controlled rollout.
ALTER TABLE inventory_item ALTER COLUMN tenant_id SET DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE stock_reservation ALTER COLUMN tenant_id SET DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE stock_movement ALTER COLUMN tenant_id SET DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE back_in_stock_alert ALTER COLUMN tenant_id SET DEFAULT '00000000-0000-4000-8000-000000000001';

ALTER TABLE inventory_item DROP CONSTRAINT IF EXISTS inventory_item_product_id_key;
CREATE UNIQUE INDEX IF NOT EXISTS ux_inventory_item_tenant_product ON inventory_item(tenant_id, product_id);
CREATE INDEX IF NOT EXISTS idx_inventory_item_tenant ON inventory_item(tenant_id);
CREATE INDEX IF NOT EXISTS idx_stock_reservation_tenant ON stock_reservation(tenant_id, reservation_id);
CREATE INDEX IF NOT EXISTS idx_stock_movement_tenant ON stock_movement(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_back_in_stock_alert_tenant ON back_in_stock_alert(tenant_id, product_id, status);
