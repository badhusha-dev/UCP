-- Dev-only seed: two test tenants for verifying the tenant-scoped Accounting API on the local stack.
-- Prerequisite: the three users in scripts/dev-seed/accounting-test-users.env were registered through
-- POST /api/users/register (so their passwords are hashed by the application). Idempotent; safe to re-run.
-- Run: docker exec -i ecommerce-platform-postgres-1 psql -v ON_ERROR_STOP=1 -U ecommerce_admin -d user_db < scripts/dev-seed/accounting-test-tenants.sql
-- Remove: see the commented block at the end.

BEGIN;

INSERT INTO tenants(tenant_id, slug, display_name, status) VALUES
    ('a0c0a0c0-0000-4000-8000-0000000000a1', 'ucp-test-acct-a', 'UCP Test Tenant A (Accounting)', 'ACTIVE'),
    ('a0c0a0c0-0000-4000-8000-0000000000b2', 'ucp-test-acct-b', 'UCP Test Tenant B (Accounting)', 'ACTIVE')
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO tenant_application_entitlements(tenant_id, application_code, enabled) VALUES
    ('a0c0a0c0-0000-4000-8000-0000000000a1', 'ACCOUNTING', TRUE),
    ('a0c0a0c0-0000-4000-8000-0000000000b2', 'ACCOUNTING', TRUE)
ON CONFLICT (tenant_id, application_code) DO UPDATE SET enabled = TRUE, updated_at = CURRENT_TIMESTAMP;

WITH seed(tenant_id, email, role_key) AS (VALUES
    ('a0c0a0c0-0000-4000-8000-0000000000a1'::uuid, 'accountant.a@ucp-accounting.test', 'ACCOUNTANT'),
    ('a0c0a0c0-0000-4000-8000-0000000000a1'::uuid, 'auditor.a@ucp-accounting.test',    'AUDITOR'),
    ('a0c0a0c0-0000-4000-8000-0000000000b2'::uuid, 'accountant.b@ucp-accounting.test', 'ACCOUNTANT')
), resolved AS (
    SELECT s.tenant_id, u.id AS user_id, s.role_key FROM seed s JOIN users u ON u.email = s.email
), memberships AS (
    INSERT INTO tenant_memberships(tenant_id, user_id, status)
    SELECT tenant_id, user_id, 'ACTIVE' FROM resolved
    ON CONFLICT (tenant_id, user_id) DO UPDATE SET status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP
    RETURNING tenant_id
)
SELECT COUNT(*) AS memberships FROM memberships;

INSERT INTO user_application_grants(tenant_id, user_id, application_code, active)
SELECT s.tenant_id, u.id, 'ACCOUNTING', TRUE
FROM (VALUES
    ('a0c0a0c0-0000-4000-8000-0000000000a1'::uuid, 'accountant.a@ucp-accounting.test'),
    ('a0c0a0c0-0000-4000-8000-0000000000a1'::uuid, 'auditor.a@ucp-accounting.test'),
    ('a0c0a0c0-0000-4000-8000-0000000000b2'::uuid, 'accountant.b@ucp-accounting.test')) s(tenant_id, email)
JOIN users u ON u.email = s.email
ON CONFLICT (tenant_id, user_id, application_code) DO UPDATE SET active = TRUE, revoked_at = NULL;

INSERT INTO tenant_role_assignments(tenant_id, user_id, application_code, role_key)
SELECT s.tenant_id, u.id, 'ACCOUNTING', s.role_key
FROM (VALUES
    ('a0c0a0c0-0000-4000-8000-0000000000a1'::uuid, 'accountant.a@ucp-accounting.test', 'ACCOUNTANT'),
    ('a0c0a0c0-0000-4000-8000-0000000000a1'::uuid, 'auditor.a@ucp-accounting.test',    'AUDITOR'),
    ('a0c0a0c0-0000-4000-8000-0000000000b2'::uuid, 'accountant.b@ucp-accounting.test', 'ACCOUNTANT')) s(tenant_id, email, role_key)
JOIN users u ON u.email = s.email
ON CONFLICT (tenant_id, user_id, application_code) DO UPDATE SET role_key = EXCLUDED.role_key, assigned_at = CURRENT_TIMESTAMP;

INSERT INTO tenant_access_audit_log(tenant_id, actor_email, action, target_type, target_id, after_value)
SELECT t, 'dev-seed', 'DEV_SEED_ACCOUNTING_TEST', 'TENANT', t::text, '{"source":"scripts/dev-seed/accounting-test-tenants.sql"}'::jsonb
FROM (VALUES ('a0c0a0c0-0000-4000-8000-0000000000a1'::uuid), ('a0c0a0c0-0000-4000-8000-0000000000b2'::uuid)) v(t);

COMMIT;

-- Removal (dev only). Accounting rows these tenants created stay in accounting_db unless removed there too
-- (DELETE ... WHERE tenant_id IN (...) on the accounting tables, children first).
-- BEGIN;
-- DELETE FROM tenant_role_assignments WHERE tenant_id IN ('a0c0a0c0-0000-4000-8000-0000000000a1','a0c0a0c0-0000-4000-8000-0000000000b2');
-- DELETE FROM user_application_grants WHERE tenant_id IN ('a0c0a0c0-0000-4000-8000-0000000000a1','a0c0a0c0-0000-4000-8000-0000000000b2');
-- DELETE FROM tenant_memberships WHERE tenant_id IN ('a0c0a0c0-0000-4000-8000-0000000000a1','a0c0a0c0-0000-4000-8000-0000000000b2');
-- DELETE FROM tenant_application_entitlements WHERE tenant_id IN ('a0c0a0c0-0000-4000-8000-0000000000a1','a0c0a0c0-0000-4000-8000-0000000000b2');
-- DELETE FROM tenant_access_audit_log WHERE tenant_id IN ('a0c0a0c0-0000-4000-8000-0000000000a1','a0c0a0c0-0000-4000-8000-0000000000b2');
-- DELETE FROM tenants WHERE tenant_id IN ('a0c0a0c0-0000-4000-8000-0000000000a1','a0c0a0c0-0000-4000-8000-0000000000b2');
-- DELETE FROM users WHERE email LIKE '%@ucp-accounting.test';
-- COMMIT;
