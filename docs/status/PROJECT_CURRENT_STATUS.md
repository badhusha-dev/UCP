# Project Current Status

Audit date: 2026-11-01. Baseline: HEAD `881fe882` plus the uncommitted changes in [CHANGELOG_CURRENT.md](CHANGELOG_CURRENT.md).
This document replaces the status claims in older reports where they disagree. Each statement below cites code, a command, or a test run from this audit.

**Overall: not MVP-ready and not production-ready.** The platform has real, tested functionality in several areas, but three P0 gaps remain open: no payment service, nine services without tenant isolation, and committed credentials. No completion percentage is given (see the final section).

## Status definitions

- **COMPLETED**: implemented and verified by tests or commands that passed in this audit.
- **PARTIALLY COMPLETED**: some implementation exists; functionality is incomplete.
- **NOT IMPLEMENTED**: no meaningful implementation exists.
- **IMPLEMENTED BUT BROKEN**: code exists, but verification found a defect.
- **NOT VERIFIED**: implementation exists, but it could not be tested here.
- **BLOCKED**: depends on an unresolved decision or on an environment that is not available.

## Architecture (verified in source)

| Layer | What exists | Evidence |
|---|---|---|
| Backend | 18 Spring Boot 3.2.4 modules: api-gateway, discovery-server, config-server, user, product, order, cart, inventory, stock, procurement, accounting, analytics, crm, delivery, loyalty, notification, error-stack, qa | `backend/pom.xml` `<module>` list |
| Config | `backend/config-repo` is a nested git repository, referenced as a gitlink | `git ls-tree HEAD backend/config-repo` |
| Frontend | Shell app, 14 Module Federation remotes, shared library | `frontend/package.json` workspaces |
| Data | PostgreSQL per service; Flyway migrations (18 services, 0 duplicate versions) | Migration audit |
| Messaging | Kafka; producers in 7 services; consumers in 6 services; outbox only in delivery-service | Source grep |
| Cache | Redis declared in product-service only, unused | Source grep |
| Deployment | Docker Compose (46 services); Kubernetes manifests for 18 backend services and the frontend; Helm chart; Terraform | `docker compose config --services`; `kubectl kustomize` |
| CI | Four GitHub workflows (backend, frontend, CD, Terraform) | `.github/workflows/` |

## The ten applications

| # | Application | Status | Frontend | Backend | Principal gap | Evidence |
|---|---|---|---|---|---|---|
| 1 | Super Admin | PARTIALLY COMPLETED | `mfe-super-admin` (16 KB App, no tests) | user-service `PlatformTenantController`; grant and revoke audited | Tenant lifecycle, plans and limits | A-01 |
| 2 | E-commerce Admin | PARTIALLY COMPLETED | `mfe-order`, `mfe-product`, `mfe-user` | order-service admin API (121 mappings), product-service (121) | Customer, staff and report screens; tenant consoles empty until storefront resolution | A-02, K21 |
| 3 | Storefront | PARTIALLY COMPLETED | `shell-app`, storefront in `mfe-product`, `mfe-cart`, checkout in `mfe-order` | product-service storefront, order-service checkout | Tenant resolution; promo codes not validated server-side | A-03, B-14, B-25 |
| 4 | Inventory Management | PARTIALLY COMPLETED | `mfe-inventory`, `mfe-stock` | inventory-service, stock-service | Atomic reservations; stock not tenant-scoped; migration V4 never applied | A-04, B-28 |
| 5 | Accounting & Finance | PARTIALLY COMPLETED | `mfe-accounting` (no login route) | accounting-service: 55 mappings, 27 tests (5 Postgres tests not run) | Immutability trigger; Accounting grants; payment postings | A-05, B-26, B-29 |
| 6 | Delivery Management | PARTIALLY COMPLETED | `mfe-delivery` (single 45 KB App, no tests) | delivery-service: 45 mappings, 37 tests, outbox | Delivery not created on fulfilment; migrations V1–V2 not applied | A-06 |
| 7 | Vendor / Restaurant | PARTIALLY COMPLETED | `mfe-procurement` (single App, no tests) | order-service restaurant states; procurement-service supplier flows | Vendor domain and portal | A-07 |
| 8 | CRM | PARTIALLY COMPLETED | `mfe-crm` (38 KB App and `CrmRecordManager`, no tests) | crm-service: 46 mappings, 2 tests | Tenant scope; test coverage | A-08 |
| 9 | Tester / Quality Center | PARTIALLY COMPLETED | `mfe-error-stack` (no tests); Quality Center in `shell-app` | qa-service: 17 mappings, 16 tests; runner needs repo mount | Tenant-or-platform decision (BLOCKED) | A-09 |
| 10 | Customer Support | NOT IMPLEMENTED | None | None | No module, entity or screen | A-10 |

