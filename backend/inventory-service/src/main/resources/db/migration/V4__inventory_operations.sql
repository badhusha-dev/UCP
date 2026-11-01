-- Inventory & Procurement operations: warehouses, per-warehouse stock, transfers, adjustments, cost layers.
-- All tables are prefixed wms_ to avoid collisions with legacy stock/inventory tables in the shared database.

ALTER TABLE inventory_item ADD COLUMN IF NOT EXISTS unavailable_quantity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE inventory_item ADD COLUMN IF NOT EXISTS incoming_quantity INTEGER NOT NULL DEFAULT 0;
ALTER TABLE inventory_item ADD COLUMN IF NOT EXISTS category VARCHAR(80);
ALTER TABLE stock_reservation ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120);
ALTER TABLE stock_reservation ADD COLUMN IF NOT EXISTS backorder_quantity INTEGER NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX IF NOT EXISTS ux_stock_reservation_idem ON stock_reservation(tenant_id, idempotency_key);

CREATE TABLE wms_warehouse (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    store_id VARCHAR(64),
    address VARCHAR(255),
    capacity_units INTEGER NOT NULL DEFAULT 0,
    manager_email VARCHAR(180),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX ux_wms_warehouse_code ON wms_warehouse(tenant_id, code);

CREATE TABLE wms_location (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    warehouse_id BIGINT NOT NULL,
    code VARCHAR(40) NOT NULL,
    capacity_units INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX ux_wms_location_code ON wms_location(tenant_id, warehouse_id, code);

CREATE TABLE wms_stock (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    warehouse_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    on_hand INTEGER NOT NULL DEFAULT 0,
    reserved INTEGER NOT NULL DEFAULT 0,
    unavailable INTEGER NOT NULL DEFAULT 0,
    in_transit_out INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_wms_stock_nonneg CHECK (on_hand >= 0 AND reserved >= 0 AND unavailable >= 0 AND reserved + unavailable <= on_hand)
);
CREATE UNIQUE INDEX ux_wms_stock ON wms_stock(tenant_id, warehouse_id, product_id);

CREATE TABLE wms_transfer (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    transfer_no VARCHAR(40) NOT NULL,
    source_warehouse_id BIGINT NOT NULL,
    destination_warehouse_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    requested_by VARCHAR(180),
    approved_by VARCHAR(180),
    note VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX ux_wms_transfer_no ON wms_transfer(tenant_id, transfer_no);

CREATE TABLE wms_transfer_line (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    transfer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    received_quantity INTEGER NOT NULL DEFAULT 0,
    damaged_quantity INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_wms_transfer_line ON wms_transfer_line(transfer_id);

CREATE TABLE wms_adjustment (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    warehouse_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    before_quantity INTEGER NOT NULL,
    after_quantity INTEGER NOT NULL,
    delta INTEGER NOT NULL,
    reason VARCHAR(255) NOT NULL,
    evidence VARCHAR(500),
    status VARCHAR(16) NOT NULL,
    requested_by VARCHAR(180) NOT NULL,
    approved_by VARCHAR(180),
    idempotency_key VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMP WITH TIME ZONE
);
CREATE UNIQUE INDEX ux_wms_adjustment_idem ON wms_adjustment(tenant_id, idempotency_key);

CREATE TABLE wms_cost_layer (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    product_id BIGINT NOT NULL,
    warehouse_id BIGINT,
    remaining INTEGER NOT NULL,
    unit_cost NUMERIC(38, 4) NOT NULL,
    batch_no VARCHAR(80),
    expiry_date DATE,
    source_ref VARCHAR(160) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX ux_wms_cost_layer_src ON wms_cost_layer(tenant_id, source_ref, product_id);
CREATE INDEX idx_wms_cost_layer_fifo ON wms_cost_layer(tenant_id, product_id, received_at);

CREATE TABLE wms_config (
    tenant_id UUID PRIMARY KEY,
    adjustment_approval_threshold INTEGER NOT NULL DEFAULT 20,
    reservation_hold_minutes INTEGER NOT NULL DEFAULT 15,
    default_lead_time_days INTEGER NOT NULL DEFAULT 7,
    default_safety_stock INTEGER NOT NULL DEFAULT 5,
    over_receipt_tolerance_percent INTEGER NOT NULL DEFAULT 5,
    valuation_method VARCHAR(16) NOT NULL DEFAULT 'FIFO'
);

CREATE TABLE wms_audit_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    entity VARCHAR(40) NOT NULL,
    entity_id VARCHAR(60) NOT NULL,
    action VARCHAR(60) NOT NULL,
    actor VARCHAR(180),
    details VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_wms_audit_entity ON wms_audit_log(tenant_id, entity, entity_id);
