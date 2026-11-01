# UCP Master Application Audit

Date 2026-11-01 · branch `saas/delivery-verification-and-app-registry` · nothing committed · the git root is `D:\practical`
(the repo also tracks unrelated sibling projects; only `ecommerce-platform/` is in scope).

Companion documents: [APPLICATION_FEATURE_MATRIX](APPLICATION_FEATURE_MATRIX.md) ·
[IMPLEMENTATION_GAP_ANALYSIS](IMPLEMENTATION_GAP_ANALYSIS.md) · [SECURITY_AUDIT](SECURITY_AUDIT.md) ·
[MULTI_TENANCY_AUDIT](MULTI_TENANCY_AUDIT.md) · [TEST_EXECUTION_REPORT](TEST_EXECUTION_REPORT.md) ·
[IMPLEMENTATION_PROGRESS](IMPLEMENTATION_PROGRESS.md) · [KNOWN_ISSUES](KNOWN_ISSUES.md).

## 1. Repository structure

| Path | Contents |
|---|---|
| `backend/` | Maven multi-module (19 modules): 15 business services, api-gateway, config-server, discovery-server, parent. `config-repo/` holds Spring Cloud Config YAML. `legacy/` holds old accounting/analytics/order migrations. One Dockerfile per service. |
| `frontend/` | npm workspaces: `shell-app` (host + many admin/customer pages), 14 Module-Federation MFEs, `shared-library` (auth sessions, app registry, API client). |
| `docker-compose.yml` | Full local stack: Postgres, Redis, Kafka/ZK, ELK, Prometheus/Grafana/Alertmanager, Zipkin, Vault, all services and MFEs. Ports bound to 127.0.0.1. |
| `kubernetes/` | Kustomize base, ingress, network policies, HPA, chaos, AWS integration; **service manifests for only 5 services + gateway + delivery frontend**. |
| `devops/` | helm, terraform, load-testing, prometheus/grafana, vault, runbooks. |
| `.github/workflows/` | ci-backend (9 modules), ci-frontend (7 apps, build only), cd-release, terraform. |
| `docs/` | ~150 documents incl. per-module VERIFICATION_REPORTs for pricing-promotions, storefront-studio, order-control-tower, returns-refunds. |

## 2. Backend service inventory

| Module | Main LOC | Controllers | Flyway files | Test classes | Own JWT check | tenant_id |
|---|---|---|---|---|---|---|
| user-service | 2090 | 5 | 12 (V1–V12) | 4 | yes | tenancy tables |
| product-service | 6111 | 7 | 8 | 10 | admin paths | V6–V8 |
| order-service | 7428 | 6 (+admin, rma pkgs) | 8 files (V1, V4–V10; V2/V3 were the accounting ledger, now in accounting-service; copies kept in `backend/legacy/order-migrations`) | 10 | admin paths | V8+ |
| inventory-service | 2801 | 4 | 4 | 3 | yes | yes |
| delivery-service | 2533 | 2 | 2 | 9 | yes | yes |
| accounting-service | 4326 | 4 | 12 | 2 | no | no |
| analytics-service | 1838 | 1 | 4 | 1 | no | no |
| qa-service | 1890 | 2 | 1 | 3 | no | no |
| stock-service | 1659 | 3 | 3 | 1 | no | no |
| error-stack-service | 1349 | 2 | 1 | 1 | no | no |
| cart-service | 1203 | 2 | 1 | 1 | no | no |
| loyalty-service | 1063 | 2 | 1 | 1 | no | no |
| procurement-service | 818 | 1 | 1 | 0 | no | no (sibling adding) |
| notification-service | 807 | 1 | 2 | 3 | yes | no |
| crm-service | 482 | 1 | 2 | 1 | no | no |
| api-gateway | 545 | 1 | – | 4 (+2 new) | yes | – |

There is **no Payment, Vendor or Support service**.

## 3. Frontend inventory

| App | src files / LOC | Tests | Notes |
|---|---|---|---|
| shell-app | 8 / 2465 | 0 | host, customer storefront pages, Quality Center page |
| mfe-order | 22 / 8132 | 20 node tests | control tower, returns |
| mfe-product | 18 / 3796 | 17 node tests | catalog, pricing, storefront studio |
| mfe-accounting | 5 / 3165 | 0 | |
| mfe-inventory | 10 / 1542 | 0 | sibling session extending |
| mfe-analytics | 3 / 1437 | 0 | |
| mfe-error-stack | 6 / 1426 | 0 | |
| mfe-cart | 5 / 1353 | 0 | |
| mfe-stock | 8 / 1157 | 0 | |
| mfe-user | 5 / 1132 | 0 | |
| mfe-loyalty | 5 / 627 | 0 | |
| mfe-crm | 3 / 331 | 0 | thin |
| mfe-procurement | 2 / 284 | 0 | single App.tsx |
| mfe-super-admin | 2 / 220 | 0 | tenants + grants |
| mfe-delivery | 2 / 216 | 0 | thin |
| shared-library | 11 / 873 | 39 node tests | |

All 15 apps type-check clean (`tsc --noEmit`, 0 errors). No component-render or browser tests exist anywhere.

## 4. Docs vs code

| Documented claim | Code reality |
|---|---|
| Ten independently deployable apps (shared-library registry, user-service V6) | Registry exists; Vendor/Restaurant portal, Support and Storefront-as-app have no frontend; CRM/Super Admin/Delivery UIs are thin. |
| `docs/handover/PROJECT_COMPLETION_REPORT.md` states "100% DELIVERED, TESTED & VERIFIED" | Payment is a callback contract only; 10 of 15 services are not tenant-aware; k8s covers 5 services. |
| Module VERIFICATION_REPORTs (pricing, storefront, control tower, returns) | Re-confirmed by re-running their suites (see TEST_EXECUTION_REPORT); their own "not verified live" caveats still hold. |
| Delivery hand-over note: "order-service has no tenant column" | Outdated — `orders.tenant_id` was added in order V8 (control tower). |
| CI "builds and tests the platform" | Backend CI covers 9 of 19 modules; frontend CI builds 7 of 16 packages and runs no tests. |

## 5. Critical defect list (verified by code reading and/or tests)

| ID | Severity | Defect | Status |
|---|---|---|---|
| D1 | Critical | Tenant tokens reach non-tenant services with an `ADMIN` role header (cross-tenant read/write) | Fixed (gateway policy) |
| D2 | High | Gateway public-endpoint substring match lets `/…/swagger`, `/…/api-docs`, `/…/actuator`, `/…/fallback` skip auth | Fixed |
| D3 | High | Public default service token used by the running stack | Compose now fail-closed; code defaults open |
| D4 | High | No Payment service: payments/refunds are confirmed only by a shared-token callback; nothing talks to a PSP | Open — Phase 5 |
| D5 | High | Checkout has no tenant context; all orders land in one tenant | Open — Phase 6 |
| D6 | Medium | cart-service tests (and runtime requests) block 60 s per Kafka send when the broker is down | Test fixed; runtime open |
| D7 | Medium | Product inventory analytics cross-tenant + `contains("ADMIN")` role check | Mitigated at gateway |
| D8 | Medium | CI misses 10 backend modules and all frontend tests | Open |
| D9 | Medium | Order uses legacy stock-service, not inventory reservations | Sibling session (Phase 4) |

## 6. Prioritised plan
See [IMPLEMENTATION_GAP_ANALYSIS](IMPLEMENTATION_GAP_ANALYSIS.md) §Plan. Phases 1–2 started this session (see
[IMPLEMENTATION_PROGRESS](IMPLEMENTATION_PROGRESS.md)).
