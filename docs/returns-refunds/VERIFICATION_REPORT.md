# Returns & Refund Management — verification report

Working tree on `saas/delivery-verification-and-app-registry`; **nothing committed or pushed**. This report separates what was built, what was tested and how, what could not be verified, and what is not done. It makes no production-readiness claim: see “Not verified” and “Not done”.

## Result at a glance

| Area | Status |
|---|---|
| Schema (V9, V10, user-service V12) | **Implemented; applied to the live Docker databases** (order_db at v10, user_db at v12) |
| Eligibility engine, refund calculation, lifecycle, policies, inspection, refunds, exchanges, reverse pickup, exceptions, dashboard/analytics/audit, settings, attachments | **Implemented and tested** (API integration tests on H2 and on PostgreSQL 15) |
| Payment integration | **Partial by necessity**: no Payment service exists in this repo. Calculation, approval, attempt tracking, idempotency and the confirmation callback are built and tested against a simulated Payment; the real service must implement the contract in README §4. |
| Delivery (reverse pickup) integration | **Partial**: same — event out, authenticated progress callback in, tested with a simulated Delivery. delivery-service has no reverse-pickup endpoint. |
| Admin UI (20 screens) | **Implemented, type-checked, built and deployed**; model logic unit-tested. **Never opened in a browser; no component-render tests.** |
| Customer-facing | Request creation re-routed through the engine. **No customer UI for evidence upload or cancellation** (see Not done). |
| Authenticated end-to-end through gateway/UI on the live stack | **Not performed** (no known login with a tenant grant; seeding one was out of scope). |

## What was built

* **Backend** (`order-service/.../rma`): `ReturnStateMachine`, `Policy`/`PolicyService` (versioned, second-person review), `EligibilityEngine`, `RefundCalculator`, `ReturnService`, `InspectionService`, `RefundService`, `ExchangeService`, `PickupService`, `ReturnExceptionService`, `ReturnReportService`, `ReturnSettingsService`, `AttachmentService`, `CatalogClient`; controllers `ReturnAdminController` (`/api/orders/admin/rma/**`) and `ReturnEventsController` (`/internal/return-events/{refund,pickup}`); shared `IdempotencyService`. 13 new tables, extended `order_return`/`order_return_items`/`orders`.
* **RBAC**: 19 `return:*` permissions and 5 roles (user-service V12); gateway rule `/api/orders/admin/rma → return:view`; each endpoint also enforces its own permission; tenant and store scope from the verified token only.
* **Notifications**: `RETURN_*` events on `order-events`; the consumer sends customer messages for the steps a customer cares about, skips internal events, and is idempotent.
* **Frontend** (`mfe-order/src/returns`): console with the 20-item menu, dashboard, queues, return workspace (overview, items, evidence, pickup, inspection, refund, exchange, notes, history, exceptions; approve/partial/reject/info/pickup/inspection/refund/exchange dialogs), exceptions, exchanges, policy editor with draft/review/publish/rollback, refund configuration, analytics, audit; shell menu entry; the old `AdminReturnsPage` (which called the retired endpoints) was removed.
* **Retired** because they were unsafe: `GET /api/orders/returns`, `POST /api/orders/returns/{id}/process` (410), `/internal/order-events/return-refund`, and the legacy service methods behind them.

## Tests and results

| Suite | Result |
|---|---|
| `RmaDomainTest` (state machine, policy validation, eligibility, refund maths) | 16 / 16 |
| `ReturnsApiIntegrationTest` (HTTP, real JWT verification, real migration SQL) | 24 / 24 on H2 · **24 / 24 on PostgreSQL 15** (run with the Docker Postgres) |
| order-service full module | **93 / 93** (the three legacy tests that exercised the retired endpoints were rewritten to assert 410 / 422; one obsolete test removed) |
| notification-service | 9 / 9 (1 new: return events notify once each, internal events never reach customers) |
| api-gateway | 30 / 30 (1 new: `/api/orders/admin/rma` needs `return:view`; `order:view`/`orders:manage` are not enough) |
| user-service | 12 / 12 (V12 is also applied on the live user_db; its seeding was checked there) |
| mfe-order node tests | 20 / 20 (11 new: menu/permissions, filters, approval, inspection rules, action availability, lifecycle, refund breakdown, pickup window, policy/settings validation) |
| Frontend build | `vite build` OK for mfe-order and shell-app; the new files are clean under `tsc` (legacy pages and `shared-library` already had unrelated TS errors; not touched) |

