# Implementation Gap Analysis and Phase Plan

Each phase is delivered as vertical slices (API + DB + security + UI + tests + docs). Ownership notes reflect the sessions
working in this checkout on 2026-11-01.

| Phase | Scope | Current state | Gap to close | Owner / status |
|---|---|---|---|---|
| 1 Baseline + tests | full test run, hermetic tests, CI coverage | backend suites pass except non-hermetic cart (Kafka); CI covers 9/19 modules, no frontend tests | cart test fixed; add the 10 missing modules and `npm test` for shared-library/mfe-order/mfe-product to CI | this session — started |
| 2 Security / identity / tenancy | header spoofing, tenant isolation at gateway and services | gateway strips headers (verified by test); tenant tokens reached platform-wide services | gateway tenant boundary **done**; next: per-service tenant scoping (accounting, crm, analytics), MFA/lockout, asymmetric JWT | this session — in progress |
| 3 Gateway / service authz | per-route permission map, service-side checks | coarse gateway gates + service checks on tenant-aware services | constant-time service-token compare; remove direct host ports for crm/procurement; per-service JWT verification for header-trusting services | next |
| 4 Inventory API / data integrity | reservations, warehouses, transfers, receiving | see sibling notes | atomic reservations, order→inventory switch | **Inventory & Procurement session** |
| 5 Payment service | PSP adapter, webhooks, idempotency, refunds, reconciliation | none; order-service exposes payment/refund callbacks guarded by shared token | new `payment-service` (tokenised PSP references only, no card data), signed webhooks, idempotency keys, refund + settlement reconciliation feeding accounting | not started |
| 6 Product/pricing/cart/checkout | | pricing & storefront done; checkout single-tenant | storefront domain→tenant resolution; tenant on cart and order creation | not started |
| 7 Order/inventory/delivery workflows | | control tower + returns + delivery callback done | order→inventory reservation, delivery creation on fulfilment | partly sibling |
| 8 Ecom Admin | | catalog/pricing/storefront/orders/returns UIs | registry status ACTIVE, customers/staff/reports, UI render tests | not started |
| 9 Accounting | immutable postings, compensating entries | reversal-by-compensation exists; no tenant; no DB-level immutability | tenant column + backfill, trigger/permission preventing UPDATE/DELETE of posted lines, payment/refund postings from Payment service | not started |
| 10 Vendor / restaurant | | restaurant order flow only | vendor domain + portal | not started |
| 11 CRM + Support | | thin CRM; no support | tenant-scoped CRM; support ticket service + app | not started |
| 12 Super Admin + entitlements | | tenants, entitlements, grants | plans/limits, tenant lifecycle, registry UI | not started |
| 13 Quality Center | | qa-service platform-only | decide platform-only vs tenant, document | not started |
| 14 Analytics / automation | | cross-tenant aggregates | tenant-scoped analytics | not started |
| 15 Perf / security / readiness | | load-testing scripts exist (devops) but no recorded results this session | load test, DAST, k8s for all services | not started |

## Order of work recommended
1. Finish Phase 1 CI coverage (cheap, prevents regressions).
2. Phase 2/3 service-side: give accounting, crm and analytics their own JWT verification + tenant columns (each a slice
   with Flyway `V<n+1>` adding `tenant_id NOT NULL DEFAULT legacy`).
3. Phase 5 Payment service — largest missing domain and a blocker for real checkout.
4. Phase 6 tenant-aware checkout.
