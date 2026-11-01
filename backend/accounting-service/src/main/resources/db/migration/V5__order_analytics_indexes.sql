-- Flyway Migration V5: Analytics Performance Indexes
CREATE INDEX IF NOT EXISTS idx_orders_created_status ON orders (created_at, status);
CREATE INDEX IF NOT EXISTS idx_orders_user_created ON orders (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_order_items_product_id ON order_items (product_id);
CREATE INDEX IF NOT EXISTS idx_order_items_order_product ON order_items (order_id, product_id);
