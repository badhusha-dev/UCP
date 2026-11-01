# Multi-Tenancy Audit

Date 2026-11-01. Tenant model: `tenants`, `tenant_application_entitlements`, `user_application_grants` in user-service (V6).
An application login (`/api/users/login/application`) issues a token with `tenant_id`, `tenant_role`, `application_code`,
`permissions`. Legacy logins issue platform tokens without tenant claims; their data lives in the legacy tenant
`00000000-0000-4000-8000-000000000001`.

## Per service

| Service | tenant_id in schema | Enforcement | Verdict |
|---|---|---|---|
| user-service | tenants/grants (V6) | grant check endpoint; platform routes need SUPER_ADMIN platform token | OK for tenancy tables; RBAC roles are global |
| product-service | V6 catalog, V7 pricing, V8 storefront | `CatalogTenantAuthenticationFilter` (ECOMMERCE_ADMIN token) + tenant in every query (tests in prior reports) | OK, **except** `/admin/analytics/inventory` (cross-tenant aggregate) |
| order-service | `orders.tenant_id` (V8), RMA tables (V9/V10) | Admin APIs verify token + tenant (54 tests incl. Postgres); legacy `/api/orders/**` does not | Partial — checkout writes the legacy (or one configured) tenant |
| delivery-service | V1/V2 | own JWT + grant check, tenant-scoped JDBC | OK (Postgres run pending) |
| inventory-service | V1–V4 | tenant filter + grant check | OK (sibling session extending) |
| accounting-service | V19 (12 tables, composite same-tenant FKs) | own JWT + grant check on `/api/accounting/admin/**`, Hibernate `@TenantId` + explicit `tenant_id` in all JDBC SQL; Postgres isolation test | OK for admin API (deployed; live auth probes only unauthenticated); customer endpoints legacy-only |
| analytics, cart, crm, error-stack, loyalty, notification, procurement (legacy), qa, stock | none | none | **Not multi-tenant** |

## Fix applied this session (Phase 2)
`TenantRoutePolicy` in the gateway: a token with `application_code` may only reach
`/api/delivery/**`, `/api/inventory/**`, `/api/procurement/ucp/**`, `/api/products/admin/**` (except analytics),
`/api/orders/admin/**` (except analytics), public catalog and own cart, and the caller's own `/api/users/{id}`
(not role/status/loyalty-earn). Everything else answers 403 for tenant tokens. Platform tokens are unchanged, so the
existing single-tenant shell keeps working. This makes cross-tenant access fail closed until each listed service is
tenant-scoped; it does not make those services multi-tenant.

## Remaining gaps (ordered)
1. Checkout / storefront have no tenant resolution (domain → tenant); every customer order lands in one tenant.
2. Accounting admin is tenant-scoped since V19 (deployed); order-event postings and customer endpoints still land in the legacy tenant because events carry no tenant.
3. ~~The notification consumer's `OrderEvent` DTO has no tenant field, so `order-events` consumers cannot scope by tenant.~~ Resolved 2026-11-01: `order-events` carry `tenantId` and notification's DTO has the field. Consumers do not scope by it yet.
4. RBAC roles are global; a tenant cannot define its own roles.
5. Store-level scope relies on a `store_ids` claim that user-service does not issue (fail closed today).