## Backend and frontend implementation status

Backend module results from the full run are in [TEST_AND_VERIFICATION_REPORT.md](TEST_AND_VERIFICATION_REPORT.md#backend-tests).

| Area | Status | Notes |
|---|---|---|
| Backend compilation and tests | COMPLETED | Full run: `BUILD SUCCESS`, 19 of 19 modules, 385 executed, 380 passed, 0 failed, 5 skipped (Postgres-gated). |
| Frontend production builds | COMPLETED | 16 of 16 workspaces. |
| Frontend typecheck | PARTIALLY COMPLETED | 9 of 16 pass. 7 fail, mostly unused locals. |
| Frontend lint | PARTIALLY COMPLETED | 4 workspaces define a lint script; all four pass. |
| Frontend unit tests | PARTIALLY COMPLETED | 76 tests pass in three workspaces; 12 workspaces have none. |
| Container images | NOT VERIFIED | No Docker daemon. |
| Kubernetes | NOT VERIFIED | Builds offline; never applied. |

## Shared platform status

### Identity and access management

| Capability | Status | Evidence |
|---|---|---|
| Centralized authentication | PARTIALLY COMPLETED | user-service issues tokens; gateway validates them. |
| JWT validation | PARTIALLY COMPLETED | Signature and expiry only; one HS256 secret shared by seven services (B-05). |
| Refresh tokens | NOT IMPLEMENTED | No refresh endpoint or table. |
| User management | PARTIALLY COMPLETED | user-service: 5 controllers, 36 mappings. |
| Tenant management and membership | PARTIALLY COMPLETED | `PlatformTenantController`, `TenantAccessService`. |
| Application registry and entitlements | PARTIALLY COMPLETED | Grants and revocation exist; the TypeScript registry still lists Accounting as PLANNED (K17). |
| User-to-application grants | PARTIALLY COMPLETED | Grant and revoke are audited (`APPLICATION_GRANT_REVOKE`). |
| Role and permission management | PARTIALLY COMPLETED | Role editor in `mfe-user`; hierarchy checks in the UI. Server-side enforcement not checked here. |
| Tenant-aware authorization | PARTIALLY COMPLETED | Gateway tenant allow-list (6 gateway boundary tests). Nine services not tenant-scoped (B-02). |
| Session management and logout or revocation | PARTIALLY COMPLETED | Grant revocation only; logout does not revoke JWTs (B-21). |
| Password reset | NOT IMPLEMENTED | No reset flow. |
| Account lockout and throttling | NOT IMPLEMENTED | No lockout logic; rate limiting only in error-stack-service (B-20). |
| MFA | NOT IMPLEMENTED | No TOTP code. |

### Multi-tenancy

| Capability | Status | Evidence |
|---|---|---|
| Tenant creation and lifecycle | PARTIALLY COMPLETED | Creation exists; lifecycle states do not (A-01). |
| Tenant context propagation | PARTIALLY COMPLETED | Gateway injects tenant headers; services that use them are the tenant-aware subset. |
| Tenant isolation in every query | PARTIALLY COMPLETED | Tenant-aware services filter; nine services do not (B-02). |
| Tenant-aware unique constraints | NOT VERIFIED | Not checked per table in this audit. |
| Tenant-aware cache keys | NOT IMPLEMENTED | No cache exists (F-09). |
| Tenant-aware Kafka events | NOT IMPLEMENTED | `order-events` carry no tenant (B-27). |
| Tenant-aware files, reports and exports | NOT VERIFIED | Return-evidence directory is configured; tenant partitioning not checked. |
| Cross-tenant access prevention | PARTIALLY COMPLETED | Accounting and gateway tests exist. Postgres accounting test not run. |
| Migration of legacy data | PARTIALLY COMPLETED | Legacy-tenant migrations exist (K19, K21). Not re-verified against a live database. |

### Shared infrastructure

| Capability | Status | Evidence |
|---|---|---|
| API Gateway routing and security | PARTIALLY COMPLETED | Routes to all 15 business services; actuator over-exposed (B-19). |
| Service-to-service authentication | PARTIALLY COMPLETED | Shared token; constant-time comparison in nine classes; accounting fixed in this audit (B-03). |
| Kafka event architecture | PARTIALLY COMPLETED | Producers and consumers exist; synchronous publishing (B-16). |
| Transactional outbox | PARTIALLY COMPLETED | delivery-service only (F-10). |
| Redis caching and locking | NOT IMPLEMENTED | Dependency unused (F-09). |
| Centralized exception handling | PARTIALLY COMPLETED | `@RestControllerAdvice` in delivery, user, qa, order and accounting. Other services not checked. |
| Audit logging | PARTIALLY COMPLETED | Audit tables and writes in accounting and tenant grants. Coverage elsewhere not checked. |
| Notifications | PARTIALLY COMPLETED | notification-service consumes events and has DLQ handling. No reset or verification flows. |
| API documentation | NOT VERIFIED | `@Operation` and springdoc annotations present in 20 files; served output not checked. |
| Monitoring and tracing | NOT VERIFIED | Prometheus and Zipkin configuration present; no running stack. |

## Completed functionality (verified)

- Frontend production builds for all 16 workspaces (`npm run build`).
- Frontend unit tests: shared-library (39), mfe-order (20), mfe-product (17).
- Accounting: customer and admin access rules, internal endpoint token refusal, 404 and 400 error mapping (targeted run, 22 passing).
- Stock-service internal endpoints reject an invalid service token (9 tests).
- Inventory and procurement integration tests (27 and 8 tests in the earlier run).
- Order-service: 99 tests passed in the earlier run.
- Full backend test run: `BUILD SUCCESS`, 380 passed, 0 failed, 5 skipped (Postgres-gated).
- Compose and Kubernetes manifests validate offline.
- Flyway version numbers have no duplicates.

## Partially completed functionality

See the application table and [INCOMPLETE_FEATURES.md](INCOMPLETE_FEATURES.md). The largest partial items are tenant isolation (B-02), the outbox (F-10), checkout tenant resolution (B-25), and the Quality Center runner (A-09).

## Missing functionality

Payment service (F-01), refresh tokens (F-05), password reset (F-06), MFA (F-07), account lockout (F-03), Redis (F-09), Customer Support (A-10), vendor domain and portal (A-07), Super Admin plans and limits (A-01).

## Broken functionality (verified defects)

| Item | Defect | State |
|---|---|---|
| B-04 / O-01 Analytics | Cross-tenant aggregate; `role.contains("ADMIN")` | OPEN |
| B-19 / F-17 Gateway actuator | Non-prod profile exposes `refresh` and `flyway` | OPEN |
| B-10 mfe-user | Missing `Button` import; throws on render | **FIXED** (uncommitted) |
| B-11 mfe-product | `noWrap` in `sx` | **FIXED** (uncommitted) |
| B-12 accounting | 500 for unknown IDs | **FIXED** (`e7fe0017`) |
| B-13 crm → loyalty | Wrong port 8086 | **FIXED** on disk in nested config-repo |
| B-03 accounting | Non-constant-time service-token comparison | **FIXED** (uncommitted, tested) |

## Unverified functionality

Container runtime; Kubernetes apply; the Postgres tenant-isolation test; Flyway against a live Postgres; Kafka producers and consumers end to end; browser flows in all ten applications; the smoke tests; Istio mesh behaviour.

## Current technical risks

1. **Committed credentials** (B-06 to B-09): tracked files include an Alertmanager bearer token, a PagerDuty routing key, a Grafana admin password and a database password. Rotation status unknown.
2. **Cross-tenant data exposure** (B-02, B-04): nine services and analytics return data across tenants to platform tokens.
3. **Shared signing secret** (B-05): compromise of any one of seven services allows forging any token.
4. **No payment service** (B-01): payment confirmation relies on a shared token.
5. **No brute-force protection** (B-20): login is open to password guessing.
6. **Untested frontends**: 12 of 16 workspaces have no tests, and typecheck fails in 7 (TD-01, TD-04).
7. **Non-reproducible images** (TD-08) and a build gate that does not typecheck (TD-03).
8. **Nested config repository** (TD-07): configuration changes are not versioned in the parent repository.

## Closest to completion

- **Accounting & Finance**: tenant-scoped, the most tested backend module, and the targeted run is green. Remaining: immutability trigger, grants, Postgres test, and payment postings.
- **E-commerce Admin**: admin APIs and screens exist; K21 reports a live tenant login verified 12 of 12, but this audit did not re-run that check.

## Completion assessment

No percentage is given. Feature counts by status across the register in [INCOMPLETE_FEATURES.md](INCOMPLETE_FEATURES.md) would be misleading, because features carry different weights and most have no verification evidence. Verifiable statements instead:

- Ten applications: 0 COMPLETED, 9 PARTIALLY COMPLETED, 1 NOT IMPLEMENTED.
- MVP blockers: P0 items B-01, B-02, B-03 (fixed), B-04, B-05, B-06 to B-09, F-03, F-04, and the payment decision.
