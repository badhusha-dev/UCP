# Application Feature Matrix

Snapshot: 2026-11-01, branch `saas/delivery-verification-and-app-registry`, uncommitted working tree.
Method: static reading of code, migrations, gateway rules and frontends, plus the test runs in `TEST_EXECUTION_REPORT.md`
and the earlier module reports (`docs/*/VERIFICATION_REPORT.md`). **No authenticated end-to-end run through the live
gateway/UI has been possible** (Docker daemon off during this audit; no known login with a tenant grant), so no row below
is "verified live".

Status words: **Implemented** = code + tests exist and pass; **Partial** = real code, but missing tenant scope, tests,
UI or integration; **Missing** = nothing usable; **Broken** = code exists but a defect makes it fail or unsafe.
Completion % is an engineering estimate of required scope delivered, not a test metric.

## 1. Ten target applications

| App (registry code, status) | Existing implementation | Backend | Frontend | DB | Security / tenant | Integration | Tests | Missing work | Priority | % |
|---|---|---|---|---|---|---|---|---|---|---|
| Super Admin (`SUPER_ADMIN`, ACTIVE) | user-service `/api/platform/tenants` (tenants, app entitlements, memberships, grants, audit); `mfe-super-admin` (220 LOC) | Partial | Partial (tenant list + grants only) | V6 tenants/entitlements/grants | Platform token + `SUPER_ADMIN` role required at gateway | user-service only | user-service 12 (one Postgres test skipped without DB) | plans/billing, entitlement limits, tenant lifecycle (suspend/delete), impersonation audit, app registry UI | P1 | 35 |
| Ecom Admin (`ECOMMERCE_ADMIN`, PLANNED in registry) | shell-app admin pages; product admin, pricing & promotions, storefront studio (mfe-product); order control tower + returns (mfe-order) | Implemented for catalog/pricing/storefront/orders/returns | Implemented (logic tests only; UI never rendered in a browser per prior reports) | product V6–V8, order V7–V10 | Own JWT verification, tenant + permission per endpoint | order↔pricing (quote/redeem/reverse), order↔delivery callback | product 75, order 54+, mfe-product 17, mfe-order 20 | registry still says PLANNED; customers/CRM view, reports, staff mgmt per tenant | P1 | 65 |
| Storefront (`STOREFRONT`, PLANNED) | shell-app customer pages (catalog, cart, checkout, orders, profile); public storefront content API | Partial | Partial | product, cart, order | Customer flows use legacy platform tokens; checkout stamps the legacy (or one configured) tenant on every order | cart→order→pricing | cart 1 class, order checkout tests | tenant-resolved storefront (domain→tenant), customer accounts per tenant, real payment | P1 | 40 |
| Inventory (`INVENTORY`, ACTIVE) | inventory-service (tenant-scoped), stock-service (legacy), mfe-inventory, mfe-stock | Partial → being built by the Inventory & Procurement session | Partial | inventory V1–V4 (V4 new, sibling) | Tenant JWT filter + grant check | order uses legacy stock-service, **not** inventory reservations | inventory 27 | owned by sibling session (warehouses, transfers, atomic reservations, valuation) | P1 (sibling) | 45 |
| Accounting (`ACCOUNTING`, PLANNED) | accounting-service (ledger, journals, drafts/approval, periods, invoices, reconciliation, import/export), mfe-accounting | Partial | Partial | 21 tables in its own `accounting_db` (keeps local copies of order tables); `tenant_id` on 12 tables (V19, deployed) | Admin API verifies its own JWT + grant and is tenant-scoped (Postgres isolation test); customer endpoints legacy-only | order events → ledger | 2 test classes; **not in CI** | tenant scoping, DB-level immutability, refund/payment posting from Payment service | P1 | 45 |
| Delivery (`DELIVERY_OPS`, ACTIVE) | delivery-service (tenant-scoped JDBC, lifecycle, OTP proof, dispatch, partners, zones, trips, incidents, earnings, rules), mfe-delivery (216 LOC) | Implemented | Partial (thin UI) | delivery V1–V2 | Own JWT + grant check; gateway per-route permissions | order-service delivery-status callback | delivery 31 | Postgres verification of V1/V2, live tracking, partner mobile, UI depth | P2 | 65 |
| Vendor / Restaurant (`VENDOR_PORTAL`, PLANNED) | Restaurant order lifecycle in order-service control tower; restaurant hours/JSON-LD in storefront | Partial (restaurant only) | Missing (no vendor portal) | order V8 | n/a | n/a | order admin tests cover restaurant flow | vendor onboarding, vendor catalog/payouts, menu management, vendor portal UI | P2 | 15 |
| CRM (`CRM`, PLANNED) | crm-service (482 LOC, 13 tables), mfe-crm (331 LOC) | Partial (thin) | Partial | crm V1–V2, no tenant_id | Trusts `X-User-Role` via `StaffAccessFilter`; not tenant-scoped | none verified | 1 test class; **not in CI** | tenant scoping, segments, campaigns, customer 360 | P2 | 20 |
| Tester / Quality Center (`QUALITY_CENTER`, ACTIVE) | qa-service (catalog, runs, results), shell-app QualityCenterPage | Partial | Partial | qa V1, no tenant_id | Gateway `qa:*` permissions; platform tokens only (tenant tokens now blocked) | none | qa 3 test classes; **not in CI** | tenant scope or explicit platform-only decision, run executors | P3 | 40 |
| Support (`CUSTOMER_SUPPORT`, PLANNED) | none (no ticket service, no UI) | Missing | Missing | none | n/a | n/a | none | whole app: tickets, SLA, macros, order/return context | P2 | 0 |

