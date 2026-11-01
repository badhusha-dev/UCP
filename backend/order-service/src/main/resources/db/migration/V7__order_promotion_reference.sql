-- Reference to the pricing-service redemption so a cancelled order can release its coupon/campaign usage.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS promotion_ref VARCHAR(64);
