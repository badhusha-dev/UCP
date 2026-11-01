-- Flyway Migration V2: Add Idempotency Key and Dead Letter Queue Table
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_idempotency_key ON notifications (idempotency_key);

CREATE TABLE IF NOT EXISTS dead_letter_events (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    partition_id INTEGER,
    offset_number BIGINT,
    payload TEXT NOT NULL,
    exception_message TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'FAILED',
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dead_letter_status ON dead_letter_events (status);
