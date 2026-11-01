# Verification report — Order Management Control Tower

Working tree on `saas/delivery-verification-and-app-registry`; nothing committed or pushed.
Environment: Windows, offline Maven. Suites run on H2 (PostgreSQL mode) with the real V8 SQL, and the whole order-service suite was also run against Docker PostgreSQL 15. The stack was rebuilt and redeployed to local Docker.

## Implemented
- **Tenant model for orders** (was absent): `tenant_id`, `store_id`, `vendor_id`, `order_type`, customer snapshot, payment/refund/fulfillment fields, stage timestamps, optimistic-lock `version`; existing orders (90 in the local DB) moved to the reserved legacy tenant.
- **Self-verifying admin API** (`/api/orders/admin/**`): JWT signature/issuer/audience/application + live tenant grant; tenant and store scope only from the token; 16 `order:*` permissions enforced per endpoint; customer PII gated and unsearchable without permission.
- **State machine** for retail and restaurant, with ownership rules (payment/refund callbacks only, fulfillment-driven, delivery-owned), reasons, payment gate (tenant setting), idempotency keys, row lock + optimistic version, full status history (actor, reason, previous/new value, correlation id).
- **Search/list**: database pagination, whitelisted multi-column sort, 15 filters, 11 menu views, SLA-breach filter, CSV export (formula-safe, PII-gated, 5,000-row cap), saved views, column settings.
- **Order workspace**: overview, timeline, fulfillment (allocation, partial, split, multi-location, packing, ship, cancel), payment (read-only), customer, notes & communication, audit.
- **Restaurant workflow**: accept (prep time) / reject (reason) / prepare / delay / ready / picked up / pickup failed; excluded from warehouse flows.
- **Exception centre**: 11 categories, tenant SLA thresholds, idempotent scan (also scheduled), auto-resolve, assign / escalate / resolve / dismiss / notes, history.
- **Bulk operations**: preview → confirm → execute-once, per-order re-validation, 7 actions (no raw status change), tenant size limit.
- **Dashboard & reports** from real queries; per-tenant SLA settings.
- **Returns/refunds**: read-only window; return refunds now stay `REFUND_PENDING` until the Payment/Accounting callback confirms; no admin path to `REFUNDED`.
- **Notifications**: events with correlation ids; consumer fixed so lifecycle updates are not collapsed; SLA events never go to customers.
- **UI**: 19 menu screens (`mfe-order`), shell menu entry.
- **Docs**: this folder.

## Tested
| Suite | Result |
|---|---|
| order-service (all) | **54 run, 0 failures** on H2 and on real PostgreSQL. New: `OrderStateMachineTest` 9, `OrderAdminApiIntegrationTest` 24 |
| notification-service | 8 run, 0 failures (2 new) |
| api-gateway | 29 run, 0 failures (1 new order route test) |
| user-service | 12 run, 0 failures; V1–V11 apply on real Postgres |
| product-service | 75 run, 0 failures (unchanged) |
| mfe-order logic | 9 `node --test` tests pass; `tsc` and `vite build` clean for the new code |
| Live stack | order-service V8 / user-service V11 applied on the real databases; with `ddl-auto: validate` the service is healthy; order admin → **401** without/with a bad token; internal callbacks not reachable through the gateway; existing `/api/products` → 200 |

Covered by the API integration tests: authentication (wrong application, spoofed `X-Tenant-ID` ignored), permission matrix per target status, **tenant isolation across every endpoint** (details, timeline, notes, transitions, fulfillment, bulk, export, audit, exceptions, dashboard, saved views), store-level scope (fail closed), PII hiding/search, pagination/sorting/filter validation (SQL-ish sort rejected, wildcard is data), SLA filter and per-tenant thresholds, valid/invalid/system-only/fulfillment-driven/delivery-owned transitions, reasons, hold/release/cancellation withdraw, idempotent replay and key misuse (422), **8-way concurrent hold (exactly one wins)**, **same key sent 6× concurrently (one change)**, stale-version refusal, payment gate and Payment callback idempotency, refund-only-by-callback incl. retries and cross-tenant, restaurant lifecycle and exclusion from retail flows, over-allocation refusal, **10-way concurrent allocation of one item (exactly 2 of 10 succeed)**, split shipments, release on cancel, cannot cancel shipped, exception lifecycle (idempotent scan, auto-resolve, escalation, repeated delivery failure), bulk preview/execute/skip/re-validate/**5-way concurrent execute (applied once)**/export privacy/size limit, dashboard numbers vs seeded data, saved views per user, CSV injection guard, legacy endpoints now guarded.

