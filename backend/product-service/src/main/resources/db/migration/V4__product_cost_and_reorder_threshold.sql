-- Flyway Migration V4: Product Cost Price & Reorder Threshold for Inventory Analytics
ALTER TABLE product ADD COLUMN IF NOT EXISTS cost_price NUMERIC(19, 4);
ALTER TABLE product ADD COLUMN IF NOT EXISTS reorder_threshold INTEGER NOT NULL DEFAULT 10;

-- Update seeded products with realistic wholesale cost prices
UPDATE product SET cost_price = 85.0000, reorder_threshold = 15 WHERE id = 1;
UPDATE product SET cost_price = 60.0000, reorder_threshold = 20 WHERE id = 2;
UPDATE product SET cost_price = 35.0000, reorder_threshold = 25 WHERE id = 3;

CREATE INDEX IF NOT EXISTS idx_product_stock_reorder ON product (stock_quantity, reorder_threshold);
