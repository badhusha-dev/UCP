CREATE TABLE qa_features (
    feature_id VARCHAR(96) PRIMARY KEY,
    feature_name VARCHAR(180) NOT NULL,
    domain_name VARCHAR(96) NOT NULL,
    frontend_module VARCHAR(96) NOT NULL,
    backend_service VARCHAR(96) NOT NULL,
    api_endpoints TEXT NOT NULL,
    database_dependencies TEXT NOT NULL,
    permissions TEXT NOT NULL,
    coverage_status VARCHAR(24) NOT NULL,
    coverage_note TEXT NOT NULL,
    discovered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE qa_feature_tests (
    feature_id VARCHAR(96) NOT NULL REFERENCES qa_features(feature_id) ON DELETE CASCADE,
    test_id VARCHAR(180) NOT NULL,
    test_name VARCHAR(240) NOT NULL,
    test_class VARCHAR(320) NOT NULL,
    test_layer VARCHAR(32) NOT NULL,
    case_count INTEGER NOT NULL CHECK (case_count >= 0),
    permission_tested VARCHAR(120),
    PRIMARY KEY (feature_id, test_id)
);

CREATE TABLE qa_test_suites (
    suite_id VARCHAR(64) PRIMARY KEY,
    suite_name VARCHAR(160) NOT NULL,
    description TEXT NOT NULL,
    executable BOOLEAN NOT NULL,
    runner_key VARCHAR(64) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE qa_rbac_roles (
    role_key VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    source_note TEXT NOT NULL
);

CREATE TABLE qa_rbac_role_permissions (
    role_key VARCHAR(64) NOT NULL REFERENCES qa_rbac_roles(role_key) ON DELETE CASCADE,
    permission_key VARCHAR(120) NOT NULL,
    PRIMARY KEY (role_key, permission_key)
);

CREATE TABLE qa_test_runs (
    run_id UUID PRIMARY KEY,
    suite_id VARCHAR(64) NOT NULL,
    feature_id VARCHAR(96),
    environment VARCHAR(32) NOT NULL CHECK (environment = 'LOCAL_ISOLATED'),
    source_version VARCHAR(96) NOT NULL,
    requested_by BIGINT NOT NULL,
    requested_by_email VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('QUEUED','PREFLIGHT','RUNNING','FINALIZING','PASSED','FAILED','BLOCKED','CANCELLED')),
    phase VARCHAR(48) NOT NULL,
    current_test VARCHAR(320),
    total_tests INTEGER NOT NULL DEFAULT 0,
    completed_tests INTEGER NOT NULL DEFAULT 0,
    passed_tests INTEGER NOT NULL DEFAULT 0,
    failed_tests INTEGER NOT NULL DEFAULT 0,
    skipped_tests INTEGER NOT NULL DEFAULT 0,
    blocked_tests INTEGER NOT NULL DEFAULT 0,
    infrastructure_failures INTEGER NOT NULL DEFAULT 0,
    error_type VARCHAR(48),
    error_message TEXT,
    execution_log TEXT NOT NULL DEFAULT '',
    retry_of UUID REFERENCES qa_test_runs(run_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    duration_ms BIGINT
);
CREATE INDEX idx_qa_runs_created ON qa_test_runs(created_at DESC);
CREATE INDEX idx_qa_runs_status ON qa_test_runs(status, created_at DESC);
CREATE INDEX idx_qa_runs_feature ON qa_test_runs(feature_id, created_at DESC);

CREATE TABLE qa_test_results (
    result_id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES qa_test_runs(run_id) ON DELETE CASCADE,
    test_id VARCHAR(320) NOT NULL,
    test_name VARCHAR(500) NOT NULL,
    feature_id VARCHAR(96) REFERENCES qa_features(feature_id),
    service_name VARCHAR(96) NOT NULL,
    test_layer VARCHAR(32) NOT NULL,
    role_name VARCHAR(64),
    role_permission VARCHAR(120),
    status VARCHAR(32) NOT NULL CHECK (status IN ('PASSED','FAILED','SKIPPED','BLOCKED','INFRASTRUCTURE_FAILURE','TIMED_OUT')),
    duration_ms BIGINT NOT NULL DEFAULT 0,
    expected_result TEXT,
    actual_result TEXT,
    error_message TEXT,
    log_excerpt TEXT,
    evidence_reference VARCHAR(320),
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_qa_results_run ON qa_test_results(run_id, result_id);
CREATE INDEX idx_qa_results_test ON qa_test_results(test_id, executed_at DESC);

CREATE TABLE qa_active_run_lock (
    lock_id SMALLINT PRIMARY KEY CHECK (lock_id = 1),
    run_id UUID NOT NULL UNIQUE REFERENCES qa_test_runs(run_id) ON DELETE CASCADE
);

CREATE TABLE qa_audit_log (
    audit_id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT NOT NULL,
    actor_email VARCHAR(255) NOT NULL,
    action VARCHAR(48) NOT NULL,
    run_id UUID,
    details TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_qa_audit_created ON qa_audit_log(created_at DESC);
