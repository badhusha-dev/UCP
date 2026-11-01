# Verification report

Date: 2026-11-01. Nothing is committed. This is not a production-readiness claim.

## Test results (run offline with Maven; frontend with `node --test`)

| Suite | Result | Database |
|---|---|---|
| inventory-service (existing 9 + `DomainRulesTest` 7 + `OpsIntegrationTest` 11) | 27 passed, 0 failed | H2, and again against Docker Postgres 15 (Flyway V1-V4, Hibernate `validate`) |
| procurement-service `UcpProcurementTest` (8, new module tests) | 8 passed | H2, and again against Docker Postgres (Flyway V1-V2) |
| mfe-inventory `src/ops/model.test.ts` | 12 passed | n/a |
| user-service (existing) | 12 passed (V13 applied to H2 profile) | H2 |
| api-gateway (existing) | all passed (8+2+10+5+7+5 listed classes) | n/a |
| accounting-service (existing) | **18 errors**, context fails: `Could not resolve placeholder 'jwt.secret'` in `accountingJwtVerifier` (test config has no `jwt.secret`). Unrelated to the credit-note method I added; not fixed. The credit-note endpoint has therefore **no accounting-side test** |

Coverage against the requested test list:

- Unit: availability, reorder point, unit conversion, FIFO, weighted average, transfer state machine, receiving/PO maths: covered (`DomainRulesTest`, `PoRules` in procurement tests).
- Integration: transfer workflow (stock moves only at dispatch/acceptance), PO workflow and receiving, supplier return with accounting call (accounting client mocked), reservation lifecycle. **Order-service reservation/release is not tested because order-service is not wired to inventory reservations.**
- Concurrency: 30 simultaneous reservations on 10 units -> exactly 10 succeed; duplicate and concurrent receipts add stock once (inventory and procurement). Duplicate order events and concurrent adjustments are **not** tested (no order consumer; adjustments are idempotent by key but not race-tested).
- Security: tenant isolation (warehouses, suppliers, POs), RBAC 403s, missing/foreign-application token 401, service-token fail-closed on internal API, header tenant ignored.
- Frontend: listing status/availability, warehouse form, PO form, receiving, transfer workflow actions, dashboard cards, CSV: pure model tests only. **No component render tests and the UI was never opened in a browser.**

## Live Docker check (local stack)

Rebuilt jars and frontend dist, `docker compose up -d --build` for inventory, procurement, accounting, user-service, api-gateway, mfe-inventory, shell-app. Docker Desktop and Kafka had to be restarted first (stale Zookeeper session).

Verified live:
- Flyway applied inventory V4, procurement V2, user-service V13 successfully; the 17-permission rows exist in `access_permissions`.
- Gateway returns 401 for `/api/inventory/ops/dashboard` and `/api/procurement/ucp/suppliers` without a token.
- Inventory internal API: 401 without service token; with the token and the legacy tenant id, `/internal/inventory/forecast` returned the seeded products classified DEAD_STOCK (no history).

Not verified live: any authenticated flow (no tenant login with the new permissions is known), the UI in a browser, the credit-note endpoint, receipts through procurement to inventory across containers. The `SERVICE_TO_SERVICE_TOKEN` was supplied only as a process environment variable for the deploy; `.env` still lacks it, so a plain `docker compose up` fails until it is added.

## Completed, partial, blocked

Completed (backend + tests + UI wiring): warehouses/locations, per-warehouse stock, transfers, adjustments with approval, atomic/idempotent reservations with partial allocation, suppliers/catalog/performance, PO lifecycle and document, goods receiving, supplier returns with accounting credit note, replenishment to draft PO, rule-based forecast, FIFO and weighted-average valuation with uncosted-unit reconciliation, tenant isolation, 17 permissions, dashboard and 21 navigation entries.

Partial:
- Cycle counting = counts recorded as adjustments (no count sessions or blind counts); Stock Audits = audit log.
- Dashboard filters: warehouse only; tenant from token; category/status/date filters exist on lists, not on the dashboard; no vendor filter.
- PO document is a generated HTML file, not the existing reporting service.
- Valuation method config is stored but reports always show both methods.
- Over-receipt tolerance is a constant (5%) in procurement, not the per-tenant setting.
- Shell route `/inventory` still uses the old platform role gate (`MANAGER/ADMIN/SUPER_ADMIN` + `inventory:read`); tenant-only roles use the standalone app login.
- `receive` incoming adjustment for rejected/damaged units is a separate non-idempotent call after the receipt is stored.
- Legacy `procurement-service` endpoints stay single-tenant and open behind the gateway; new data is in separate `proc_*` tables, legacy data is not migrated.
- Reservation expiry job uses the old non-atomic path (the job is now enabled).

Not done: Order Service integration (still deducts via stock-service), restaurant ingredient inventory (only unit-conversion maths), bulk operations with approval, variant-level stock, CSV import for the new workspace, all notification alerts and the notification-service consumer, ABC/seasonal forecasting, accounting-service tests for the new endpoint, browser/UI verification, production hardening.
