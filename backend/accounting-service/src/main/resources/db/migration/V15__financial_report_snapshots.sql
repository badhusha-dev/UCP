CREATE TABLE financial_report_snapshots (
    id BIGSERIAL PRIMARY KEY,
    report_type VARCHAR(30) NOT NULL CHECK (report_type IN ('PROFIT_LOSS','BALANCE_SHEET','TAX_SUMMARY','CASH_FLOW')),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    report_data JSONB NOT NULL,
    generated_by VARCHAR(150) NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_report_snapshot_period CHECK (period_end >= period_start)
);
