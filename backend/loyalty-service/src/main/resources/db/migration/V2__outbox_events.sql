-- Transactional outbox for loyalty events (ucp-outbox). Same DDL as outbox/src/main/resources/ucp-outbox/schema.sql.
CREATE TABLE IF NOT EXISTS outbox_events (
    event_id        VARCHAR(36)  PRIMARY KEY,
    tenant_id       VARCHAR(36),
    topic           VARCHAR(200) NOT NULL,
    message_key     VARCHAR(200),
    event_type      VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'IN_FLIGHT', 'PUBLISHED')),
    attempt_count   INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_until    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at    TIMESTAMP WITH TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_outbox_events_due ON outbox_events (status, next_attempt_at);
