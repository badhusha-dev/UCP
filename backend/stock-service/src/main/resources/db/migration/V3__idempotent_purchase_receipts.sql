ALTER TABLE stock_movement ADD COLUMN IF NOT EXISTS reference_id VARCHAR(150);
CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_movement_reference_id
    ON stock_movement(reference_id) WHERE reference_id IS NOT NULL;
