-- Flyway Migration V2: Customer Loyalty Program & Address Book
CREATE TABLE IF NOT EXISTS customer_loyalty (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL,
    points_balance INTEGER NOT NULL DEFAULT 0,
    lifetime_points INTEGER NOT NULL DEFAULT 0,
    tier VARCHAR(32) NOT NULL DEFAULT 'BRONZE', -- 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM'
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loyalty_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_loyalty_user_id ON customer_loyalty (user_id);

CREATE TABLE IF NOT EXISTS loyalty_transaction (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    points INTEGER NOT NULL,
    transaction_type VARCHAR(32) NOT NULL, -- 'EARN', 'REDEEM', 'BONUS'
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_loyalty_tx_user_id ON loyalty_transaction (user_id);

CREATE TABLE IF NOT EXISTS customer_address (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    address_type VARCHAR(32) NOT NULL DEFAULT 'SHIPPING', -- 'SHIPPING', 'BILLING'
    street VARCHAR(255) NOT NULL,
    city VARCHAR(128) NOT NULL,
    state VARCHAR(128),
    postal_code VARCHAR(32) NOT NULL,
    country VARCHAR(64) NOT NULL DEFAULT 'US',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_customer_address_user_id ON customer_address (user_id);

-- Seed initial loyalty accounts
INSERT INTO customer_loyalty (user_id, points_balance, lifetime_points, tier)
VALUES 
(1, 1500, 1500, 'GOLD'),
(2, 250, 250, 'BRONZE')
ON CONFLICT (user_id) DO NOTHING;

-- Seed initial addresses
INSERT INTO customer_address (user_id, address_type, street, city, state, postal_code, country, is_default)
VALUES 
(1, 'SHIPPING', '100 Silicon Way, Suite 400', 'San Francisco', 'CA', '94105', 'US', TRUE),
(2, 'SHIPPING', '742 Evergreen Terrace', 'Springfield', 'OR', '97477', 'US', TRUE)
ON CONFLICT DO NOTHING;
