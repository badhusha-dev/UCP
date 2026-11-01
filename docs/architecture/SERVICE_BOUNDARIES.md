# Service Boundaries: specification coverage

Maps the 34 logical services of [specification section 5.2](../product/PRODUCT_REQUIREMENTS.md) onto the 18 backend modules that exist, and checks the cross-cutting rules of sections 6 to 10.
Date 2026-11-01, at commit `bb1b7b0e`. Evidence method and status labels as in [APPLICATION_CATALOG.md](../product/APPLICATION_CATALOG.md).

The specification says to reuse existing services where their responsibility fits and to extract new ones only when scale, ownership or release independence justifies it. The "Placement" column follows that rule: "module" means the capability lives inside an existing service.

## Logical services

| # | Logical service | Current owner | Placement | Status |
|---|---|---|---|---|
| 1 | API Gateway | api-gateway | service | PARTIALLY COMPLETED: tenant route policy, header sanitising and sign-in rate limit tested; actuator over-exposed (B-19) |
| 2 | Identity and User | user-service | service | PARTIALLY COMPLETED: no refresh tokens, password reset or MFA |
| 3 | Tenant | user-service (`tenants`, `tenant_memberships`) | module | PARTIALLY COMPLETED: no legal entity, business unit, branch or department |
| 4 | Subscription and Entitlement | user-service (`tenant_application_entitlements`) | module | PARTIALLY COMPLETED: entitlements only; no plans, subscriptions, invoices or usage |
| 5 | Application Registry | user-service (`application_registry`) | module | PARTIALLY COMPLETED |
| 6 | Notification | notification-service | service | PARTIALLY COMPLETED: no templates table; consumes events with a dead-letter store |
| 7 | Audit | Per-service audit tables (accounting, user, product, order, delivery, procurement, crm, qa, inventory) | none central | PARTIALLY COMPLETED: no shared audit or security-event store |
| 8 | Configuration | config-server with nested `backend/config-repo` | service | PARTIALLY COMPLETED: config-repo has no remote (TD-07) |
| 9 | Document | none | — | NOT IMPLEMENTED |
| 10 | Workflow | none | — | NOT IMPLEMENTED |
| 11 | Catalog | product-service | service | PARTIALLY COMPLETED |
| 12 | Pricing and Promotion | product-service (`/api/products/admin/pricing`, `/internal/pricing`) | module | PARTIALLY COMPLETED |
| 13 | Cart | cart-service | service | PARTIALLY COMPLETED: not tenant-scoped |
| 14 | Order | order-service | service | PARTIALLY COMPLETED |
| 15 | Payment | none | — | NOT IMPLEMENTED: BLOCKED on PSP choice (B-01) |
| 16 | Return and Refund | order-service (RMA), accounting-service (`refund_requests`) | module | PARTIALLY COMPLETED: refunds confirmed by service-token callback, not by a provider |
| 17 | Storefront Content | product-service (`/api/products/admin/storefront`) | module | PARTIALLY COMPLETED |
| 18 | Marketplace / Vendor | none | — | NOT IMPLEMENTED |
| 19 | Inventory | inventory-service **and** stock-service | two services | PARTIALLY COMPLETED: overlapping ownership (see below) |
| 20 | Warehouse | inventory-service (`wms_*`) | module | PARTIALLY COMPLETED |
| 21 | Procurement | procurement-service | service | PARTIALLY COMPLETED: two schemas in one service |
| 22 | Supplier | procurement-service | module | PARTIALLY COMPLETED |
| 23 | Delivery | delivery-service | service | PARTIALLY COMPLETED |
| 24 | Restaurant / POS | none | — | NOT IMPLEMENTED |
| 25 | Accounting | accounting-service | service | PARTIALLY COMPLETED |
| 26 | HR | none | — | NOT IMPLEMENTED |
| 27 | Payroll | none | — | NOT IMPLEMENTED |
| 28 | Asset | none | — | NOT IMPLEMENTED |
| 29 | Project Management | none | — | NOT IMPLEMENTED |
| 30 | CRM | crm-service, loyalty-service | service | PARTIALLY COMPLETED: not tenant-scoped (BLOCKED, F-02) |
| 31 | Customer Support | none | — | NOT IMPLEMENTED |
| 32 | Analytics / Reporting | analytics-service, plus reports in product and accounting | service | PARTIALLY COMPLETED: platform tokens only; no saved or scheduled reports |
| 33 | AI Orchestration | none | — | NOT IMPLEMENTED |
| 34 | Integration / Webhook | none | — | NOT IMPLEMENTED |

Modules with no row in the specification: `discovery-server` (service registry), `qa-service` and `error-stack-service` (Quality Center, App 9), and the shared `outbox` library.

## Data ownership (section 6)

