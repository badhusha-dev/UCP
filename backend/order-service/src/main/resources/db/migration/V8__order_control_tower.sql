-- Order Management Control Tower. Extends orders/order_items in place and adds tenant-scoped history, notes, fulfillment,
-- exceptions, bulk-job and SLA structures. Kept in the SQL subset shared by PostgreSQL and H2 (tests run this exact text).

-- Existing rows belong to the reserved legacy tenant (same convention as product-service V6).
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tenant_id UUID NOT NULL DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS store_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS vendor_id VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS order_type VARCHAR(12) NOT NULL DEFAULT 'RETAIL';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_name VARCHAR(160);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_email VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS customer_phone VARCHAR(40);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_address VARCHAR(500);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS special_instructions VARCHAR(500);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(40);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(80);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS fulfillment_status VARCHAR(16) NOT NULL DEFAULT 'UNFULFILLED';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS refund_status VARCHAR(16) NOT NULL DEFAULT 'NONE';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS refund_reference VARCHAR(80);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(38, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_charge NUMERIC(38, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_partner_name VARCHAR(160);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_reference VARCHAR(80);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS assigned_operator_id BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS hold_reason VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS status_before_hold VARCHAR(32);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS prep_minutes INTEGER;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS processing_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS ready_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipped_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS vendor_id VARCHAR(64);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS allocated_quantity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS fulfilled_quantity INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_orders_tenant_created ON orders (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_tenant_status ON orders (tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_tenant_store ON orders (tenant_id, store_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_tenant_operator ON orders (tenant_id, assigned_operator_id);

CREATE TABLE IF NOT EXISTS order_status_history (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor_id BIGINT,
    actor_email VARCHAR(255),
    source VARCHAR(16) NOT NULL DEFAULT 'ADMIN',
    reason VARCHAR(500),
    correlation_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_osh_order ON order_status_history(tenant_id, order_id, created_at);

-- One row per (tenant, idempotency key): a retry returns the stored outcome instead of repeating the action.
CREATE TABLE IF NOT EXISTS order_admin_actions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    action VARCHAR(40) NOT NULL,
    order_id BIGINT,
    actor_id BIGINT NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    outcome VARCHAR(12) NOT NULL DEFAULT 'DONE',
    response TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_oaa_key UNIQUE (tenant_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS order_notes (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    kind VARCHAR(16) NOT NULL DEFAULT 'INTERNAL',
    body VARCHAR(2000) NOT NULL,
    author_id BIGINT NOT NULL,
    author_email VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_on_kind CHECK (kind IN ('INTERNAL', 'COMMUNICATION', 'ADMIN_COMMENT'))
);
CREATE INDEX IF NOT EXISTS idx_on_order ON order_notes(tenant_id, order_id, created_at);

CREATE TABLE IF NOT EXISTS order_fulfillments (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    location_code VARCHAR(64) NOT NULL,
    location_name VARCHAR(160),
    method VARCHAR(16) NOT NULL DEFAULT 'SHIP',
    status VARCHAR(16) NOT NULL DEFAULT 'ALLOCATED',
    packing_status VARCHAR(12) NOT NULL DEFAULT 'NOT_STARTED',
    carrier VARCHAR(64),
    tracking_reference VARCHAR(128),
    delivery_reference VARCHAR(80),
    version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    shipped_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_of_method CHECK (method IN ('SHIP', 'PICKUP', 'DELIVERY', 'DINE_IN')),
    CONSTRAINT ck_of_status CHECK (status IN ('ALLOCATED', 'PACKED', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'FAILED')),
    CONSTRAINT ck_of_packing CHECK (packing_status IN ('NOT_STARTED', 'PACKING', 'PACKED'))
);
CREATE INDEX IF NOT EXISTS idx_of_order ON order_fulfillments(tenant_id, order_id);

CREATE TABLE IF NOT EXISTS order_fulfillment_items (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    fulfillment_id BIGINT NOT NULL REFERENCES order_fulfillments(id),
    order_item_id BIGINT NOT NULL REFERENCES order_items(id),
    quantity INTEGER NOT NULL,
    CONSTRAINT ck_ofi_qty CHECK (quantity > 0),
    CONSTRAINT ux_ofi UNIQUE (fulfillment_id, order_item_id)
);
CREATE INDEX IF NOT EXISTS idx_ofi_item ON order_fulfillment_items(tenant_id, order_item_id);

CREATE TABLE IF NOT EXISTS order_exceptions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    order_id BIGINT NOT NULL REFERENCES orders(id),
    category VARCHAR(32) NOT NULL,
    priority VARCHAR(8) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(12) NOT NULL DEFAULT 'OPEN',
    summary VARCHAR(255) NOT NULL,
    assignee_id BIGINT,
    escalated_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_note VARCHAR(500),
    open_key VARCHAR(80) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_oe_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_oe_status CHECK (status IN ('OPEN', 'ASSIGNED', 'ESCALATED', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT ux_oe_open UNIQUE (tenant_id, order_id, category, open_key)
);
CREATE INDEX IF NOT EXISTS idx_oe_tenant_status ON order_exceptions(tenant_id, status, priority);

CREATE TABLE IF NOT EXISTS order_exception_events (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    exception_id BIGINT NOT NULL REFERENCES order_exceptions(id),
    action VARCHAR(24) NOT NULL,
    actor_id BIGINT,
    note VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_oee_exc ON order_exception_events(tenant_id, exception_id);

CREATE TABLE IF NOT EXISTS order_settings (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    store_id VARCHAR(64) NOT NULL DEFAULT '*',
    confirm_sla_minutes INTEGER NOT NULL DEFAULT 30,
    processing_sla_minutes INTEGER NOT NULL DEFAULT 240,
    fulfillment_sla_minutes INTEGER NOT NULL DEFAULT 1440,
    delivery_sla_minutes INTEGER NOT NULL DEFAULT 4320,
    refund_sla_minutes INTEGER NOT NULL DEFAULT 4320,
    restaurant_accept_sla_minutes INTEGER NOT NULL DEFAULT 5,
    require_payment_before_processing BOOLEAN NOT NULL DEFAULT FALSE,
    max_bulk_size INTEGER NOT NULL DEFAULT 200,
    version INTEGER NOT NULL DEFAULT 0,
    updated_by BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_os_scope UNIQUE (tenant_id, store_id),
    CONSTRAINT ck_os_minutes CHECK (confirm_sla_minutes BETWEEN 1 AND 100000 AND processing_sla_minutes BETWEEN 1 AND 100000
        AND fulfillment_sla_minutes BETWEEN 1 AND 100000 AND delivery_sla_minutes BETWEEN 1 AND 100000
        AND refund_sla_minutes BETWEEN 1 AND 100000 AND restaurant_accept_sla_minutes BETWEEN 1 AND 100000),
    CONSTRAINT ck_os_bulk CHECK (max_bulk_size BETWEEN 1 AND 1000)
);

CREATE TABLE IF NOT EXISTS order_bulk_jobs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    action VARCHAR(24) NOT NULL,
    params TEXT,
    status VARCHAR(12) NOT NULL DEFAULT 'PREVIEWED',
    total_count INTEGER NOT NULL DEFAULT 0,
    eligible_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    failure_count INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    executed_by BIGINT,
    executed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_obj_status CHECK (status IN ('PREVIEWED', 'EXECUTING', 'COMPLETED', 'EXPIRED'))
);
CREATE INDEX IF NOT EXISTS idx_obj_tenant ON order_bulk_jobs(tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS order_bulk_job_items (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    job_id BIGINT NOT NULL REFERENCES order_bulk_jobs(id),
    order_id BIGINT NOT NULL,
    eligible BOOLEAN NOT NULL,
    outcome VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    message VARCHAR(255),
    CONSTRAINT ux_obji UNIQUE (job_id, order_id)
);

CREATE TABLE IF NOT EXISTS order_saved_views (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    filters TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_osv UNIQUE (tenant_id, user_id, name)
);

-- Inbound events (delivery, payment, refund) are applied once per (tenant, source, event id).
CREATE TABLE IF NOT EXISTS order_processed_events (
    tenant_id UUID NOT NULL,
    source VARCHAR(16) NOT NULL,
    event_id VARCHAR(80) NOT NULL,
    order_id BIGINT,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, source, event_id)
);
