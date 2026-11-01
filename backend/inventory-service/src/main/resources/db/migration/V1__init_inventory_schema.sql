CREATE TABLE IF NOT EXISTS inventory_item (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    sku VARCHAR(64),
    stock_quantity INTEGER NOT NULL DEFAULT 0,
    reserved_quantity INTEGER NOT NULL DEFAULT 0,
    reorder_threshold INTEGER NOT NULL DEFAULT 10,
    cost_price NUMERIC(38, 4) NOT NULL DEFAULT 0.0000,
    warehouse_location VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inventory_product_id ON inventory_item(product_id);

CREATE TABLE IF NOT EXISTS stock_reservation (
    id BIGSERIAL PRIMARY KEY,
    reservation_id VARCHAR(64) NOT NULL UNIQUE,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    order_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'HOLD',
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_stock_res_id ON stock_reservation(reservation_id);
CREATE INDEX idx_stock_res_status ON stock_reservation(status);

CREATE TABLE IF NOT EXISTS stock_movement (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    delta INTEGER NOT NULL,
    movement_type VARCHAR(32) NOT NULL,
    reason VARCHAR(255),
    performed_by VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_movement_product_id ON stock_movement(product_id);

CREATE TABLE IF NOT EXISTS back_in_stock_alert (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    user_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_back_in_stock_prod ON back_in_stock_alert(product_id);

-- Seed initial stock for catalog items
INSERT INTO inventory_item (product_id, sku, stock_quantity, reserved_quantity, reorder_threshold, cost_price, warehouse_location)
VALUES
(1, 'AUDIO-WH-ANC-01', 25, 0, 15, 85.0000, 'WH-A-12-01'),
(2, 'PERIPH-KB-RGB-02', 42, 0, 20, 60.0000, 'WH-B-04-03'),
(3, 'GAME-MON-4K-03', 30, 0, 25, 35.0000, 'WH-C-08-02')
ON CONFLICT (product_id) DO NOTHING;

INSERT INTO stock_movement (product_id, delta, movement_type, reason, performed_by)
VALUES
(1, 25, 'RECEIVE', 'Initial warehouse baseline stock', 'SYSTEM_MIGRATION'),
(2, 42, 'RECEIVE', 'Initial warehouse baseline stock', 'SYSTEM_MIGRATION'),
(3, 30, 'RECEIVE', 'Initial warehouse baseline stock', 'SYSTEM_MIGRATION');