What the API tests cover: authentication (bad/absent token, wrong application, spoofed tenant header ignored); a permission check for every protected action; **tenant isolation** on lists, details, actions, attachments, exceptions, policies, settings, dashboard, audit, and on Payment/Delivery callbacks; store scope (fail closed) and PII masking; eligibility (window, quantity caps, duplicates, undelivered orders, unknown items/reasons, cancelled quantity freed); **concurrency** — two simultaneous requests for the same units (one wins), two simultaneous “Process” clicks (one Payment attempt); idempotent create/replay and key misuse; lifecycle guards (money and carrier states cannot be set by hand, terminal states, reasons); the full happy path with its exact status trail, sources and events; Delivery callbacks out of order / repeated / skipping steps / lost / failed + reschedule; restaurant claims (no pickup/inspection, evidence gate, refund from approval); inspection validation and outcomes (partial, rejected, manual review, restock only for resellable accepted goods); refund calculation (discount, tax, delivery fee), cap across multiple refunds, maker-checker above the threshold, wrong-amount confirmation refused and flagged, failure → retry with a new attempt key, late result for a dead attempt ignored; exchange (stock unknown/zero/changed, approval order, accepted-for-exchange only, replacement order waits for payment of the difference, cheaper replacement creates a price-difference refund); policy lifecycle (validation, second-person review, publish, a case keeps the version it was filed under, rollback); attachments (content sniffing, fake PNG rejected, path stripped, private, tenant-scoped); exceptions (flags don't block, scan is idempotent, resolve needs a note); settings validation; dashboard numbers vs seeded data, views, search (LIKE wildcards are data), CSV.

## Live deployment checks (Docker, local)

* order-service healthy, Flyway V9 and V10 applied (`ddl-auto: validate` passes); user-service V12 applied (19 permissions; roles seeded: ADMIN 19, RETURNS_MANAGER 19, RETURNS_AGENT 9, REFUND_APPROVER 5, RETURNS_ANALYST 4, WAREHOUSE_INSPECTOR 3 return permissions).
* Through the gateway: RMA API → 401 without/with a bad token; `/internal/return-events/*` not routed (404); legacy `/api/orders/returns` → 401; `/api/products` still 200.
* Inside the order-service container: callbacks → 401 without/with a wrong token, 404 for an unknown attempt/reference, 400 for an invalid status; retired endpoints → **410**.
* The RMA-number backfill statement was run on the live schema inside a rolled-back transaction (`RMA-000001`, legacy tenant, default store).
* mfe-order and shell-app bundles served from the rebuilt `dist` contain the new screens/menu.

## Problems found and fixed on the way

* The accounting-service already owns a table named `refund_requests` in the shared `order_db`; the first deploy failed on it (Flyway rolled back cleanly). RMA tables were renamed `return_refunds` / `return_refund_attempts`; V10 and the tests were re-run.
* `return_attachments.kind` was one character too short (`CUSTOMER_EVIDENCE`) — caught by the first upload test.
* A wrong-amount Payment confirmation rolled back its own “review item”; the flag is now recorded outside the callback transaction.
* An analytics column alias (`day`) was rejected by H2; renamed. Column widths were then checked by hand against every value the code writes.
* A stray `&` was rendering in the shell's mobile navigation (from an earlier change); removed.

## Not verified

* **No authenticated run through the live gateway or UI** and **the UI was never opened in a browser** — no visual, accessibility or responsive check; no component-render or browser end-to-end tests. The end-to-end *flow* is covered at the API level (`fullLifecycle`), not in a browser.
* **Kafka**: events are produced/consumed in tests with mocks and in-process calls only; no live broker round trip was observed.
* No load or performance testing; list/report queries rely on the new indexes but were not measured at volume.
* PostgreSQL concurrency was exercised by the same two-thread tests only (not a stress test).
* Antivirus scanning of uploads is not part of this module (content-type sniffing, size and pixel limits are).

## Not done / known gaps

* **Real Payment and Delivery services** do not exist here; their sides of the contracts are specified, not built. Until Payment calls the callback nothing is ever marked refunded — by design, but it means refunds cannot complete in this environment without that service.
* **Customer evidence upload and customer cancellation have no customer-facing endpoint/UI**: operators attach evidence on the customer's behalf (the service method for customer cancel exists but is not exposed). The existing customer page can still create requests (reason code defaults to *Other*).
* **Store credit** is recorded and sent to Payment as the refund method; there is no wallet/credit service to receive it.
* **Variants/sizes** are stored as text on an exchange; there is no variant catalog to validate them against. Stock for the replacement is checked at proposal, approval and fulfillment but not *reserved*; allocation follows the normal fulfillment flow.
* The policy editor edits windows, reasons, switches and thresholds; per-product/category lists are carried over unchanged and can only be changed through the API.
* The legacy customer `GET /api/orders/returns/user/{id}` still returns the old entity shape (no new fields).
* Fraud/risk handling is threshold-based signalling only; there is no model or cross-account analysis, by design.
* Orders store no currency, so amounts are shown without a currency symbol; `orders.refund_status` can now also be `PARTIAL`.
* Not committed, not pushed, no pull request.
