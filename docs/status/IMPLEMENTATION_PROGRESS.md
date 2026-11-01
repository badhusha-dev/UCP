# Implementation Progress

Continuously updated. Phase and item IDs refer to [COMPLETION_ROADMAP.md](COMPLETION_ROADMAP.md).

**Last verification:** 2026-11-01 (frontend builds, typecheck, lint, node tests, accounting module tests). Full backend run: `BUILD SUCCESS` (see the verification log).

## Completed

| Task | Evidence |
|---|---|
| Establish baseline from HEAD `881fe882` | `git status`, `git log`, module lists |
| Inventory the ten applications and shared capabilities | Route and controller counts; source greps. Recorded in PROJECT_CURRENT_STATUS.md. |
| Frontend builds for all 16 workspaces | `npm run build` exit 0 for each |
| Frontend typecheck and lint | `tsc --noEmit` and `npm run lint` per workspace; recorded in TEST_AND_VERIFICATION_REPORT.md |
| Frontend unit tests | shared-library 39, mfe-order 20, mfe-product 17, all pass |
| Flyway duplicate and gap check | Static scan of 18 services |
| Fix B-10: missing `Button` import in `mfe-user` | `tsc` TS2304 cleared |
| Fix B-11: `noWrap` placed in `sx` in `mfe-product` | `tsc` TS2769 cleared |
| Fix B-03: constant-time service-token comparison in accounting | Accounting module: 22 pass, 5 Postgres-gated skipped |
| Add regression test for B-03 | `testInternalAccountingRefusesWrongServiceToken` passes |
| Fix B-13: crm → loyalty port 8086 → 8087 | On disk in nested `backend/config-repo`; not committed |
| Write the seven `docs/status/` documents | This folder |
| Account lockout (catalog PLT-01, status F-03) | user-service V16, `LoginAttemptService`, both login endpoints; user-service suite 17 pass, 0 fail |
| Advanced feature catalog and expansion roadmap | `docs/product/ADVANCED_FEATURE_CATALOG.md`, `docs/product/FEATURE_EXPANSION_ROADMAP.md` |
| Lockout user and operator guide | `docs/security/ACCOUNT_LOCKOUT.md` |
| Remove committed credentials from tracked files (B-06 to B-09, B-35) | `git grep` finds none outside docs and tests; compose resolves with `.env` and refuses without; kustomize builds; error-stack 6/6 tests pass. Rotation of the old values is still the owner's task. |
| Tenant-scope inventory analytics and require `analytics:read` (B-04) | product-service: 79 tests, 0 failures (2 new) |
| Kubernetes secrets template (B-18) | Fresh-clone simulation: build fails without `02-secrets.yml`, passes with the template copied (34 Deployments) |
| Gateway sign-in rate limit (B-20, PLT-01 second half) | api-gateway: 41 tests, 0 failures (3 new in `LoginRateLimitTest`) |
| Shared transactional outbox, adopted by cart and loyalty (PLT-05 part, B-16, K5) | `outbox` 7, `cart-service` 8, `loyalty-service` 9 tests, all passing |
| Same-origin `/api` URLs in every frontend; dev proxies added to 11 MFEs (B-15, K12, S10) | 0 type errors; 16 builds, 4 lint and 76 node tests pass. Not exercised in a browser. |
| Frontend typecheck clean in all 16 workspaces, plus a CI typecheck gate (TD-01 to TD-03) | 0 type errors; 16 builds, 4 lint and 76 node tests pass |
| Tenant on order events and their outbox rows (B-27) | `order-service` 101 (2 producer tests), `notification-service` 11 (2 compatibility tests), all passing |
| Product specification v1.0 recorded and measured against the repository | `docs/product/PRODUCT_REQUIREMENTS.md` (verbatim), `APPLICATION_CATALOG.md`, `RELEASE_STRATEGY.md`, `docs/architecture/SERVICE_BOUNDARIES.md`, `docs/README.md`. Evidence from migrations, entities and controller paths; documentation only. |
| Storefront tenant resolution in the catalog and checkout (SF-01, B-25, K4, roadmap 1.9 and 2.13) | product-service: `StorefrontCatalogTest` 4 tests and the full suite 83 tests, 0 failures. order-service `StorefrontCheckoutTest` (existing). shared-library 48 tests (9 new); mfe-product 17, mfe-order 20; 16 typechecks, builds and lint clean. Not exercised in a browser. |
| Analytics dashboards show load failures (B-37) | `mfe-analytics` and `mfe-order`: 0 type errors, both builds pass, mfe-order lint and 20 tests pass. Not exercised in a browser. |
| Outbox adopted by order, inventory and stock; RAW payload mode (PLT-05, B-34) | `outbox` 10, `order-service` 100, `inventory-service` 29, `stock-service` 11 tests, all passing; cart 8 and loyalty 9 re-run |

