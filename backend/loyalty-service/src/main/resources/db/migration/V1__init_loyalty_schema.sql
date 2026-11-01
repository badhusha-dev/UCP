-- Loyalty Service Migration V1: Initial Loyalty Schema

CREATE TABLE IF NOT EXISTS loyalty_tier_rules (
    id BIGSERIAL PRIMARY KEY,
    tier VARCHAR(32) NOT NULL UNIQUE,
    min_lifetime_points INTEGER NOT NULL,
    multiplier NUMERIC(4,2) NOT NULL DEFAULT 1.00,
    discount_percent_bonus NUMERIC(5,2) DEFAULT 0.00,
    perks_description VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS loyalty_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    points_balance INTEGER NOT NULL DEFAULT 0 CHECK (points_balance >= 0),
    lifetime_points INTEGER NOT NULL DEFAULT 0 CHECK (lifetime_points >= 0),
    tier VARCHAR(32) NOT NULL DEFAULT 'BRONZE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_loyalty_acc_user_id ON loyalty_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_loyalty_acc_tier ON loyalty_accounts(tier);

CREATE TABLE IF NOT EXISTS loyalty_transactions (
    id BIGSERIAL PRIMARY KEY,
    loyalty_account_id BIGINT NOT NULL REFERENCES loyalty_accounts(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    points INTEGER NOT NULL,
    transaction_type VARCHAR(32) NOT NULL, -- EARN, REDEEM, REFUND, BONUS, ADJUSTMENT
    reference_id VARCHAR(128),
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_loyalty_tx_user_id ON loyalty_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_loyalty_tx_created ON loyalty_transactions(created_at);

-- Seed Tier Configuration Rules
INSERT INTO loyalty_tier_rules (id, tier, min_lifetime_points, multiplier, discount_percent_bonus, perks_description)
VALUES
    (1, 'BRONZE', 0, 1.00, 0.00, 'Standard 1x earn rate, standard access to catalog promotions'),
    (2, 'SILVER', 500, 1.25, 5.00, '1.25x point accelerator, priority customer care, 5% birthday discount voucher'),
    (3, 'GOLD', 2000, 1.50, 10.00, '1.50x point accelerator, free standard delivery on all orders, VIP pre-order access'),
    (4, 'PLATINUM', 5000, 2.00, 15.00, '2.00x point accelerator, complimentary express shipping, concierge service, annual anniversary gift')
ON CONFLICT (id) DO NOTHING;

-- Seed Customer Loyalty Accounts
INSERT INTO loyalty_accounts (id, user_id, points_balance, lifetime_points, tier, created_at, updated_at)
VALUES
    (1, 1, 450, 1200, 'SILVER', CURRENT_TIMESTAMP - INTERVAL '60 days', CURRENT_TIMESTAMP - INTERVAL '2 days'),
    (2, 2, 150, 350, 'BRONZE', CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '5 days'),
    (3, 3, 2500, 6000, 'PLATINUM', CURRENT_TIMESTAMP - INTERVAL '120 days', CURRENT_TIMESTAMP - INTERVAL '1 day')
ON CONFLICT (id) DO NOTHING;

-- Seed Transaction Ledger Audit Trail
INSERT INTO loyalty_transactions (id, loyalty_account_id, user_id, points, transaction_type, reference_id, description, created_at)
VALUES
    (1, 1, 1, 250, 'EARN', 'ORDER-101', 'Points earned for Order #101 ($200.00 spend at 1.25x Silver rate)', CURRENT_TIMESTAMP - INTERVAL '20 days'),
    (2, 1, 1, -100, 'REDEEM', 'ORDER-104', 'Points redeemed for $5.00 checkout discount on Order #104', CURRENT_TIMESTAMP - INTERVAL '10 days'),
    (3, 1, 1, 300, 'EARN', 'ORDER-109', 'Points earned for Order #109 ($240.00 spend at 1.25x Silver rate)', CURRENT_TIMESTAMP - INTERVAL '2 days'),
    (4, 2, 2, 250, 'EARN', 'ORDER-201', 'Points earned for Order #201 ($250.00 spend at 1.0x Bronze rate)', CURRENT_TIMESTAMP - INTERVAL '15 days'),
    (5, 2, 2, -100, 'REDEEM', 'ORDER-205', 'Points redeemed for $5.00 checkout discount on Order #205', CURRENT_TIMESTAMP - INTERVAL '5 days'),
    (6, 3, 3, 1000, 'BONUS', 'CAMPAIGN-VIP', 'Exclusive Platinum VIP Annual Bonus Award', CURRENT_TIMESTAMP - INTERVAL '30 days'),
    (7, 3, 3, 1500, 'EARN', 'ORDER-301', 'Points earned for Order #301 ($750.00 spend at 2.0x Platinum rate)', CURRENT_TIMESTAMP - INTERVAL '1 day')
ON CONFLICT (id) DO NOTHING;

-- Reset Sequences
SELECT setval('loyalty_tier_rules_id_seq', (SELECT COALESCE(MAX(id), 1) FROM loyalty_tier_rules));
SELECT setval('loyalty_accounts_id_seq', (SELECT COALESCE(MAX(id), 1) FROM loyalty_accounts));
SELECT setval('loyalty_transactions_id_seq', (SELECT COALESCE(MAX(id), 1) FROM loyalty_transactions));
