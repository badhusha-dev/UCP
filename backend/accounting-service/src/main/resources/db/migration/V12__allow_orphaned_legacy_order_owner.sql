-- Historical imports contain a small number of orders without a resolved customer.
-- The accounting read model preserves those records while Order remains authoritative.
ALTER TABLE orders ALTER COLUMN user_id DROP NOT NULL;
