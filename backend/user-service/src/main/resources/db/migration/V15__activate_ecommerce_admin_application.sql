-- Activate the E-commerce Admin application for tenants.
-- product-service (/api/products/admin/**: catalog, pricing, storefront) and order-service (/api/orders/admin/**: control
-- tower, returns) already accept only ECOMMERCE_ADMIN tenant tokens, verify them themselves, re-check the grant with this
-- service and scope every query by tenant (product V6-V8, order V8-V10). While the application was PLANNED no such token
-- could be issued, so none of those consoles could be used with a tenant login.
-- Roles already exist (V9-V12: PRICING_*, MARKETING_MANAGER, STOREFRONT_*, CONTENT_EDITOR, ORDER_*, RETURNS_*,
-- REFUND_APPROVER, WAREHOUSE_INSPECTOR; ADMIN/MANAGER). Tenants still need an entitlement and per-user grants.
UPDATE application_registry
SET status='ACTIVE',
    metadata=metadata || '{"independentFrontend":false,"services":["product-service","order-service"],"tenantScopedApis":["/api/products/admin","/api/orders/admin"]}'::jsonb,
    updated_at=CURRENT_TIMESTAMP
WHERE application_code='ECOMMERCE_ADMIN';
