-- Add optimistic locking column to prevent concurrent over-sell on stock adjustments (A-04).
ALTER TABLE stock_item ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
