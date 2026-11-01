# Verification report — Pricing & Promotions

Branch `saas/delivery-verification-and-app-registry`, working tree (nothing committed, nothing pushed).
Environment: Windows, offline Maven (`mvnw.cmd -o`), H2 in PostgreSQL mode with Flyway disabled, no Docker/Postgres/Kafka, no browser run of the UI.

## Implemented
- **Schema** (product-service V7, new): all 11 requested tables plus `pricing_settings` and `promotion_order_applications`; tenant/store ids, audit columns, status/effective dates, indexes, unique constraints, FKs, CHECKs.
- **Prices**: create/update with optimistic version, history, bulk (dry run, all-or-nothing), rollback, CSV export/import, scheduled prices with approval, cancel and an applier job; cost price behind `pricing:cost:read`; margin and threshold enforcement.
- **Campaigns**: percentage, fixed, Buy X Get Y, bundle, quantity tier, automatic, segment, first-order; product/category/store-wide scope with exclusions; min order, max discount, usage limits, priority, stacking; approval workflow, publish/pause/resume/cancel; conflict detection and calendar; performance from real redemptions; audit log.
- **Coupons**: single and bulk generation, restrictions, expiry, pause/activate/revoke, redemption history, concurrency-safe and idempotent redemption, reversal.
- **Server-side calculation** and checkout integration: `/internal/pricing/*`; order-service `PricingClient` (flagged off by default) ignores client prices when on.
- **RBAC**: 13 permission keys + 4 roles (user-service V9), gateway coarse route gate, per-endpoint checks, tenant isolation in every query.
- **UI** (mfe-product, 15 sections under *Pricing & Promotions*), all backed by the real API; sections hidden by permission.
- **Docs**: this folder (ERD, API, business rules, RBAC, deploy).

## Tested (all green in this environment)
| Module | Result |
|---|---|
| product-service | 55 tests, 0 failures — of which 32 new: `DiscountEngineTest` (13: percentage, fixed, caps, margin, eligibility, BXGY, bundle, tiers, stacking policies, invalid lines, exclusions) and `PricingApiIntegrationTest` (19, incl. product min-selling-price/max-discount/tax/effective-window rules and dashboard money: auth/permissions, price validation incl. zero/negative/invalid currency, history, rollback, cost masking, bulk, CSV, scheduled flow + applier, tenant isolation, campaign validation, approval workflow, conflicts/calendar, bulk coupons, end-to-end campaign→coupon→quote→redeem→replay→immutability→reversal, expired/future/paused/unknown/restricted coupons, **25-way concurrent redemption of a 5-use coupon (exactly 5 succeed)**, 6-way concurrent same-order redemption (one redemption), automatic multi-promotion stacking, settings validation, internal-API auth). The 23 existing product tests still pass. |
| order-service | 21 tests, 0 failures (3 new in `OrderPricingCheckoutTest`: server price/total replaces tampered client price; refused redemption blocks the order; failure after redemption triggers reversal) |
| api-gateway | 26 tests, 0 failures (3 new: `PricingRoutePermissionTest`) |
| user-service | 12 run, 0 failures (`TenantAccessPostgresIntegrationTest` runs 0 — needs Postgres) |
| mfe-product logic | 9 `node --test` tests pass (`tests/pricingModel.test.ts`: permission-gated navigation, price/campaign/CSV validation, request building) |
| mfe-product build | `vite build` succeeds; `tsc` reports no errors in the new files |

The migration text V7 was executed by the integration tests on H2, so syntax/constraints/FKs are exercised there.

## Blocked (could not be verified here)
- **PostgreSQL**: V7 and user-service V9 (uses `ON CONFLICT`) were not run on Postgres; H2 PostgreSQL mode is not identical. Row-lock behaviour (`FOR UPDATE`, guarded UPDATEs) was only exercised on H2 (H2's MVStore serialises the concurrent writers; the guarded-UPDATE design is standard, but real-Postgres concurrency is unproven).
- **Live stack**: no gateway → product-service → user-service grant check → order-service run; no Kafka; no docker-compose/k8s run.
- **UI never rendered or clicked**, no browser screenshots. No React component-render tests: the repo has no vitest/jsdom/testing-library installed (offline), so only the pure logic module is unit-tested.
- Playwright/e2e UI test of "campaign creation + checkout": the equivalent API-level end-to-end test exists; a browser e2e does not.

## Pending / known gaps (honest list)
- **Excel (.xlsx)** import/export not implemented — CSV only.
- **Loyalty-based promotions** not built (CRM/loyalty segment feeds are not integrated); customer segment and first-order flag must be supplied by the caller. No CRM lookup was added.
- **Order cancellation → `reverse`** is wired in `OrderService.cancelOrder` (best-effort: a pricing outage is logged and does not block the cancel, so usage could stay consumed; no retry queue).
- **Order-service has no tenant model**: checkout integration is single-tenant per deployment (`pricing.integration.tenant-id`) and off by default; the legacy `/api/orders/coupons` global coupons are unchanged and unscoped.
- Scheduled prices store `effective_to` but do not revert automatically. Per-customer limits are enforced for coupon campaigns only. Variant-level prices exist in the model/API, but there is no UI picker for variants; "stores" are free-form strings (no store master table exists in the repo).
- Not touched because another session has uncommitted edits there: `docker-compose.yml`, `.env.example`, `kubernetes/**` (no env wiring for the optional pricing integration; see README), `frontend/shared-library` registry (`ECOMMERCE_ADMIN` remains PLANNED). Added edits inside shared files: one line in `api-gateway/.../JwtAuthenticationFilter.java` (`routePermission`) and two menu lines in `frontend/shell-app/src/App.tsx` (shell not type-checked here).
- Performance page shows redemptions for coupon usage and `usage_count` for orders; there is no revenue/margin attribution (no order-line data in this service).
- No load/security scan (OWASP/ZAP) run; no pagination beyond hard `LIMIT 500` caps on list endpoints.
- Multi-instance deployments: the scheduled-price applier uses row locks (`FOR UPDATE`) per row but no leader election; double application is prevented by the status check, not tested with two instances.

## Addendum — gaps found against the full brief, and their status
Done: per-price **minimum selling price**, **max discount %**, **tax-inclusive flag** (stored and returned only — there is no tax engine, so no tax computation), **effective start/end** (API + UI; quotes ignore prices outside their window); the engine honours both guard rails. Dashboard now also shows expired promotions, products with a sale price, and per-currency promotion order count, total discount value and promotion-generated revenue (from `promotion_order_applications`).
Not done:
- **Conversion rate** — returned as `null` with an explanation: the platform records no storefront sessions/cart views, so there is no denominator. It is deliberately not faked.
- **Calendar grid view** (list view only) and **editing a draft campaign in the UI** (the API `PUT /campaigns/{id}` exists; the form only creates).
- **Coupon restrictions to products/categories/stores and minimum cart value** are expressed through the coupon's campaign (targets, store, min order value), not per coupon.
- **Revenue/discount totals count only orders redeemed through the checkout integration**; they are empty until `pricing.integration.enabled` is on.
- Product-level (not just price-row) restaurant/menu offerings: no restaurant catalogue model exists in this repo, so restaurant items are priced as products.
