-- Returns & Refund Management (RMA). Extends the existing order_return / order_return_items in place (no parallel returns tables)
-- and adds tenant-scoped policy, inspection, refund, exchange, reverse-pickup, exception and audit structures.
-- SQL kept in the PostgreSQL/H2 common subset (tests run this exact text).

ALTER TABLE order_return ADD COLUMN IF NOT EXISTS tenant_id UUID NOT NULL DEFAULT '00000000-0000-4000-8000-000000000001';
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS store_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS rma_number VARCHAR(24);
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS return_kind VARCHAR(20) NOT NULL DEFAULT 'RETAIL_RETURN';
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS resolution VARCHAR(16) NOT NULL DEFAULT 'REFUND';
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS reason_code VARCHAR(40);
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS source VARCHAR(12) NOT NULL DEFAULT 'CUSTOMER';
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS request_key VARCHAR(80);
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS policy_version_id BIGINT;
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS policy_decision TEXT;
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS risk_flags TEXT;
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS auto_approved BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS evidence_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS assigned_operator_id BIGINT;
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS info_requested_note VARCHAR(500);
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS reviewed_by BIGINT;
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS approved_by BIGINT;
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS received_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE order_return ADD COLUMN IF NOT EXISTS rma_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE order_return_items ADD COLUMN IF NOT EXISTS reason_code VARCHAR(40);
ALTER TABLE order_return_items ADD COLUMN IF NOT EXISTS approved_quantity INTEGER;
ALTER TABLE order_return_items ADD COLUMN IF NOT EXISTS received_quantity INTEGER;
ALTER TABLE order_return_items ADD COLUMN IF NOT EXISTS accepted_quantity INTEGER;
ALTER TABLE order_return_items ADD COLUMN IF NOT EXISTS refunded_quantity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE order_return_items ADD COLUMN IF NOT EXISTS condition_reported VARCHAR(16);

CREATE INDEX IF NOT EXISTS idx_or_tenant_status ON order_return(tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_or_tenant_store ON order_return(tenant_id, store_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_or_request_key ON order_return(tenant_id, request_key);
CREATE UNIQUE INDEX IF NOT EXISTS ux_or_rma_number ON order_return(tenant_id, rma_number);

CREATE TABLE IF NOT EXISTS return_policies (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    store_id VARCHAR(64) NOT NULL DEFAULT '*',
    kind VARCHAR(20) NOT NULL DEFAULT 'RETAIL_RETURN',
    name VARCHAR(120) NOT NULL,
    published_version_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_rp_scope UNIQUE (tenant_id, store_id, kind),
    CONSTRAINT ck_rp_kind CHECK (kind IN ('RETAIL_RETURN', 'RESTAURANT_CLAIM'))
);

-- Policy content is immutable once it leaves DRAFT; a return stores the version it was decided under.
CREATE TABLE IF NOT EXISTS return_policy_versions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    policy_id BIGINT NOT NULL REFERENCES return_policies(id),
    version_no INTEGER NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'DRAFT',
    config TEXT NOT NULL,
    change_note VARCHAR(255),
    submitted_by BIGINT,
    approved_by BIGINT,
    published_by BIGINT,
    published_at TIMESTAMP WITH TIME ZONE,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_rpv UNIQUE (policy_id, version_no),
    CONSTRAINT ck_rpv_status CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'PUBLISHED', 'ARCHIVED'))
);
CREATE INDEX IF NOT EXISTS idx_rpv_tenant ON return_policy_versions(tenant_id, policy_id, version_no DESC);