## 2. Shared services

| Service / capability | Where | Status | Tenant-aware | Evidence | Missing / defects | Priority | % |
|---|---|---|---|---|---|---|---|
| Identity / login | user-service `/api/users/login`, `/login/application` | Implemented | Yes for application login (grant check) | JwtUtil issues `permissions`, `tenant_id`, `application_code` | MFA, refresh tokens, lockout evidence not found; HS256 shared secret across services | P1 | 70 |
| Tenant management | user-service `/api/platform/tenants` | Partial | Yes | V6, TenantAccessService | suspend/delete lifecycle, quotas | P1 | 50 |
| App registry | user-service `application_registry`, shared-library `applicationRegistry.ts` | Implemented (data) | n/a | V6 seed; TS registry mirrors codes | statuses stale (ECOMMERCE_ADMIN/DELIVERY_OPS) between DB seed and TS | P3 | 70 |
| Entitlements | `tenant_application_entitlements` + `user_application_grants` | Partial | Yes | grant check endpoint `/api/users/internal/authorization/check` | plan codes unused; no limits/metering | P1 | 40 |
| API gateway | api-gateway `JwtAuthenticationFilter` | Implemented, **two defects fixed this session** | Now enforces tenant-route boundary | `GatewayTenantBoundaryTest`, `GatewayHeaderSanitizationTest` | gateway actuator (`refresh`, `flyway`) exposed in non-prod profile; frontends hard-code `http://localhost:8080` | P0 | 80 |
| RBAC | user-service V4 hierarchical RBAC + per-module permission migrations V5–V12 | Implemented | Role definitions are global (not per tenant) | access_roles, access_role_permissions | per-tenant custom roles; `/api/access` edits global roles (tenant tokens now blocked) | P1 | 65 |
| Catalog | product-service | Implemented | Yes for `/api/products/admin` (V6) | 75 tests | `/api/products/admin/analytics` aggregates all tenants (blocked for tenant tokens) | P2 | 75 |
| Pricing & promotions | product-service `pricing` | Implemented | Yes | docs/pricing-promotions | loyalty rules, xlsx | P2 | 80 |
| Order | order-service | Partial | Admin APIs yes; `orders.tenant_id` exists since V8 but **checkout always writes the legacy tenant or one configured tenant** (no per-request tenant context) | 54 tests incl. Postgres | storefront tenant resolution at checkout; legacy `/api/orders` trusts the role header | P0 | 60 |
| Cart / checkout | cart-service + order-service checkout | Partial | No | cart 1 test class | tenant scope; payment step is a contract only | P1 | 50 |
| Payment | **none** — order-service callback contract (`X-Service-Token`) | Missing | — | returns/control-tower reports | Phase 5: gateway adapter, webhooks, idempotency, refunds, reconciliation | P0 | 10 |
| Inventory | inventory-service | Partial (sibling building) | Yes | 27 tests | see sibling notes | P1 | 45 |
| Procurement | procurement-service | Partial (sibling building `/ucp`) | No (legacy), sibling adding | no tests | sibling | P1 | 30 |
| Vendor | — | Missing | — | — | vendor domain | P2 | 0 |
| Restaurant | order-service restaurant workflow | Partial | via order admin | order admin tests | menus, kitchen display, vendor payouts | P2 | 25 |
| Delivery | delivery-service | Implemented | Yes | 31 tests | Postgres verification | P2 | 70 |
| Accounting | accounting-service | Partial | No | 2 test classes, not in CI | tenant scope, DB immutability | P1 | 45 |
| CRM | crm-service | Partial | No | 1 test class | most of CRM | P2 | 20 |
| Notification | notification-service (Kafka `order-events`, idempotent consumer) | Implemented | No (events carry no tenant) | 8 tests | tenant in events; channels beyond email | P2 | 60 |
| Support | — | Missing | — | — | everything | P2 | 0 |
| QA | qa-service | Partial | No | 3 test classes | see app row | P3 | 40 |
| Analytics | analytics-service, product admin analytics | Partial | **No** (cross-tenant aggregates) | 1 test class | tenant scope (blocked for tenant tokens now) | P1 | 40 |
| Audit | per-service audit tables (user, product, order, delivery, inventory, accounting, crm, qa) | Partial | Mixed | grep of `audit` tables | no central audit query, no tamper evidence | P2 | 50 |
| Config | config-server + config-repo | Implemented | n/a | compose | `refresh` exposed on actuator in non-prod | P3 | 75 |
| File / media | storefront media (product-service), RMA attachments (order-service) | Partial | Yes (both scoped) | storefront & returns reports | no shared object store; local/DB storage | P2 | 40 |
