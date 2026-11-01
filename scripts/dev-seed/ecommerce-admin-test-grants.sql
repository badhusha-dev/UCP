-- Dev-only seed: read-only E-commerce Admin access (ORDER_ANALYST) for the two test accountants, each in their own test tenant.
-- Prerequisite: scripts/dev-seed/accounting-test-tenants.sql (tenants, users, memberships). Idempotent; safe to re-run.
-- Run: docker exec -i ecommerce-platform-postgres-1 psql -v ON_ERROR_STOP=1 -U ecommerce_admin -d user_db < scripts/dev-seed/ecommerce-admin-test-grants.sql

BEGIN;

INSERT INTO tenant_application_entitlements(tenant_id, application_code, enabled) VALUES
    ('a0c0a0c0-0000-4000-8000-0000000000a1', 'ECOMMERCE_ADMIN', TRUE),
    ('a0c0a0c0-0000-4000-8000-0000000000b2', 'ECOMMERCE_ADMIN', TRUE)
ON CONFLICT (tenant_id, application_code) DO UPDATE SET enabled = TRUE, updated_at = CURRENT_TIMESTAMP;

INSERT INTO user_application_grants(tenant_id, user_id, application_code, active)
SELECT s.tenant_id, u.id, 'ECOMMERCE_ADMIN', TRUE
FROM (VALUES
    ('a0c0a0c0-0000-4000-8000-0000000000a1'::uuid, 'accountant.a@ucp-accounting.test'),
    ('a0c0a0c0-0000-4000-8000-0000000000b2'::uuid, 'accountant.b@ucp-accounting.test')) s(tenant_id, email)
JOIN users u ON u.email = s.email
ON CONFLICT (tenant_id, user_id, application_code) DO UPDATE SET active = TRUE, revoked_at = NULL;

INSERT INTO tenant_role_assignments(tenant_id, user_id, application_code, role_key)
SELECT s.tenant_id, u.id, 'ECOMMERCE_ADMIN', 'ORDER_ANALYST'
FROM (VALUES
    ('a0c0a0c0-0000-4000-8000-0000000000a1'::uuid, 'accountant.a@ucp-accounting.test'),
    ('a0c0a0c0-0000-4000-8000-0000000000b2'::uuid, 'accountant.b@ucp-accounting.test')) s(tenant_id, email)
JOIN users u ON u.email = s.email
ON CONFLICT (tenant_id, user_id, application_code) DO UPDATE SET role_key = EXCLUDED.role_key, assigned_at = CURRENT_TIMESTAMP;

INSERT INTO tenant_access_audit_log(tenant_id, actor_email, action, target_type, target_id, after_value)
SELECT t, 'dev-seed', 'DEV_SEED_ECOMMERCE_ADMIN_TEST', 'TENANT', t::text, '{"source":"scripts/dev-seed/ecommerce-admin-test-grants.sql"}'::jsonb
FROM (VALUES ('a0c0a0c0-0000-4000-8000-0000000000a1'::uuid), ('a0c0a0c0-0000-4000-8000-0000000000b2'::uuid)) v(t);

COMMIT;

-- Removal (dev only):
-- BEGIN;
-- DELETE FROM tenant_role_assignments WHERE application_code='ECOMMERCE_ADMIN' AND tenant_id IN ('a0c0a0c0-0000-4000-8000-0000000000a1','a0c0a0c0-0000-4000-8000-0000000000b2');
-- DELETE FROM user_application_grants WHERE application_code='ECOMMERCE_ADMIN' AND tenant_id IN ('a0c0a0c0-0000-4000-8000-0000000000a1','a0c0a0c0-0000-4000-8000-0000000000b2');
-- DELETE FROM tenant_application_entitlements WHERE application_code='ECOMMERCE_ADMIN' AND tenant_id IN ('a0c0a0c0-0000-4000-8000-0000000000a1','a0c0a0c0-0000-4000-8000-0000000000b2');
-- COMMIT;