CREATE TABLE IF NOT EXISTS return_status_history (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    return_id BIGINT NOT NULL REFERENCES order_return(id),
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
CREATE INDEX IF NOT EXISTS idx_rsh_return ON return_status_history(tenant_id, return_id, created_at);

CREATE TABLE IF NOT EXISTS return_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    return_id BIGINT,
    entity_type VARCHAR(24) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor_id BIGINT,
    actor_email VARCHAR(255),
    detail TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ral_tenant_time ON return_audit_logs(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ral_return ON return_audit_logs(tenant_id, return_id);

CREATE TABLE IF NOT EXISTS return_internal_notes (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    return_id BIGINT NOT NULL REFERENCES order_return(id),
    body VARCHAR(2000) NOT NULL,
    author_id BIGINT NOT NULL,
    author_email VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_rin_return ON return_internal_notes(tenant_id, return_id, created_at);

-- Evidence and inspection photographs: private files, served only through authenticated, tenant- and store-checked endpoints.
CREATE TABLE IF NOT EXISTS return_attachments (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    return_id BIGINT NOT NULL REFERENCES order_return(id),
    kind VARCHAR(20) NOT NULL,
    file_name VARCHAR(160) NOT NULL,
    content_type VARCHAR(40) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_key VARCHAR(200) NOT NULL,
    note VARCHAR(255),
    uploaded_by_actor BIGINT,
    uploaded_by_customer BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ra_kind CHECK (kind IN ('CUSTOMER_EVIDENCE', 'INSPECTION', 'PICKUP_PROOF')),
    CONSTRAINT ck_ra_size CHECK (size_bytes > 0)
);
CREATE INDEX IF NOT EXISTS idx_ra_return ON return_attachments(tenant_id, return_id);

CREATE TABLE IF NOT EXISTS return_inspections (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    return_id BIGINT NOT NULL REFERENCES order_return(id),
    packaging_condition VARCHAR(16) NOT NULL,
    accessories_received BOOLEAN NOT NULL DEFAULT TRUE,
    missing_items VARCHAR(500),
    damage_details VARCHAR(1000),
    notes VARCHAR(2000),
    outcome VARCHAR(24) NOT NULL,
    inspector_id BIGINT NOT NULL,
    inspector_email VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ri_outcome CHECK (outcome IN ('ACCEPTED_FOR_REFUND', 'ACCEPTED_FOR_EXCHANGE', 'PARTIALLY_ACCEPTED', 'REJECTED', 'REQUIRES_MANUAL_REVIEW')),
    CONSTRAINT ck_ri_pack CHECK (packaging_condition IN ('INTACT', 'DAMAGED', 'MISSING', 'OPENED'))
);
CREATE INDEX IF NOT EXISTS idx_ri_return ON return_inspections(tenant_id, return_id, created_at DESC);

CREATE TABLE IF NOT EXISTS return_inspection_items (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    inspection_id BIGINT NOT NULL REFERENCES return_inspections(id),
    return_item_id BIGINT NOT NULL REFERENCES order_return_items(id),
    received_quantity INTEGER NOT NULL,
    accepted_quantity INTEGER NOT NULL,
    product_condition VARCHAR(16) NOT NULL,
    restockable BOOLEAN NOT NULL DEFAULT FALSE,
    note VARCHAR(500),
    CONSTRAINT ck_rii_qty CHECK (received_quantity >= 0 AND accepted_quantity >= 0 AND accepted_quantity <= received_quantity),
    CONSTRAINT ck_rii_cond CHECK (product_condition IN ('NEW', 'LIKE_NEW', 'USED', 'DAMAGED', 'DEFECTIVE', 'WRONG_ITEM')),
    CONSTRAINT ux_rii UNIQUE (inspection_id, return_item_id)
);

CREATE TABLE IF NOT EXISTS return_refunds (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    return_id BIGINT NOT NULL REFERENCES order_return(id),
    order_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL DEFAULT 'RETURN',
    method VARCHAR(16) NOT NULL DEFAULT 'ORIGINAL',
    status VARCHAR(16) NOT NULL DEFAULT 'CALCULATED',
    original_amount NUMERIC(19,2) NOT NULL,
    previously_refunded NUMERIC(19,2) NOT NULL DEFAULT 0,
    items_amount NUMERIC(19,2) NOT NULL,
    discount_allocation NUMERIC(19,2) NOT NULL DEFAULT 0,
    tax_adjustment NUMERIC(19,2) NOT NULL DEFAULT 0,
    delivery_fee_refund NUMERIC(19,2) NOT NULL DEFAULT 0,
    fees NUMERIC(19,2) NOT NULL DEFAULT 0,
    final_amount NUMERIC(19,2) NOT NULL,
    calculation TEXT,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    approved_by BIGINT,
    approved_at TIMESTAMP WITH TIME ZONE,
    requested_by BIGINT,
    completed_at TIMESTAMP WITH TIME ZONE,
    failure_reason VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_rr_kind CHECK (kind IN ('RETURN', 'PRICE_DIFFERENCE', 'CLAIM')),
    CONSTRAINT ck_rr_method CHECK (method IN ('ORIGINAL', 'STORE_CREDIT')),
    CONSTRAINT ck_rr_status CHECK (status IN ('CALCULATED', 'APPROVED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_rr_amounts CHECK (final_amount >= 0 AND original_amount >= 0 AND previously_refunded >= 0 AND fees >= 0)
);
CREATE INDEX IF NOT EXISTS idx_rr_return ON return_refunds(tenant_id, return_id);
CREATE INDEX IF NOT EXISTS idx_rr_status ON return_refunds(tenant_id, status);

-- Attempts only: the money movement itself and its transaction record belong to the Payment service; we store its reference and outcome.
CREATE TABLE IF NOT EXISTS return_refund_attempts (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    refund_request_id BIGINT NOT NULL REFERENCES return_refunds(id),
    attempt_no INTEGER NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    amount NUMERIC(19,2) NOT NULL,
    payment_reference VARCHAR(100),
    failure_reason VARCHAR(255),
    requested_by BIGINT,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ux_rt_attempt UNIQUE (refund_request_id, attempt_no),
    CONSTRAINT ux_rt_key UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT ck_rt_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS exchange_requests (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    return_id BIGINT NOT NULL REFERENCES order_return(id),
    return_item_id BIGINT NOT NULL REFERENCES order_return_items(id),
    new_product_id BIGINT NOT NULL,
    new_variant VARCHAR(80),
    new_product_name VARCHAR(220),
    quantity INTEGER NOT NULL,
    old_unit_price NUMERIC(19,2) NOT NULL,
    new_unit_price NUMERIC(19,2) NOT NULL,
    price_difference NUMERIC(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL',
    stock_checked_at TIMESTAMP WITH TIME ZONE,
    stock_available INTEGER,
    replacement_order_id BIGINT,
    approved_by BIGINT,
    approved_at TIMESTAMP WITH TIME ZONE,
    note VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_er_status CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'FULFILLMENT_CREATED', 'DISPATCHED', 'CANCELLED')),
    CONSTRAINT ck_er_qty CHECK (quantity > 0)
);
CREATE INDEX IF NOT EXISTS idx_er_return ON exchange_requests(tenant_id, return_id);

CREATE TABLE IF NOT EXISTS reverse_pickup_requests (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    return_id BIGINT NOT NULL REFERENCES order_return(id),
    pickup_address VARCHAR(500) NOT NULL,
    contact_phone VARCHAR(40),
    scheduled_from TIMESTAMP WITH TIME ZONE,
    scheduled_to TIMESTAMP WITH TIME ZONE,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    delivery_reference VARCHAR(80),
    partner_name VARCHAR(160),
    tracking_reference VARCHAR(128),
    proof_reference VARCHAR(160),
    attempts INTEGER NOT NULL DEFAULT 0,
    failure_reason VARCHAR(255),
    destination VARCHAR(160),
    version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_rpr_status CHECK (status IN ('REQUESTED', 'SCHEDULED', 'PICKED_UP', 'IN_TRANSIT', 'AT_WAREHOUSE', 'FAILED', 'CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_rpr_return ON reverse_pickup_requests(tenant_id, return_id);

CREATE TABLE IF NOT EXISTS return_exceptions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    return_id BIGINT NOT NULL REFERENCES order_return(id),
    category VARCHAR(32) NOT NULL,
    priority VARCHAR(8) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(12) NOT NULL DEFAULT 'OPEN',
    summary VARCHAR(255) NOT NULL,
    risk_factors VARCHAR(500),
    assignee_id BIGINT,
    escalated_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    resolution_note VARCHAR(500),
    open_key VARCHAR(80) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_rex_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_rex_status CHECK (status IN ('OPEN', 'ASSIGNED', 'ESCALATED', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT ux_rex_open UNIQUE (tenant_id, return_id, category, open_key)
);
CREATE INDEX IF NOT EXISTS idx_rex_tenant_status ON return_exceptions(tenant_id, status, priority);

CREATE TABLE IF NOT EXISTS return_exception_events (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    exception_id BIGINT NOT NULL REFERENCES return_exceptions(id),
    action VARCHAR(24) NOT NULL,
    actor_id BIGINT,
    note VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_rexe_exc ON return_exception_events(tenant_id, exception_id);

-- Tenant refund configuration: validated numbers only.
CREATE TABLE IF NOT EXISTS return_settings (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    high_value_refund_threshold NUMERIC(19,2) NOT NULL DEFAULT 500.00,
    refund_approval_threshold NUMERIC(19,2) NOT NULL DEFAULT 100.00,
    repeat_return_count INTEGER NOT NULL DEFAULT 3,
    repeat_return_days INTEGER NOT NULL DEFAULT 30,
    pickup_failure_limit INTEGER NOT NULL DEFAULT 2,
    lost_shipment_days INTEGER NOT NULL DEFAULT 14,
    review_sla_hours INTEGER NOT NULL DEFAULT 48,
    refund_sla_hours INTEGER NOT NULL DEFAULT 72,
    version INTEGER NOT NULL DEFAULT 0,
    updated_by BIGINT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_rs_tenant UNIQUE (tenant_id),
    CONSTRAINT ck_rs_values CHECK (high_value_refund_threshold > 0 AND refund_approval_threshold >= 0 AND repeat_return_count BETWEEN 2 AND 100
        AND repeat_return_days BETWEEN 1 AND 365 AND pickup_failure_limit BETWEEN 1 AND 20 AND lost_shipment_days BETWEEN 1 AND 365
        AND review_sla_hours BETWEEN 1 AND 2000 AND refund_sla_hours BETWEEN 1 AND 2000)
);

-- A replacement order created by an exchange points back at its return; if the new item costs more, processing waits for that payment.
ALTER TABLE orders ADD COLUMN IF NOT EXISTS exchange_of_return_id BIGINT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS exchange_payment_required BOOLEAN NOT NULL DEFAULT FALSE;
