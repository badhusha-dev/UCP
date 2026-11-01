-- Dev-only seed: one login for every RBAC role currently installed in user-service.
-- The password for every account is: password123
-- Run after user-service Flyway migrations have completed:
--   docker exec -i ucp-postgres-1 psql -v ON_ERROR_STOP=1 -U ecommerce_admin -d user_db < scripts/dev-seed/rbac-users.sql
-- This is intentionally not a Flyway migration. It creates test credentials and must
-- never be applied to a shared or production database.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Use crypt() so the seed never stores a plaintext password. Existing accounts are
-- left untouched; re-running this script only restores these explicitly-owned test
-- accounts to their expected role and active state.
INSERT INTO users(email, password, role, name, active)
SELECT
    'rbac.' || lower(role_key) || '@ucp-rbac.test',
    crypt('password123', gen_salt('bf')),
    role_key,
    display_name || ' Test User',
    TRUE
FROM access_roles
ON CONFLICT (email) DO UPDATE
SET password = EXCLUDED.password,
    role = EXCLUDED.role,
    name = EXCLUDED.name,
    active = TRUE;

-- Make the accounts usable by the normal legacy-tenant login/application flows.
INSERT INTO tenant_memberships(tenant_id, user_id, status)
SELECT
    '00000000-0000-4000-8000-000000000001',
    u.id,
    'ACTIVE'
FROM users u
WHERE u.email LIKE 'rbac.%@ucp-rbac.test'
ON CONFLICT (tenant_id, user_id) DO UPDATE
SET status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP;

-- =====================================================================
-- PART 3: Application entitlements for the legacy development tenant.
-- Enable every currently ACTIVE application so test users can exercise
-- the full application-login flow without additional setup.
-- =====================================================================
INSERT INTO tenant_application_entitlements(tenant_id, application_code, enabled)
VALUES
    ('00000000-0000-4000-8000-000000000001', 'ECOMMERCE_ADMIN', TRUE),
    ('00000000-0000-4000-8000-000000000001', 'INVENTORY',       TRUE),
    ('00000000-0000-4000-8000-000000000001', 'ACCOUNTING',      TRUE),
    ('00000000-0000-4000-8000-000000000001', 'DELIVERY_OPS',    TRUE),
    ('00000000-0000-4000-8000-000000000001', 'QUALITY_CENTER',  TRUE)
ON CONFLICT (tenant_id, application_code) DO UPDATE
    SET enabled = TRUE, updated_at = CURRENT_TIMESTAMP;

-- =====================================================================
-- PART 4: User application grants.
-- Each test user is granted their natural application. Grants are inserted
-- in the order required by the FK chain:
--   tenant_memberships → tenant_application_entitlements → user_application_grants
-- =====================================================================

-- ECOMMERCE_ADMIN: admin, management, order, returns, pricing, storefront,
-- inventory and procurement roles that operate the e-commerce back-office.
INSERT INTO user_application_grants(tenant_id, user_id, application_code, active)
SELECT '00000000-0000-4000-8000-000000000001', u.id, 'ECOMMERCE_ADMIN', TRUE
FROM users u
WHERE u.email LIKE 'rbac.%@ucp-rbac.test'
  AND u.role IN (
      'ADMIN', 'MANAGER',
      'ORDER_MANAGER', 'ORDER_OPERATOR', 'ORDER_SUPPORT', 'ORDER_ANALYST',
      'RETURNS_MANAGER', 'RETURNS_AGENT', 'WAREHOUSE_INSPECTOR', 'REFUND_APPROVER', 'RETURNS_ANALYST',
      'PRICING_MANAGER', 'PRICING_APPROVER', 'PRICING_ANALYST',
      'MARKETING_MANAGER',
      'STOREFRONT_MANAGER', 'STOREFRONT_PUBLISHER', 'CONTENT_EDITOR',
      'INVENTORY_MANAGER', 'INVENTORY_ANALYST',
      'PROCUREMENT_MANAGER', 'PROCUREMENT_OFFICER'
  )
ON CONFLICT (tenant_id, user_id, application_code) DO UPDATE
    SET active = TRUE, granted_at = CURRENT_TIMESTAMP;

