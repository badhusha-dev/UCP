-- Activate the Accounting application for tenants.
-- accounting-service now verifies ACCOUNTING tenant tokens itself (grant re-checked against this service) and scopes every
-- admin query by tenant (accounting-service Flyway V19), so tenants can be entitled to it.
-- Permissions and roles already exist (V4): ACCOUNTANT (read/write/reconcile/import/export), AUDITOR (read/export),
-- MANAGER and ADMIN (all accounting permissions). Tenant access still needs an entitlement and a user grant per tenant.
UPDATE application_registry
SET status='ACTIVE',
    required_permissions=ARRAY['accounting:read'],
    metadata=metadata || '{"independentFrontend":false,"service":"accounting-service","tenantScopedApi":"/api/accounting/admin"}'::jsonb,
    updated_at=CURRENT_TIMESTAMP
WHERE application_code='ACCOUNTING';
