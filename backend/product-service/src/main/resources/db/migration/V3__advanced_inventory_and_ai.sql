-- Flyway Migration V3: Advanced Inventory Reservations, Back-in-Stock Alerts & AI Support
CREATE TABLE IF NOT EXISTS back_in_stock_alert (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    user_id BIGINT,
    email VARCHAR(255) NOT NULL,
    notified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alert_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_stock_alert_product ON back_in_stock_alert (product_id);
CREATE INDEX IF NOT EXISTS idx_stock_alert_notified ON back_in_stock_alert (notified);

CREATE TABLE IF NOT EXISTS stock_reservation (
    id BIGSERIAL PRIMARY KEY,
    reservation_token VARCHAR(64) UNIQUE NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'CONFIRMED', 'RELEASED'
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservation_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_stock_res_token ON stock_reservation (reservation_token);
CREATE INDEX IF NOT EXISTS idx_stock_res_status ON stock_reservation (status);
CREATE INDEX IF NOT EXISTS idx_stock_res_expiry ON stock_reservation (expires_at);
