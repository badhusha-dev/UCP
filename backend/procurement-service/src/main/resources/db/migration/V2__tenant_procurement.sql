-- Tenant-scoped Inventory & Procurement tables. The legacy single-tenant tables from V1 are left untouched.
CREATE TABLE proc_supplier (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(180) NOT NULL,
    contact_name VARCHAR(150),
    email VARCHAR(180),
    phone VARCHAR(60),
    payment_terms_days INTEGER NOT NULL DEFAULT 30,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    address VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_by VARCHAR(180),
    approved_by VARCHAR(180),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX ux_proc_supplier_code ON proc_supplier(tenant_id, code);

CREATE TABLE proc_supplier_item (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    supplier_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    supplier_sku VARCHAR(80),
    unit_cost NUMERIC(19,4) NOT NULL,
    lead_time_days INTEGER NOT NULL DEFAULT 7,
    minimum_order_quantity INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE UNIQUE INDEX ux_proc_supplier_item ON proc_supplier_item(tenant_id, supplier_id, product_id);

CREATE TABLE proc_po (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    po_number VARCHAR(40) NOT NULL,
    supplier_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    subtotal NUMERIC(19,4) NOT NULL,
    discount NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax_rate NUMERIC(7,3) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    shipping NUMERIC(19,4) NOT NULL DEFAULT 0,
    total NUMERIC(19,4) NOT NULL,
    expected_date DATE,
    note VARCHAR(500),
    reject_reason VARCHAR(255),
    created_by VARCHAR(180),
    approved_by VARCHAR(180),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX ux_proc_po_number ON proc_po(tenant_id, po_number);

CREATE TABLE proc_po_line (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    po_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    accepted_quantity INTEGER NOT NULL DEFAULT 0,
    rejected_quantity INTEGER NOT NULL DEFAULT 0,
    unit_cost NUMERIC(19,4) NOT NULL,
    line_total NUMERIC(19,4) NOT NULL
);
CREATE INDEX idx_proc_po_line ON proc_po_line(tenant_id, po_id);

CREATE TABLE proc_receipt (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    receipt_no VARCHAR(80) NOT NULL,
    po_id BIGINT NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    received_by VARCHAR(180),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX ux_proc_receipt_idem ON proc_receipt(tenant_id, idempotency_key);
CREATE UNIQUE INDEX ux_proc_receipt_no ON proc_receipt(tenant_id, receipt_no);

CREATE TABLE proc_receipt_line (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    receipt_id BIGINT NOT NULL,
    po_line_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    received INTEGER NOT NULL,
    accepted INTEGER NOT NULL,
    rejected INTEGER NOT NULL DEFAULT 0,
    damaged INTEGER NOT NULL DEFAULT 0,
    batch_no VARCHAR(80),
    expiry_date DATE,
    serial_no VARCHAR(120),
    inspection_note VARCHAR(255)
);

CREATE TABLE proc_return (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    return_no VARCHAR(40) NOT NULL,
    supplier_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    total_credit NUMERIC(19,4) NOT NULL DEFAULT 0,
    accounting_entry VARCHAR(80),
    created_by VARCHAR(180),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX ux_proc_return_no ON proc_return(tenant_id, return_no);

CREATE TABLE proc_return_line (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    return_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    cost_value NUMERIC(19,4)
);

CREATE TABLE proc_audit (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    entity VARCHAR(40) NOT NULL,
    entity_id VARCHAR(60) NOT NULL,
    action VARCHAR(60) NOT NULL,
    actor VARCHAR(180),
    details VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
