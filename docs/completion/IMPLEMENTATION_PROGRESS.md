# Implementation Progress

## Phase 1 — Baseline and tests (2026-11-01)

Changed files
- `backend/cart-service/src/test/resources/application.yml`, `backend/loyalty-service/src/test/resources/application.yml`
  — `spring.kafka.producer.properties.max.block.ms: 500`. Without a broker each event send blocked 60 s; the cart suite
  ran for >10 minutes before it was stopped. After the change: cart 7/7 and loyalty 8/8 pass in seconds.
- `.github/workflows/ci-backend.yml` — matrix now includes accounting, analytics, cart, crm, error-stack, loyalty,
  procurement, qa, stock (was 9 of 18 testable modules).
- `.github/workflows/ci-frontend.yml` — builds all 15 apps (was 7); new `unit-tests` job runs `npm test` for
  shared-library, mfe-order, mfe-product on Node 22 (`--experimental-strip-types` needs 22.6+; the build job stays on 20).
  Both files parse as YAML (checked with the `yaml` package); they have not been run on GitHub.

Commands run: see TEST_EXECUTION_REPORT.

## Phase 2 — Security, identity, multi-tenancy (started)

Changed files
- `backend/api-gateway/.../security/TenantRoutePolicy.java` (new) — allow-list of routes a tenant-scoped token may reach.
- `backend/api-gateway/.../security/JwtAuthenticationFilter.java` — (a) open endpoints matched exactly / by prefix /
  per-service api-docs regex instead of `contains`; (b) tenant tokens checked against `TenantRoutePolicy` → 403.
  (The Inventory & Procurement session edits other lines of this file concurrently: `/api/procurement/ucp/` and
  `/api/inventory/ops/` gates. Both changes coexist; coordination note in the shared notes folder.)
- `backend/api-gateway/src/test/.../GatewayTenantBoundaryTest.java` (new, 5 tests) and
  `GatewayHeaderSanitizationTest.java` (new, 2 tests).
- `docker-compose.yml` — 10 services now require `SERVICE_TO_SERVICE_TOKEN` (`:?` instead of the public default).
- `.env.example` — documents `SERVICE_TO_SERVICE_TOKEN`.

Explanation: see SECURITY_AUDIT S1–S3 and MULTI_TENANCY_AUDIT.

## Phase 3 — Gateway / service authz (started)
- `backend/stock-service/.../StockInternalController.java`, `StockController.java` — service token compared with
  `MessageDigest.isEqual` (constant time).
- `backend/stock-service/src/test/.../StockServiceIntegrationTest.java` — new test: internal endpoint refuses a missing or
  near-miss token and accepts the exact one.

## Phase 1 addendum
- `backend/error-stack-service/.../ErrorRecordRepository.java`, `ErrorStackService.java` — hourly-trend query used `TO_CHAR(...,'YYYY-MM-DD HH24:00')` (Postgres-only literal); now `'YYYY-MM-DD HH24'` with `:00` appended in Java. Same keys on Postgres; the H2 test that errored now passes.
- `backend/config-repo/cart-service-docker.yml`, `loyalty-service-docker.yml` — `max.block.ms: 5000` (was default 60 s). Not redeployed.

## Results (real runs, details in TEST_EXECUTION_REPORT)
- Backend: 368 tests across 18 modules, 0 failures, 0 errors (offline, H2, no Docker).
- Frontend: 76 node tests pass; 15 apps type-check with 0 errors.
- Not run: Postgres migrations, Kafka, live gateway, UI, load, DAST.

## Unresolved defects
KNOWN_ISSUES K1–K16.

## Next phase
1. Run the edited CI on GitHub (needs a push — not done; nothing committed).
2. Phase 2/3 service-side slices: own JWT verification + `tenant_id` for accounting, then crm and analytics.
3. Phase 5 Payment service design + first slice (webhook intake with signature check and idempotency).

## Phase 2/3 slice — Accounting tenant scoping (2026-11-01)

Changed files (accounting-service unless noted)
- `pom.xml` — jjwt 0.11.5 (same as order-service).
- `accounting/security/AccountingAuthenticationFilter.java` (new) — `/api/accounting/admin/**` now verifies the JWT itself.
  Tenant tokens must be ACCOUNTING tokens (issuer, audience, jti, tenant claims) with a live grant (identity-service check);
  platform tokens keep working and are pinned to the legacy tenant. The X-User-*/X-Tenant-ID headers the controllers read are
  replaced with verified claims, so a request that bypasses the gateway cannot forge role, permission or tenant.
