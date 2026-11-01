CREATE TABLE suppliers (
    id BIGSERIAL PRIMARY KEY,
    supplier_code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL,
    contact_name VARCHAR(150),
    email VARCHAR(180),
    phone VARCHAR(60),
    payment_terms_days INTEGER NOT NULL DEFAULT 30 CHECK (payment_terms_days >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    address TEXT,
    notes TEXT,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE supplier_products (
    id BIGSERIAL PRIMARY KEY,
    supplier_id BIGINT NOT NULL REFERENCES suppliers(id),
    product_id BIGINT NOT NULL,
    supplier_sku VARCHAR(80),
    unit_cost NUMERIC(19,4) NOT NULL CHECK (unit_cost >= 0),
    lead_time_days INTEGER NOT NULL DEFAULT 7 CHECK (lead_time_days >= 0),
    minimum_order_quantity INTEGER NOT NULL DEFAULT 1 CHECK (minimum_order_quantity > 0),
    preferred BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (supplier_id, product_id)
);
CREATE INDEX idx_supplier_products_product ON supplier_products(product_id, active);

CREATE TABLE purchase_orders (
    id BIGSERIAL PRIMARY KEY,
    po_number VARCHAR(40) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL REFERENCES suppliers(id),
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','APPROVED','ORDERED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED')),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    subtotal NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
    expected_date DATE,
    note TEXT,
    created_by VARCHAR(180),
    approved_by VARCHAR(180),
    approved_at TIMESTAMP,
    ordered_at TIMESTAMP,
    received_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_purchase_orders_status_date ON purchase_orders(status, created_at DESC);
CREATE INDEX idx_purchase_orders_supplier ON purchase_orders(supplier_id, created_at DESC);

CREATE TABLE purchase_order_lines (
    id BIGSERIAL PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id),
    supplier_product_id BIGINT REFERENCES supplier_products(id),
    product_id BIGINT NOT NULL,
    sku VARCHAR(80),
    description VARCHAR(220),
    quantity_ordered INTEGER NOT NULL CHECK (quantity_ordered > 0),
    quantity_received INTEGER NOT NULL DEFAULT 0 CHECK (quantity_received >= 0 AND quantity_received <= quantity_ordered),
    unit_cost NUMERIC(19,4) NOT NULL CHECK (unit_cost >= 0),
    line_total NUMERIC(19,4) NOT NULL CHECK (line_total >= 0),
    UNIQUE (purchase_order_id, product_id)
);
CREATE INDEX idx_po_lines_product ON purchase_order_lines(product_id);

CREATE TABLE goods_receipts (
    id BIGSERIAL PRIMARY KEY,
    receipt_number VARCHAR(100) NOT NULL UNIQUE,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id),
    received_by VARCHAR(180),
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE goods_receipt_lines (
    id BIGSERIAL PRIMARY KEY,
    receipt_id BIGINT NOT NULL REFERENCES goods_receipts(id),
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    stock_reference VARCHAR(150) NOT NULL UNIQUE
);

CREATE TABLE supplier_invoices (
    id BIGSERIAL PRIMARY KEY,
    invoice_number VARCHAR(80) NOT NULL,
    supplier_id BIGINT NOT NULL REFERENCES suppliers(id),
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id),
    status VARCHAR(20) NOT NULL DEFAULT 'MATCHED' CHECK (status IN ('MATCHED','APPROVED','PAID','VOID')),
    currency VARCHAR(3) NOT NULL,
    subtotal NUMERIC(19,4) NOT NULL CHECK (subtotal >= 0),
    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (tax_amount >= 0),
    total_amount NUMERIC(19,4) NOT NULL CHECK (total_amount >= 0),
    issue_date DATE NOT NULL,
    due_date DATE,
    accounting_entry_id BIGINT,
    created_by VARCHAR(180),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (supplier_id, invoice_number)
);
CREATE INDEX idx_supplier_invoices_status_due ON supplier_invoices(status, due_date);
CREATE TABLE supplier_invoice_lines (
    id BIGSERIAL PRIMARY KEY,
    supplier_invoice_id BIGINT NOT NULL REFERENCES supplier_invoices(id),
    product_id BIGINT NOT NULL,
    quantity NUMERIC(10,2) NOT NULL CHECK (quantity > 0),
    unit_cost NUMERIC(19,4) NOT NULL CHECK (unit_cost >= 0),
    line_total NUMERIC(19,4) NOT NULL CHECK (line_total >= 0)
);

CREATE TABLE procurement_audit (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(40) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(60) NOT NULL,
    actor VARCHAR(180),
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_procurement_audit_entity ON procurement_audit(entity_type, entity_id, created_at DESC);
