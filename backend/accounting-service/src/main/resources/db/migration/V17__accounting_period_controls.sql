CREATE TABLE accounting_periods (
    id BIGSERIAL PRIMARY KEY,
    period_name VARCHAR(120) NOT NULL UNIQUE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
    closed_at TIMESTAMPTZ,
    closed_by VARCHAR(255),
    close_reason TEXT,
    created_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (end_date >= start_date)
);
CREATE INDEX idx_accounting_period_dates ON accounting_periods(start_date, end_date);