- `AccountingJwtVerifier`, `AccountingAuthorizationClient`, `AccountingTenantContext`, `AccountingTenantResolver` (new).
- `model/LedgerTransaction`, `RefundRequest`, `AccountingAuditLog` — `@TenantId` (Hibernate scopes queries and inserts).
- `service/AccountingService.java` — by-id loads check the tenant (Hibernate does **not** apply `@TenantId` to `findById`;
  the Postgres test proved tenant B could reverse tenant A's transaction before this fix).
- `service/AdvancedAccountingService.java` — every SQL statement scoped by `tenant_id` (applied by a script that asserted
  each match count). Also fixed a pre-existing bug: journal, invoice and draft lists failed on PostgreSQL whenever a filter
  was empty (`? IS NULL` with an untyped null) — now `CAST(? AS …)`.
- `service/SupplierPayablesService.java` (Inventory & Procurement session's file; minimal edit, they were told) —
  `ON CONFLICT(tenant_id, code)` and tenant-scoped lookups, otherwise its upsert would fail after V19.
- `db/migration/V19__tenant_scope_accounting.sql` (new) — `tenant_id NOT NULL DEFAULT legacy` on 12 tables; per-tenant
  business keys (account code, entry number, idempotency keys, invoice number, period name); composite same-tenant foreign
  keys (lines→entries/drafts/accounts, reversal, posted draft); tenant indexes.
- Tests: `AccountingSecurityAndControllerTest` admin cases now send a platform token; new
  `AccountingTenantIsolationPostgresTest` (5 tests, real Postgres + Flyway V1–V19, gated on `UCP_TEST_ACCOUNTING_POSTGRES_URL`
  pointing at a `ucp_scratch_*` database); test `jwt.secret`.
- api-gateway: `TenantRoutePolicy` allows `/api/accounting/admin/` for tenant tokens; test route + 1 new test.

Commands and results
- Scratch DB `ucp_scratch_accounting_v19`: V1–V18 applied with psql, constraint names listed and compared with the live
  `accounting_db` (identical), V19 applied cleanly.
- Scratch DB `ucp_scratch_accounting_copy`: `pg_dump accounting_db | psql` (live DB only read), V19 applied cleanly;
  all 14 existing ledger rows mapped to the legacy tenant.
- `mvnw -o -pl accounting-service test` with `UCP_TEST_ACCOUNTING_POSTGRES_URL=jdbc:postgresql://localhost:15432/ucp_scratch_accounting_test`:
  **23 tests, 0 failures** (6 controller + 12 service on H2, 5 isolation on Postgres).
- `mvnw -o -pl api-gateway test`: **38 tests, 0 failures**.

Not done / limits
- Deployed 2026-11-01: jar rebuilt (`mvnw -o -pl accounting-service package -DskipTests`), `docker compose up -d --build --no-deps accounting-service` with the stack's existing service token. Flyway applied V19 on the live `accounting_db` (14/14 ledger rows in the legacy tenant; new constraints present); container healthy. Probes: gateway `/api/accounting/admin/accounts` and `/dashboard` without token → 401; direct call to the service with forged `X-User-Role: ADMIN` and no token → refused (403, see K20). No authenticated live probe (no login credential available). The api-gateway container was not redeployed, so the new `/api/accounting/admin/` tenant allow-list entry is not live.
- `ACCOUNTING` is `PLANNED` in `application_registry`, so user-service will not issue Accounting tenant tokens yet;
  activating it needs a user-service migration (next free version after the sibling's V13) and is a product decision.
- Customer endpoints (`/api/accounting/balance|ledger|…`) still trust `X-User-Id` and stay legacy-tenant; tenant tokens are
  refused there by the gateway.
- Controller permission checks are still coarse (`role contains ADMIN` or any `accounting:` permission); the gateway route map
  enforces read/write/approve/reconcile/import/export per route.
- An unknown ledger id still surfaces as an unhandled `IllegalArgumentException` (HTTP 500) — pre-existing.
- The Postgres test does not run in CI (no Postgres service there yet).

### api-gateway redeployed (2026-11-01 ~10:25)
- `mvnw -o -pl api-gateway package` (38 tests, 0 failures), `docker compose up -d --build --no-deps api-gateway` with the stack's
  existing service token.
- Live probes against `localhost:8080`:
  - No token: `/api/orders/admin/orders/swagger` and `/api/notifications/actuator` → 401; `POST /api/users/login` stays open (400 for an empty body).
  - Test tokens were minted inside the gateway container for a random, non-existent tenant (5-minute expiry; the signing
    secret never left the container). ECOMMERCE_ADMIN tenant token with `*`: `/api/orders`, `/api/users`, `/api/access/roles`,
    `/api/accounting/balance`, `/api/products/admin/analytics/inventory`, `/api/crm/customers` → 403 from the gateway (empty
    body, no content type); `/api/orders/admin/orders` → forwarded (JSON 403 from order-service's grant check).
  - ACCOUNTING tenant token: `/api/accounting/admin/accounts` → forwarded (accounting-service request counter +3 for 3
    probes; the service then refuses because the tenant has no grant); `/api/accounting/balance` → blocked at the gateway
    (counter moved only by the metric reads/health checks).
- A positive authenticated call (real tenant with an Accounting grant) is still not possible: `ACCOUNTING` is PLANNED.

### Accounting application activated (2026-11-01 ~10:30)
- `backend/user-service/src/main/resources/db/migration/V14__activate_accounting_application.sql` (new): `ACCOUNTING` → ACTIVE,
  `required_permissions={accounting:read}`, metadata names the tenant-scoped API. Existing roles cover it (ACCOUNTANT, AUDITOR,
  MANAGER, ADMIN from V4).
- Verified on a `pg_dump` copy of the live `user_db` (scratch DB, dropped afterwards); `mvnw -o -pl user-service package`:
  12 tests, 0 failures (the Testcontainers Postgres test still cannot reach Docker from Maven and does not run).
- Redeployed user-service only (`--no-deps`); only V14 was newer than the running jar. Flyway: "now at version v14"; live
  registry shows `ACCOUNTING|ACTIVE`.
- Not verified: an end-to-end Accounting login. No tenant has an Accounting entitlement/grant and no test credentials exist;
  creating them means writing a test tenant and user into the live `user_db`, which was not done.
- Found: `ECOMMERCE_ADMIN` is still PLANNED live (KNOWN_ISSUES K21).

### Live end-to-end Accounting verification with seeded test tenants (2026-11-01 ~10:40)
Seed (dev only, reusable):
- `scripts/dev-seed/accounting-test-users.env` — generated test credentials (git-ignored via new `.gitignore` rule
  `scripts/dev-seed/*.env`; regenerate freely).
- Users registered through the real `POST /api/users/register` (passwords hashed by the app): accountant A, auditor A,
  accountant B (`@ucp-accounting.test`).
- `scripts/dev-seed/accounting-test-tenants.sql` — idempotent: tenants `ucp-test-acct-a` / `ucp-test-acct-b`, ACCOUNTING
  entitlements, memberships, grants, roles (ACCOUNTANT, AUDITOR, ACCOUNTANT), audit-log rows; removal block included.
- `scripts/dev-seed/accounting-tenant-e2e.sh` — live check through the gateway; prints statuses/ids only.

Defects found by the live run and fixed
- `docker-compose.yml`: accounting-service had no `IDENTITY_SERVICE_URL`, so its grant check called `localhost:8081` inside its
  own container and every tenant request was refused. Added `IDENTITY_SERVICE_URL=http://user-service:8081`. (The Postgres
  integration test mocks the grant client, so it could not catch this.)
- accounting `SecurityConfig`: `/error` is now permitted, so filter refusals keep their real status (401/403/503) instead of
  all becoming 403 (K20 resolved).
- Rebuilt with tests (23/23 incl. 5 on Postgres) and redeployed accounting-service. The first run right after the restart
  still saw 503s from the grant check (transient start-up/DNS); a replay from the container returned 204 and the next full run passed.

Live result: **21/21 checks pass** — application logins for all three users; cross-tenant login refused (403); both tenants
create the same account codes and the same journal idempotency key and get separate journals (ids 1 and 2, stored with tenant
A/B ids in `accounting_db`); B's journal list excludes A's; auditor can read but not post (403); accountant cannot approve
(403); tenant token refused on customer accounting and CRM (403); forged `X-Tenant-ID` ignored.
Caveat: "B cannot reverse A's journal" is refused by the gateway's `accounting:approve` rule before the service's tenant
check; the service-level cross-tenant refusal is proven by `AccountingTenantIsolationPostgresTest`, not by this live run.

Data left in the live databases (test data, removable with the SQL block in the seed file): 3 users, 2 tenants and their
grants in `user_db`; in `accounting_db`, 4 accounts, 2 journals with 4 lines and their audit rows, all under the two test
tenant ids.

### E-commerce Admin application activated and verified live (2026-11-01 ~10:50)
- `backend/user-service/src/main/resources/db/migration/V15__activate_ecommerce_admin_application.sql` (new): `ECOMMERCE_ADMIN`
  → ACTIVE (metadata lists product-service and order-service tenant APIs). `required_permissions` is informational only (no
  code reads it) and was left as is.
- Verified on a `pg_dump` copy of the live `user_db`; `mvnw -o -pl user-service package`: 12 tests, 0 failures; redeployed
  user-service only (V15 was the only change); Flyway "now at version v15"; live registry: ACCOUNTING and ECOMMERCE_ADMIN ACTIVE.
- Dev seed extended: `scripts/dev-seed/ecommerce-admin-test-grants.sql` gives the two existing test accountants a read-only
  ORDER_ANALYST E-commerce Admin grant in their own tenant (no new users).
- `scripts/dev-seed/ecommerce-admin-e2e.sh` (live, through the gateway): **12/12 pass** — E-commerce Admin logins for both
  tenants; auditor without a grant cannot log in; tenant A's order list and dashboard return 200 and the list is empty
  (`totalElements: 0`) while the legacy tenant has 90 orders; legacy order #1 → 404 for tenant A; ORDER_ANALYST refused on
  pricing admin (403); tenant token refused on legacy `/api/orders` (403); an E-commerce Admin token is refused by accounting
  admin and an Accounting token by order admin (403).
- Not verified live: write actions in the order/pricing/storefront consoles with tenant logins (no tenant has orders,
  products or prices; the test grant is read-only). Those flows are covered by the module integration tests.

### Phase 6 slice — tenant-aware checkout (2026-11-01 ~11:00)
Changed files
- product-service: `storefront/InternalStorefrontController.java` (new) — `GET /internal/storefronts/{slug}` (service token,
  fails closed) returns tenant, store and type of an ACTIVE storefront; `SecurityConfig` permits the route (the controller checks
  the token). Test: `InternalStorefrontControllerTest` (2).
- order-service:
  - `dto/CheckoutRequest.java` — optional `storefront` (public slug).
  - `service/StorefrontClient.java` (new) — resolves the slug through product-service; unknown/malformed → 400, outage → 503.
  - `service/OrderService.java` — with a storefront, the order gets that storefront's tenant, store and type (client `storeId`
    and `orderType` ignored) and is always priced by that tenant's pricing, regardless of the deployment-wide pricing flag.
    Pricing refuses products the tenant does not sell, so an order cannot include another tenant's products → 400 and no order.
    A failure after redemption releases the promotions in that tenant. Without a storefront, legacy checkout is unchanged.
  - `service/PricingClient.java` — tenant-explicit `redeem`/`reverse` overloads (legacy methods delegate with the configured tenant).
  - `admin/OrderAdminService.java` — cancellation releases promotions in the order's own tenant (and also when the global
    pricing flag is off, as long as the order has a promotion reference).
  - `controller/OrderController.java` — **security fix**: the authenticated `X-User-Id` now always wins over a `userId` in the
    request body (before, any shopper could place an order as another user).
  - Tests: new `StorefrontCheckoutTest` (6: storefront tenant/store/pricing, restaurant type, foreign product refused, unknown
    storefront, legacy unchanged, body userId cannot impersonate); `OrderPricingCheckoutTest` now verifies the tenant-explicit reverse.
- frontend `mfe-order/src/pages/OrderPage.tsx` — **fake data removed**: a failed checkout used to show a randomly numbered
  "PLACED" receipt and empty the cart; it now shows the error and keeps the cart.

Results
- `mvnw -o -pl order-service,product-service test`: order 99/99, product 77/77 (one first-run failure was my own test asserting
  RECEIVED; with no stock-service in tests the restaurant order is PENDING_INVENTORY — assertion corrected).
- mfe-order: `tsc` clean, 20/20 node tests, `vite build` ok (dist is volume-mounted, so it is live).
- Redeployed product-service and order-service (only these changes were newer than the running containers).
- Live (`scripts/dev-seed/storefront-checkout-e2e.sh`, test storefront `ucp-test-acct-a-shop` from `storefront-test.sql`):
  shopper login; unknown storefront → 400; tenant A storefront refuses a legacy-tenant product → 400 "An item is not sold by this
  storefront or cannot be priced."; malformed slug → 400; order count unchanged (90). Only refusal paths were run live, so no
  order was created and no stock moved.

Not done / limits
- No storefront UI sends `storefront` yet: the shell/mfe-order checkout is the legacy single-store flow. Wiring the storefront
  pages (Storefront Studio public site) to a cart and checkout is the next frontend slice.
- A successful live storefront order was not placed (the test tenant has no products or prices; placing one would also move
  stock in the shared legacy stock-service, which is not tenant-aware).
- Customer accounts are still platform-wide (no per-tenant customers); stock deduction still uses the legacy stock-service;
  `order-events` still carry no tenant, so notification/accounting consumers treat storefront orders as legacy.
