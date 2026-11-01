-- Cart Service Migration V1: Initial Cart Schema
CREATE TABLE IF NOT EXISTS carts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    session_token VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_carts_user_id ON carts(user_id);
CREATE INDEX IF NOT EXISTS idx_carts_session_token ON carts(session_token);
CREATE INDEX IF NOT EXISTS idx_carts_status_updated ON carts(status, updated_at);

CREATE TABLE IF NOT EXISTS cart_items (
    id BIGSERIAL PRIMARY KEY,
    cart_id BIGINT NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL,
    sku VARCHAR(64) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    image_url VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cart_product UNIQUE (cart_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_cart_items_cart_id ON cart_items(cart_id);
CREATE INDEX IF NOT EXISTS idx_cart_items_product_id ON cart_items(product_id);

CREATE TABLE IF NOT EXISTS abandoned_cart_reminders (
    id BIGSERIAL PRIMARY KEY,
    cart_id BIGINT NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    user_id BIGINT,
    customer_email VARCHAR(255) NOT NULL,
    discount_code VARCHAR(64),
    discount_percent NUMERIC(5,2) DEFAULT 15.00,
    status VARCHAR(32) NOT NULL DEFAULT 'SENT',
    sent_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_abandoned_reminders_cart_id ON abandoned_cart_reminders(cart_id);

-- Seed Initial Demonstration Carts
-- Cart 1: Active Cart for user 1 (Alex Customer)
INSERT INTO carts (id, user_id, session_token, status, currency, created_at, updated_at)
VALUES (1, 1, 'sess_cust_001_active', 'ACTIVE', 'USD', CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '10 minutes')
ON CONFLICT (id) DO NOTHING;

INSERT INTO cart_items (id, cart_id, product_id, sku, product_name, unit_price, quantity, image_url, created_at, updated_at)
VALUES 
    (1, 1, 1, 'SKU-HEADPHONES-01', 'Wireless Noise-Canceling Headphones', 299.99, 1, 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=400&q=80', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 1, 2, 'SKU-SMARTWATCH-02', 'Smart Fitness Watch Series 5', 199.99, 1, 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=400&q=80', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Cart 2: Inactive/Abandoned Cart for user 2 (Bob User)
INSERT INTO carts (id, user_id, session_token, status, currency, created_at, updated_at)
VALUES (2, 2, 'sess_cust_002_abandoned', 'ACTIVE', 'USD', CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '48 hours')
ON CONFLICT (id) DO NOTHING;

INSERT INTO cart_items (id, cart_id, product_id, sku, product_name, unit_price, quantity, image_url, created_at, updated_at)
VALUES 
    (3, 2, 3, 'SKU-CHAIR-03', 'Ergonomic Mesh Office Chair', 349.50, 1, 'https://images.unsplash.com/photo-1580481077195-c228ff34a03a?auto=format&fit=crop&w=400&q=80', CURRENT_TIMESTAMP - INTERVAL '48 hours', CURRENT_TIMESTAMP - INTERVAL '48 hours')
ON CONFLICT (id) DO NOTHING;

-- Reset sequences
SELECT setval('carts_id_seq', (SELECT COALESCE(MAX(id), 1) FROM carts));
SELECT setval('cart_items_id_seq', (SELECT COALESCE(MAX(id), 1) FROM cart_items));
SELECT setval('abandoned_cart_reminders_id_seq', (SELECT COALESCE(MAX(id), 1) FROM abandoned_cart_reminders));
