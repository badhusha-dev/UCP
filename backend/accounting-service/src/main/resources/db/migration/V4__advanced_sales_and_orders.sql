-- Flyway Migration V4: Advanced Sales, Marketing, Fulfillment & Returns
-- Table: coupon
CREATE TABLE IF NOT EXISTS coupon (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) UNIQUE NOT NULL,
    discount_type VARCHAR(32) NOT NULL, -- 'PERCENTAGE' or 'FIXED'
    discount_value NUMERIC(19, 4) NOT NULL,
    min_order_amount NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    max_discount NUMERIC(19, 4),
    usage_limit INTEGER NOT NULL DEFAULT 1000,
    times_used INTEGER NOT NULL DEFAULT 0,
    start_date TIMESTAMP WITH TIME ZONE,
    expiry_date TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_coupon_code ON coupon (code);
CREATE INDEX IF NOT EXISTS idx_coupon_active ON coupon (active);

-- Seed initial promotions
INSERT INTO coupon (code, discount_type, discount_value, min_order_amount, max_discount, usage_limit, times_used, start_date, expiry_date, active)
VALUES 
('WELCOME10', 'PERCENTAGE', 10.0000, 20.0000, 50.0000, 10000, 0, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '365 days', TRUE),
('SAVE25', 'FIXED', 25.0000, 100.0000, 25.0000, 500, 0, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '180 days', TRUE),
('VIP50', 'FIXED', 50.0000, 200.0000, 50.0000, 200, 0, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '90 days', TRUE)
ON CONFLICT (code) DO NOTHING;

-- Extend orders table with discount and fulfillment attributes
ALTER TABLE orders ADD COLUMN IF NOT EXISTS discount_amount NUMERIC(38, 2) DEFAULT 0.00;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS coupon_code VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS carrier VARCHAR(64) DEFAULT 'Standard Shipping';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tracking_number VARCHAR(128);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS estimated_delivery TIMESTAMP WITH TIME ZONE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_status VARCHAR(64) DEFAULT 'PROCESSING';

-- Table: customer_cart (Server-side persistent cart & abandoned cart recovery)
CREATE TABLE IF NOT EXISTS customer_cart (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL,
    items_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'ABANDONED', 'CONVERTED'
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_customer_cart_user_id ON customer_cart (user_id);
CREATE INDEX IF NOT EXISTS idx_customer_cart_status ON customer_cart (status);

-- Table: order_tracking_event
CREATE TABLE IF NOT EXISTS order_tracking_event (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    status VARCHAR(64) NOT NULL,
    location VARCHAR(255),
    description TEXT,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tracking_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tracking_order_id ON order_tracking_event (order_id);

-- Table: order_return (Customer Returns & Exchange Requests)
CREATE TABLE IF NOT EXISTS order_return (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED', -- 'REQUESTED', 'APPROVED', 'RECEIVED', 'REFUNDED', 'REJECTED'
    refund_amount NUMERIC(38, 2) NOT NULL,
    resolution_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_return_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE INDEX IF NOT EXISTS idx_order_return_order_id ON order_return (order_id);
CREATE INDEX IF NOT EXISTS idx_order_return_user_id ON order_return (user_id);
CREATE INDEX IF NOT EXISTS idx_order_return_status ON order_return (status);
