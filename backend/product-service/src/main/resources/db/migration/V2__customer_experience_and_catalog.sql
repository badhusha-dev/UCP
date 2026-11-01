-- Flyway Migration V2: Customer Experience & Catalog Expansion

CREATE TABLE IF NOT EXISTS product_category (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- Seed Categories
INSERT INTO product_category (id, name, slug, description)
VALUES 
    (1, 'Audio', 'audio', 'Headphones, earbuds, and premium audio equipment'),
    (2, 'Computer Peripherals', 'peripherals', 'Keyboards, mice, and computer accessories'),
    (3, 'Gaming', 'gaming', 'Pro-grade gaming peripherals and streaming gear')
ON CONFLICT (id) DO NOTHING;

SELECT setval('product_category_id_seq', (SELECT COALESCE(MAX(id), 1) FROM product_category));

-- Alter Product table to include category, rating aggregates, and images
ALTER TABLE product ADD COLUMN IF NOT EXISTS category_id BIGINT REFERENCES product_category(id);
ALTER TABLE product ADD COLUMN IF NOT EXISTS rating DOUBLE PRECISION DEFAULT 5.0;
ALTER TABLE product ADD COLUMN IF NOT EXISTS review_count INTEGER DEFAULT 0;
ALTER TABLE product ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);

-- Update existing seeded products with category IDs and ratings
UPDATE product SET category_id = 1, rating = 4.8, review_count = 12, image_url = 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=500' WHERE id = 1;
UPDATE product SET category_id = 2, rating = 4.9, review_count = 28, image_url = 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=500' WHERE id = 2;
UPDATE product SET category_id = 3, rating = 4.7, review_count = 19, image_url = 'https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=500' WHERE id = 3;

-- Product Variants
CREATE TABLE IF NOT EXISTS product_variant (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    sku VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    color VARCHAR(50),
    size VARCHAR(50),
    price_adjustment DOUBLE PRECISION DEFAULT 0.0,
    stock_quantity INTEGER NOT NULL DEFAULT 0
);

-- Seed Sample Variants for Headphones (id: 1)
INSERT INTO product_variant (product_id, sku, name, color, size, price_adjustment, stock_quantity)
VALUES 
    (1, 'WH-BLK-STD', 'Midnight Black', 'Black', 'Standard', 0.0, 30),
    (1, 'WH-SLV-STD', 'Platinum Silver', 'Silver', 'Standard', 10.0, 20)
ON CONFLICT (sku) DO NOTHING;

-- Seed Sample Variants for Mechanical Keyboard (id: 2)
INSERT INTO product_variant (product_id, sku, name, color, size, price_adjustment, stock_quantity)
VALUES 
    (2, 'KB-RGB-RED', 'Linear Red Switches', 'Black', 'Full-Size', 0.0, 60),
    (2, 'KB-RGB-BLU', 'Clicky Blue Switches', 'Black', 'Tenkeyless', -10.0, 40)
ON CONFLICT (sku) DO NOTHING;

-- Customer Wishlist
CREATE TABLE IF NOT EXISTS customer_wishlist (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_wishlist_user_product UNIQUE(user_id, product_id)
);

-- Product Reviews
CREATE TABLE IF NOT EXISTS product_review (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(100) NOT NULL,
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    comment TEXT,
    is_verified_purchase BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed Initial Verified Reviews
INSERT INTO product_review (product_id, user_id, user_name, rating, comment, is_verified_purchase)
VALUES 
    (1, 1, 'John Doe', 5, 'Incredible noise cancellation, battery easily lasts 30 hours on a single charge!', TRUE),
    (1, 2, 'Jane Smith', 4, 'Very comfortable for long flights. Soundstage is punchy and detailed.', TRUE),
    (2, 1, 'John Doe', 5, 'The tactile mechanical feedback is superb. Build quality is aerospace grade.', TRUE)
ON CONFLICT DO NOTHING;

-- Recently Viewed Products
CREATE TABLE IF NOT EXISTS recently_viewed (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    viewed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_product_category ON product(category_id);
CREATE INDEX IF NOT EXISTS idx_product_variant_prod ON product_variant(product_id);
CREATE INDEX IF NOT EXISTS idx_wishlist_user ON customer_wishlist(user_id);
CREATE INDEX IF NOT EXISTS idx_review_product ON product_review(product_id);
CREATE INDEX IF NOT EXISTS idx_recently_viewed_user ON recently_viewed(user_id, viewed_at DESC);
