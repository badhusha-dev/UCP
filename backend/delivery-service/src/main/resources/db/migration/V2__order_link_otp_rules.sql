-- Order linkage: Order Service owns order lifecycle and totals; Delivery keeps a read-only snapshot.
ALTER TABLE delivery ADD COLUMN order_id BIGINT;
ALTER TABLE delivery ADD COLUMN order_total NUMERIC(12,2);
ALTER TABLE delivery ADD COLUMN order_currency CHAR(3);
-- Order Service has no tenant ownership, so one order can back at most one delivery platform-wide (first claim wins).
CREATE UNIQUE INDEX ux_delivery_order_id ON delivery(order_id);

-- Proof of delivery is only trusted when the server verified it.
ALTER TABLE delivery_proof ADD COLUMN verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Hashed one-time delivery code. The plaintext is shown only to the authenticated recipient and never stored or logged.
CREATE TABLE delivery_otp (
    delivery_id UUID PRIMARY KEY REFERENCES delivery(id),
    tenant_id UUID NOT NULL,
    code_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    issue_count INTEGER NOT NULL DEFAULT 1,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Transactional outbox for Delivery -> Order Service status propagation (retried until Order Service accepts).
CREATE TABLE delivery_order_sync (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    delivery_id UUID NOT NULL REFERENCES delivery(id),
    order_id BIGINT NOT NULL,
    delivery_status VARCHAR(32) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'NEW' CHECK (state IN ('NEW','SYNCED','FAILED')),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    synced_at TIMESTAMPTZ
);
CREATE INDEX idx_delivery_order_sync_ready ON delivery_order_sync(state, next_attempt_at, created_at);

-- Controlled rule registry: versioned JSON documents of known keys only, validated before publish. No scripts.
CREATE TABLE delivery_rule_set (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    version INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','PUBLISHED','SUPERSEDED')),
    rules TEXT NOT NULL,
    rollback_of INTEGER,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_by BIGINT,
    published_at TIMESTAMPTZ,
    UNIQUE (tenant_id, version)
);