## Bugs found by tests and fixed
- Exception `category` column too short for `REPEATED_DELIVERY_FAILURE` (would have dropped repeated-failure exceptions).
- JPA line-item order was not guaranteed on PostgreSQL (items could shuffle) → stable `@OrderBy`.
- Internal callbacks evaluated the request body before authenticating.
- Existing behaviours that violated the brief: unauthenticated-by-service `GET /api/orders` returning all tenants' orders; `PUT /{id}/status` accepting any string; return "REFUND" marking money refunded on a click; notification idempotency dropping every status update after the first; product/order admin calls defaulting the identity service to `localhost:8081` inside Docker (now set in compose).

## Behaviour changes to existing features (intentional)
- `PUT /api/orders/{id}/status` and cancel now enforce the state machine (e.g. PLACED→SHIPPED is refused with 409; an existing test was updated). The older admin order list page may show 409 for jumps it used to allow.
- Return "REFUND" now yields `REFUND_PENDING` (existing test updated); `orders.returns.require-refund-confirmation=false` restores the old immediate behaviour. With no Payment service in the repo, refunds stay pending until something calls the callback.
- Order JSON for the legacy customer endpoints is unchanged (new fields are hidden).

## Blocked / not verified
- **No authenticated run through the live gateway/UI** (the only active login's password is not known to me, and seeding a tenant grant was out of scope); authenticated flows are verified by integration tests on H2 and Postgres. **The UI was never opened in a browser**; no React component-render tests (no testing-library installed).
- **Kafka**: events are published and consumed in tests with mocks/in-process only; no live broker round trip was observed.
- **Inventory**: stock is deducted at checkout by existing code; this module allocates against *ordered* quantities and does not call the Inventory API for per-location availability or release stock on cancellation (the `ORDER_CANCELLED` event is published; whether stock-service restocks from it was not verified).
- **Delivery integration**: order status follows the existing Delivery progress callback, and delivery-owned statuses are protected, but the assigned delivery partner **name** is only shown if Delivery reports it (the report DTO has no partner field today), so the column is usually empty.

## Pending / not implemented
- **No Payment Service exists**: payment/refund integration is a documented callback contract only (`/internal/order-events/*`).
- **Checkout has no tenant context**: new orders go to the pricing-integration tenant if configured, otherwise the legacy tenant, so true multi-tenant checkout needs storefront → tenant resolution (the storefront slug/domain from the Storefront module is the natural source).
- Store scope for non-"all stores" users relies on a `store_ids` token claim the identity service does not issue yet; until it does, such users see nothing (fail closed), and seeded roles use `order:view-all-stores`.
- Item-level availability toggles for restaurant dishes, kitchen display notifications and operator-facing push alerts (no staff notification channel exists; SLA events are emitted but unrouted).
- Order currency is not stored (amounts display without a currency symbol); tax and delivery charge are shown as recorded, not computed.
- `delivery_status`/`assigned operator` columns exist; no operator directory lookup (ids only).
- Bulk-job history list screen, drag-and-drop, and a notification-template UI are not built. Existing analytics endpoints (`/analytics/executive`) are still platform-wide, not tenant-scoped.
- Not touched because another session has uncommitted work there: `InternalOrderDataController`, `DeliveryStatusReport`, delivery-service, the shared-library registry. One small additive hook was added to `FulfillmentAndReturnsService.applyDeliveryStatus` (record delivery in the history).