| Rule | Status | Evidence |
|---|---|---|
| Each service owns its database | PARTIALLY COMPLETED | One Postgres database per service in config-repo (for example `analytics_db`, `accounting_db`) |
| No duplicated ownership | IMPLEMENTED BUT BROKEN | `stock_reservation` and `back_in_stock_alert` exist in inventory, stock and product services; `stock_movement` in inventory and stock; `customer_loyalty` and `loyalty_transaction` in user-service as well as loyalty-service; order tables (`orders`, `order_items`, `coupon`, `customer_cart`) in accounting and analytics as well as order-service. Whether the copies are event-fed projections was not re-verified. |
| No cross-service foreign keys | NOT VERIFIED | Separate databases make them impossible across databases; copies inside one database not checked |
| `tenant_id` on tenant-owned records, every query scoped | PARTIALLY COMPLETED | Tenant-aware: user, product, order, inventory, delivery, procurement (UCP API), accounting. Not scoped: analytics, cart, crm, error-stack, loyalty, notification, legacy procurement, qa, stock (K2) |
| PostgreSQL row-level security as extra defence | NOT IMPLEMENTED | No `ROW LEVEL SECURITY` or `CREATE POLICY` in any migration |
| Tenant from authenticated membership, never from the client | PARTIALLY COMPLETED | Gateway replaces client tenant headers with token values (`GatewayHeaderSanitizationTest`); nine services trust gateway headers without verifying the JWT |
| Tenant-aware cache keys | NOT IMPLEMENTED | No cache in use (F-09) |
| Tenant in event envelopes | PARTIALLY COMPLETED | `order-events` carry `tenantId` (B-27); consumers do not scope by it; other topics not checked |
| Migrations never rewritten; expand and contract | COMPLETED for this session | Every schema change since the baseline is a new version; no duplicate versions in 18 services |
| Upgrade tested from the current database version | NOT VERIFIED | Tests use H2 with Flyway off; recent migrations (user V16, order V11, inventory V4 and V5, stock V4, cart V2, loyalty V2, delivery V1 and V2) never run on Postgres |

## API conventions (section 7)

| Rule | Status | Evidence |
|---|---|---|
| Base path `/api/v1` | NOT IMPLEMENTED | No `/api/v1/` path anywhere; services use `/api/<domain>` |
| Consistent error response | PARTIALLY COMPLETED | `@RestControllerAdvice` in delivery, user, qa, order and accounting; others not checked |
| Idempotency keys for retryable and financial writes | PARTIALLY COMPLETED | order-service `IdempotencyService` (admin bulk), delivery `delivery_idempotency`; checkout and refunds not verified |
| Correlation IDs | PARTIALLY COMPLETED | `X-Correlation-Id` is read by some services; not propagated into events |
| Rate limiting | PARTIALLY COMPLETED | Sign-in only, per gateway instance (B-20) |
| OpenAPI specifications | NOT VERIFIED | springdoc annotations present; served output not checked |
| Server-side prices, payment state, tenant, accounting values | PARTIALLY COMPLETED | Checkout prices from product-service; verified user wins over body `userId` (K22); promo codes validated client-side only (B-14) |

Adopting `/api/v1` without breaking clients would mean the gateway accepting both prefixes for a period. That is a product decision and has not been started.

## Events (section 8)

| Rule | Status | Evidence |
|---|---|---|
| Common envelope (event id, type, version, occurred at, tenant, aggregate, correlation, causation, producer) | PARTIALLY COMPLETED | Outbox rows carry event id, topic, key, type and, for orders, tenant; no version, correlation, causation or producer fields |
| Transactional outbox | PARTIALLY COMPLETED | Shared `ucp-outbox` in cart, loyalty, order, inventory and stock; delivery has its own. Not run against a live broker. |
| Idempotent consumers | PARTIALLY COMPLETED | `order_processed_events` in order-service; other consumers not verified |
| Retry with backoff, dead-letter topics | PARTIALLY COMPLETED | Publisher backoff in the outbox; dead-letter store in notification-service only (F-11) |
| Schema versioning, replay controls, consumer monitoring | NOT IMPLEMENTED | — |

## Security and permissions (section 10)

| Rule | Status | Evidence |
|---|---|---|
| Platform roles, memberships, entitlements, grants, tenant roles | PARTIALLY COMPLETED | user-service `access_roles`, `tenant_memberships`, `tenant_role_assignments`, `tenant_application_entitlements`, `user_application_grants` |
| Branch scopes | NOT IMPLEMENTED | No branch entity |
| Permission naming | PARTIALLY COMPLETED | Existing permissions use `area:action` (for example `analytics:read`); the specification uses `area.action`. Renaming is not required for correctness and has not been planned. |
| Issuer and audience validation, asymmetric signing | NOT IMPLEMENTED | One HS256 secret across seven services (B-05); BLOCKED on key-storage decision |
| Refresh-token rotation and revocation | NOT IMPLEMENTED | F-05, B-21 |
| Gateway header sanitising | COMPLETED | `GatewayHeaderSanitizationTest` |
| Downstream authorisation | PARTIALLY COMPLETED | Six services verify the JWT; nine trust gateway headers (see [../completion/SECURITY_AUDIT.md](../completion/SECURITY_AUDIT.md)) |
| Service-to-service authentication | PARTIALLY COMPLETED | Shared `SERVICE_TO_SERVICE_TOKEN`, constant-time comparison, no default value |
| Secrets management | PARTIALLY COMPLETED | Literals removed from tracked files; rotation is the owner's task; Kubernetes secrets from a template |
| AI cannot post journals, approve payroll, refund or change permissions | Not applicable yet | No AI actions exist |
