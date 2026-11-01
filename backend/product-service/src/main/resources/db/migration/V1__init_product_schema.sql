-- Flyway Migration V1: Initial Product Schema
CREATE TABLE IF NOT EXISTS product (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    price DOUBLE PRECISION NOT NULL,
    stock_quantity INTEGER NOT NULL DEFAULT 0
);

-- Seed initial catalog products
INSERT INTO product (id, name, description, price, stock_quantity)
VALUES (1, 'Wireless Headphones', 'High quality noise cancelling', 199.99, 50)
ON CONFLICT (id) DO NOTHING;

INSERT INTO product (id, name, description, price, stock_quantity)
VALUES (2, 'Mechanical Keyboard', 'RGB switches', 149.50, 100)
ON CONFLICT (id) DO NOTHING;

INSERT INTO product (id, name, description, price, stock_quantity)
VALUES (3, 'Gaming Mouse', 'Low latency', 89.00, 200)
ON CONFLICT (id) DO NOTHING;

-- Synchronize sequence
SELECT setval('product_id_seq', (SELECT COALESCE(MAX(id), 1) FROM product));