## In progress

- None. The full backend test run finished (`BUILD SUCCESS`, see the verification log below) and its results are recorded in TEST_AND_VERIFICATION_REPORT.md and PROJECT_CURRENT_STATUS.md.
- **Code fixes committed** (B-03, B-10, B-11) after targeted tests passed. The full run is a regression check, not a gate for these commits.

## Pending

| Item | Roadmap | Notes |
|---|---|---|
| ~~Remove literal credentials from tracked files~~ | 0.1 | Done 2026-11-01. Rotation remains (see Blocked). |
| Analytics exact role and tenant scope | 0.3 | Source change; test first. |
| Tenant scoping, one slice per service (nine services) | 0.4 | Each slice is a new Flyway migration. |
| Account lockout and gateway rate limiting | 0.5 | — |
| Accounting posted-line immutability trigger | 0.7 | Needs scratch Postgres. |
| Outbox for all producers | 1.3 | — |
| Frontend typecheck clean | 1.1 | 7 workspaces fail; mostly unused locals. |
| Apply pending migrations on Postgres | 1.10 | Needs scratch Postgres. |
| Container runtime verification | 1.11 | Needs Docker daemon. |

## Blocked

| Item | Blocker | Owner action required |
|---|---|---|
| 0.1 credential removal and rotation | Need to know whether committed values are live | Confirm the PagerDuty key is a placeholder; rotate the Alertmanager bearer token, Grafana password and database password. |
| 0.6 asymmetric JWT | Key management approach not chosen | Decide on key storage (Vault or Kubernetes Secret). |
| 0.8 payment service | PSP provider not chosen | Choose a PSP. |
| 1.13 Kubernetes secrets | Gitignored `base/02-secrets.yml` referenced by kustomization | Decide on template-versus-generation approach. |
| 2.1 customer support | Scope not defined | Define tickets, roles and channels. |
| 2.4 Quality Center | Tenant-or-platform not decided | Decide. |
| TD-07 config repository | Nested repo has no remote and 27 uncommitted entries | Give it a remote or move it into the parent. |
| TD-08 frontend lockfile | Lockfile excluded from Docker context | Decide on a Linux-compatible lockfile. |
| crm tenant slice (F-02) | Its SQL is Postgres-only and untested; Testcontainers needs Docker, and no Docker daemon is available | Start Docker on the build machine, or provide a scratch Postgres. |

## Next recommended task

**0.1, the code side only:** remove the four literal credentials from tracked files (B-06 to B-09) and make compose and Kubernetes read them from required variables. The rotation itself needs the owner. This is the highest-risk open item that can be completed without a decision.

## Verification log

| Date | What was run | Result |
|---|---|---|
| 2026-11-01 | `npm run build` for 16 workspaces | 16 pass |
| 2026-11-01 | `tsc --noEmit` for 16 workspaces | 9 pass, 7 fail (before and after fixes; see report) |
| 2026-11-01 | `npm run lint` for 4 workspaces | 4 pass |
| 2026-11-01 | `npm test` shared-library, mfe-order, mfe-product | 39, 20, 17 pass |
| 2026-11-01 | `./mvnw -B -ntp -pl accounting-service test` | 27 run, 0 fail, 5 skipped |
| 2026-11-01 | `docker compose config` (temporary token) | Valid, 46 services |
| 2026-11-01 | `kubectl kustomize kubernetes` | Builds: 34 Deployments, 18 NetworkPolicies |
| 2026-11-01 | Full backend run | `BUILD SUCCESS`; 19 of 19 modules; 385 executed, 380 passed, 0 failed, 5 skipped |
| 2026-11-01 | `./mvnw -B -ntp -pl user-service test` after the lockout change | 17 executed, 0 failed (5 new lockout tests); V16 not run on Postgres |
