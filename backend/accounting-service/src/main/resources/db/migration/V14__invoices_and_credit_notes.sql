CREATE TABLE invoices (
    id BIGSERIAL PRIMARY KEY,
    invoice_number VARCHAR(50) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    customer_email VARCHAR(150),
    order_id BIGINT,
    invoice_type VARCHAR(20) NOT NULL CHECK (invoice_type IN ('INVOICE','CREDIT_NOTE')),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ISSUED','PAID','OVERDUE','VOID')),
    subtotal NUMERIC(19,4) NOT NULL CHECK (subtotal >= 0),
    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (tax_amount >= 0),
    total_amount NUMERIC(19,4) NOT NULL CHECK (total_amount >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    issue_date DATE,
    due_date DATE,
    paid_date DATE,
    notes TEXT,
    idempotency_key VARCHAR(150) UNIQUE NOT NULL,
    created_by VARCHAR(150) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE invoice_line_items (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoices(id),
    description VARCHAR(255) NOT NULL,
    quantity NUMERIC(10,2) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19,4) NOT NULL CHECK (unit_price >= 0),
    tax_rate NUMERIC(5,4) NOT NULL DEFAULT 0 CHECK (tax_rate >= 0),
    line_total NUMERIC(19,4) NOT NULL CHECK (line_total >= 0),
    product_id BIGINT
);

CREATE INDEX idx_invoices_customer_date ON invoices(user_id, created_at DESC);
CREATE INDEX idx_invoices_order_id ON invoices(order_id);