-- INVENTORY: only WAREHOUSE_OPERATOR passes the strict inventory-only role check
-- enforced by TenantAccessService.grantApplication (no supplier:view allowed).
INSERT INTO user_application_grants(tenant_id, user_id, application_code, active)
SELECT '00000000-0000-4000-8000-000000000001', u.id, 'INVENTORY', TRUE
FROM users u
WHERE u.email LIKE 'rbac.%@ucp-rbac.test'
  AND u.role = 'WAREHOUSE_OPERATOR'
ON CONFLICT (tenant_id, user_id, application_code) DO UPDATE
    SET active = TRUE, granted_at = CURRENT_TIMESTAMP;

-- ACCOUNTING: accounting and audit roles.
INSERT INTO user_application_grants(tenant_id, user_id, application_code, active)
SELECT '00000000-0000-4000-8000-000000000001', u.id, 'ACCOUNTING', TRUE
FROM users u
WHERE u.email LIKE 'rbac.%@ucp-rbac.test'
  AND u.role IN ('ACCOUNTANT', 'AUDITOR')
ON CONFLICT (tenant_id, user_id, application_code) DO UPDATE
    SET active = TRUE, granted_at = CURRENT_TIMESTAMP;

-- DELIVERY_OPS: all delivery roles (every delivery:* role passes the gate;
-- DELIVERY_RECIPIENT is accepted via the recipientOnly path).
INSERT INTO user_application_grants(tenant_id, user_id, application_code, active)
SELECT '00000000-0000-4000-8000-000000000001', u.id, 'DELIVERY_OPS', TRUE
FROM users u
WHERE u.email LIKE 'rbac.%@ucp-rbac.test'
  AND u.role IN ('DELIVERY_ADMIN', 'DISPATCHER', 'DELIVERY_PARTNER', 'DELIVERY_ANALYST', 'DELIVERY_RECIPIENT')
ON CONFLICT (tenant_id, user_id, application_code) DO UPDATE
    SET active = TRUE, granted_at = CURRENT_TIMESTAMP;

-- QUALITY_CENTER: QA_ANALYST (added by V18) and ADMIN, both hold qa:read.
INSERT INTO user_application_grants(tenant_id, user_id, application_code, active)
SELECT '00000000-0000-4000-8000-000000000001', u.id, 'QUALITY_CENTER', TRUE
FROM users u
WHERE u.email LIKE 'rbac.%@ucp-rbac.test'
  AND u.role IN ('QA_ANALYST', 'ADMIN')
ON CONFLICT (tenant_id, user_id, application_code) DO UPDATE
    SET active = TRUE, granted_at = CURRENT_TIMESTAMP;

-- =====================================================================
-- PART 5: Tenant role assignments (one per grant, role_key = platform role).
-- Must follow PART 4: tenant_role_assignments has a FK to user_application_grants.
-- =====================================================================
INSERT INTO tenant_role_assignments(tenant_id, user_id, application_code, role_key)
SELECT g.tenant_id, g.user_id, g.application_code, u.role
FROM user_application_grants g
JOIN users u ON u.id = g.user_id
WHERE u.email LIKE 'rbac.%@ucp-rbac.test'
ON CONFLICT (tenant_id, user_id, application_code) DO UPDATE
    SET role_key = EXCLUDED.role_key, assigned_at = CURRENT_TIMESTAMP;

-- Summary report.
SELECT u.email, u.role, u.active,
       string_agg(g.application_code, ', ' ORDER BY g.application_code) AS applications
FROM users u
LEFT JOIN user_application_grants g
       ON g.user_id = u.id
      AND g.tenant_id = '00000000-0000-4000-8000-000000000001'
      AND g.active
WHERE u.email LIKE 'rbac.%@ucp-rbac.test'
GROUP BY u.email, u.role, u.active
ORDER BY u.role, u.email;

COMMIT;

-- Removal (dev only, reverse FK order):
-- BEGIN;
-- DELETE FROM tenant_role_assignments
-- WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'rbac.%@ucp-rbac.test');
-- DELETE FROM user_application_grants
-- WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'rbac.%@ucp-rbac.test');
-- DELETE FROM tenant_memberships
-- WHERE user_id IN (SELECT id FROM users WHERE email LIKE 'rbac.%@ucp-rbac.test');
-- DELETE FROM users WHERE email LIKE 'rbac.%@ucp-rbac.test';
-- COMMIT;
