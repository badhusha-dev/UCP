-- V1: Initial Error Stack Schema

CREATE TABLE IF NOT EXISTS error_groups (
    id BIGSERIAL PRIMARY KEY,
    fingerprint VARCHAR(64) UNIQUE NOT NULL,
    service_name VARCHAR(128) NOT NULL,
    environment VARCHAR(64) NOT NULL DEFAULT 'production',
    error_type VARCHAR(256) NOT NULL,
    message TEXT NOT NULL,
    severity VARCHAR(32) NOT NULL DEFAULT 'ERROR',
    status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    assigned_to VARCHAR(128),
    occurrences_count BIGINT NOT NULL DEFAULT 1,
    first_seen TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_error_groups_fingerprint ON error_groups (fingerprint);
CREATE INDEX IF NOT EXISTS idx_error_groups_service ON error_groups (service_name);
CREATE INDEX IF NOT EXISTS idx_error_groups_status ON error_groups (status);
CREATE INDEX IF NOT EXISTS idx_error_groups_severity ON error_groups (severity);
CREATE INDEX IF NOT EXISTS idx_error_groups_last_seen ON error_groups (last_seen DESC);

CREATE TABLE IF NOT EXISTS error_records (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES error_groups(id) ON DELETE CASCADE,
    fingerprint VARCHAR(64) NOT NULL,
    service_name VARCHAR(128) NOT NULL,
    version VARCHAR(64),
    environment VARCHAR(64) NOT NULL DEFAULT 'production',
    release_tag VARCHAR(64),
    error_type VARCHAR(256) NOT NULL,
    message TEXT NOT NULL,
    stack_trace TEXT,
    exception_chain TEXT,
    request_method VARCHAR(16),
    request_uri VARCHAR(1024),
    response_status INTEGER,
    trace_id VARCHAR(128),
    correlation_id VARCHAR(128),
    user_id BIGINT,
    user_role VARCHAR(64),
    browser VARCHAR(128),
    os VARCHAR(128),
    frontend_route VARCHAR(512),
    severity VARCHAR(32) NOT NULL DEFAULT 'ERROR',
    metadata_json TEXT,
    breadcrumbs_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_error_records_group_id ON error_records (group_id);
CREATE INDEX IF NOT EXISTS idx_error_records_fingerprint ON error_records (fingerprint);
CREATE INDEX IF NOT EXISTS idx_error_records_service ON error_records (service_name);
CREATE INDEX IF NOT EXISTS idx_error_records_created_at ON error_records (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_error_records_trace_id ON error_records (trace_id);
