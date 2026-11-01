# Repository Baseline

Phase 0 of the completion plan. Recorded 2026-11-01 on branch `feature/ucp-completion-modernization`, cut from `main` at `faeea04a`.
Every number below was produced by a command or a search in this pass. Where an older document says something different, this file is the one measured now, and the older document is listed under [Stale documents](#stale-documents).

## Checkpoint and branches

| Item | Value |
|---|---|
| Checkpoint branch | `backup/pre-modernization-faeea04a` at `faeea04a` (local, not pushed) |
| Working branch | `feature/ucp-completion-modernization` (local, not pushed) |
| `main` | 15 commits ahead of `origin/main`. Not pushed: no push has been approved since the list was given. |
| Remote | `origin` = `git@github.com:badhusha-dev/UCP.git` (SSH). Push not attempted. |
| Stash | empty |
| Untracked | `info/`, which is a separate git repository with its own `.git`. It is not part of UCP's history and was left unchanged. |
| Local `backup-pre-scrub` branch | Named in earlier notes; not present in `git branch` in this pass. Its absence was not investigated further. |

Tracked files: 1,490 (about 12.4 MB). Ignore rules: 27 lines in `.gitignore`.

## Environment on this machine

| Tool | Version found | Notes |
|---|---|---|
| Java | 17.0.11 (`JAVA_HOME` = `C:\Program Files\Java\jdk-17`) | Root POM sets `java.version` 17 |
| Maven | not on PATH; the wrapper pins Apache Maven 3.6.3 (`backend/mvnw.cmd`) | Use `backend\mvnw.cmd` |
| Node.js | 24.5.0, npm 11.5.1 | `frontend/Dockerfile.mfe` builds on `node:20-alpine`. No `engines` field in `package.json`. Local and container versions differ. |
| Docker | Engine 29.9.2, Compose v5.6.1, **daemon running** | Earlier documents say no daemon was available. That is no longer true. |
| Scratch Postgres | Container `ucp-test-pg`, `postgres:15-alpine`, PostgreSQL 15.18, up about 3 hours, bound to `127.0.0.1:55432` | Contains databases `ucp_user_test`, `ucp_inv_test`, `ucp_delivery_test`. Not created by this work; not written to in this phase. |
| Local images | At least 15 `ecommerce-platform-*:latest` images, including backend services and frontend apps | Built earlier. Their build date and source commit were not checked. |
| Containers | One container exists, `ucp-test-pg`. No compose stack is running, so the live services and the live databases on `localhost:15432` described in [IMPLEMENTATION_PROGRESS.md](../completion/IMPLEMENTATION_PROGRESS.md) are not up now. | `docker info` reports 1 container, running. |

Earlier status documents record "no Docker daemon", which blocked the tenant slices, the Postgres-only migration checks and the container checks. That blocker is out of date. The status documents need updating once Phase 1 has run the tests against the scratch database.

## Repository structure

| Area | Contents | Count |
|---|---|---|
| `backend/` | Maven multi-module build (root `pom.xml`) | 19 modules: `outbox`, `user-service`, `product-service`, `inventory-service`, `stock-service`, `cart-service`, `loyalty-service`, `accounting-service`, `crm-service`, `procurement-service`, `analytics-service`, `order-service`, `notification-service`, `api-gateway`, `discovery-server`, `config-server`, `error-stack-service`, `qa-service`, `delivery-service` |
| `backend/config-repo/` | Nested git repository of service configuration. It has no remote and uncommitted changes. | See TD-07 |
| `backend/legacy/` | Archived material in three folders (`accounting`, `analytics`, `order-migrations`), 43 tracked files | Not referenced by the POM, Compose or CI workflows. Candidate for cleanup. |
| `frontend/` | npm workspaces | 16: `shared-library`, `shell-app`, `mfe-product`, `mfe-order`, `mfe-user`, `mfe-inventory`, `mfe-stock`, `mfe-cart`, `mfe-loyalty`, `mfe-accounting`, `mfe-analytics`, `mfe-error-stack`, `mfe-crm`, `mfe-procurement`, `mfe-super-admin`, `mfe-delivery` |
| `kubernetes/` | Kustomize tree | `kubectl kustomize kubernetes` builds 34 Deployments and 18 NetworkPolicies |
| `docker-compose.yml` | Stack definition | `docker compose config --services` returns 46 services (run with placeholder secrets, which are not recorded) |
| `.github/workflows/` | `cd-release.yml`, `ci-backend.yml`, `ci-frontend.yml`, `terraform-infra.yml` | 4 |
| `docs/` | Documentation | Layout described in [../README.md](../README.md) |
| `scripts/` | Helper scripts | Not audited in this pass |

## Backend inventory

Flyway migration files per module (`src/main/resources/db/migration`):

| Module | Migrations | Module | Migrations |
|---|---|---|---|
| user-service | 16 | product-service | 8 |
| accounting-service | 13 | order-service | 9 |
| inventory-service | 5 | analytics-service | 4 |
| stock-service | 4 | cart-service | 2 |
| loyalty-service | 2 | crm-service | 2 |
| delivery-service | 2 | notification-service | 2 |
| procurement-service | 2 | error-stack-service | 1 |
| qa-service | 1 | outbox (library) | none |

Controller base paths in use: `/api/accounting`, `/api/accounting/admin`, `/api/orders`, `/api/orders/admin`, `/api/orders/admin/analytics`, `/api/orders/admin/rma`, `/api/products`, `/api/products/admin`, `/api/products/admin/analytics`, `/api/products/admin/pricing`, `/api/products/admin/storefront`, `/api/products/public/storefront`, `/api/cart`, `/api/cart/admin`, `/api/crm`, `/api/delivery`, `/api/errors`, `/api/inventory`, `/api/inventory/admin`, `/api/inventory/ops`, `/api/loyalty`, `/api/loyalty/admin`, `/api/notifications`, `/api/procurement`, `/api/procurement/ucp`, `/api/stock`, `/api/stock/admin`, `/api/admin/qa`, `/api/users`, `/api/access`, `/api/platform/tenants`, `/api/users/internal/authorization`; internal paths under `/internal/...` for accounting, inventory, order, pricing, stock and storefronts.

## Frontend inventory

Every workspace builds with `npm run build`, and all 16 typecheck with `tsc --noEmit` (CI runs this gate). Lint is defined in four workspaces: `mfe-order`, `mfe-product`, `mfe-user` and `shell-app`. Unit tests are defined in three: `shared-library` (48 now), `mfe-product` (17), `mfe-order` (20). The other 13 workspaces have no tests.

`AdminAnalyticsDashboard.tsx` exists in two identical copies (`mfe-analytics` and `mfe-order`). Hashes match. This is a duplicate-component candidate.

## Test inventory

| Measure | Result |
|---|---|
| `@Test` annotations in backend test sources | 427, in 68 files |
| TODO and FIXME markers in source (excluding docs, `info/`, build output) | 0 |
| Postgres-gated tests | `AccountingTenantIsolationPostgresTest` runs only when `UCP_TEST_ACCOUNTING_POSTGRES_URL` matches `jdbc:postgresql://.+/ucp_scratch_*`. `TenantAccessPostgresIntegrationTest` (user-service) and `DeliveryTenantIsolationPostgresIntegrationTest` need `UCP_TEST_POSTGRES_URL` or a Docker host, and are skipped otherwise. `OpsIntegrationTest` and `InventoryServiceIntegrationTest` mention Postgres; their gating was not checked. The scratch container's databases (`ucp_user_test`, `ucp_inv_test`, `ucp_delivery_test`) do not match the accounting pattern, so a `ucp_scratch_*` database is needed for that test. |
| Last full backend run (recorded in `IMPLEMENTATION_PROGRESS.md`) | `BUILD SUCCESS`, 385 executed, 380 passed, 0 failed, 5 skipped (the skipped tests are the Postgres-gated ones) |

Results from this session, all after the commits they cover:

| Suite | Result |
|---|---|
| product-service (`mvnw -pl product-service test`) | 83 run, 0 failed, `BUILD SUCCESS` |
| shared-library | 48 pass, 0 fail |
| mfe-product | 17 pass, 0 fail |
| mfe-order | 20 pass, 0 fail |
| TypeScript, 16 workspaces | 0 errors |
| `npm run build` for shared-library, mfe-product, mfe-order | exit 0 |
| `npm run lint` for mfe-order and mfe-product | exit 0 |

The full backend suite has not been run since the outbox adoption, order tenant and storefront commits. Their targeted runs are recorded per commit in `IMPLEMENTATION_PROGRESS.md`.

## Build status

- Backend: the last full build passed. Not rebuilt in this pass.
- Frontend: all 16 workspaces typecheck (this pass). Builds were run for the three workspaces changed by SF-01, and they passed. The full 16-workspace build last ran before SF-01 and was not rerun.
- Containers: the images exist locally. Not rebuilt or started in this pass.
- Kubernetes: `kubectl kustomize` builds. Never applied to a cluster.

## Known defects

The authoritative list is [../status/BUGS_AND_TECHNICAL_DEBT.md](../status/BUGS_AND_TECHNICAL_DEBT.md). Open items that matter most for this plan:

- B-01: no payment service; payment and refund confirmations arrive only through service-token callbacks.
- B-02: nine services are not tenant-scoped. Tenant tokens are refused at the gateway; platform tokens see all rows.
- B-05 and K10: one HS256 secret shared by seven services; issuer and audience are not checked. Needs a key-storage decision.
- B-06 to B-09: credentials removed from tracked files, but still in history and must be rotated by the owner.
- B-19 and K8: gateway actuator exposes `refresh` and `flyway` in the non-production profile. The config is in the nested config-repo.
- B-21: logout does not revoke JWTs.
- B-29: no database-level immutability for posted accounting lines.
- B-36: crm `StaffAccessFilter` refuses SUPER_ADMIN.
- K11: delivery V1 and V2 and inventory V4 never ran on Postgres.
- A-04: stock reservation is not atomic with order creation.
- A-06: delivery is not created on fulfilment.

## Security risks

Summarised from [../completion/SECURITY_AUDIT.md](../completion/SECURITY_AUDIT.md), updated for this pass:

| Risk | Status |
|---|---|
| Open-endpoint substring bypass (S1) | Fixed, with a regression test |
| Tenant tokens reaching platform services (S2) | Fixed at the gateway (`TenantRoutePolicy`); services still not tenant-scoped (B-02) |
| Well-known service token (S3) | Fixed; the default is removed and the service fails at startup without it |
| Non-constant-time token comparison (S4) | Fixed in stock, accounting and storefront lookup |
| Analytics aggregates all tenants (S5) | Fixed (tenant-scoped, requires `analytics:read`) |
| Actuator over-exposure (S6) | Open |
| Unverified JWT claims (S7) | Open (B-05) |
| Committed credentials (S8) | Removed from tracked files; rotation open |
| crm and procurement host ports (S9) | Fixed |
| Hard-coded localhost API bases (S10) | Fixed |
| No MFA, refresh rotation or lockout (S11) | Lockout and per-address sign-in limit added; MFA and refresh still open |
| Kubernetes network policies (S12) | Present for all 18 services; per K25, ten services added in v2.0.0 still lack an Istio `AuthorizationPolicy` (not re-checked in this pass) |

## Technical debt

| Item | Evidence |
|---|---|
| `backend/legacy/` (43 files) unused by the build | Searched POM, Compose and CI: no references |
| Duplicate analytics dashboard in two workspaces | Identical SHA-256 hashes |
| Two procurement schemas in one service (`suppliers` and `proc_supplier`) | `procurement-service` migrations |
| Duplicate tables in several services (`stock_reservation`, `back_in_stock_alert`, `stock_movement`, `customer_loyalty`, `loyalty_transaction`, order tables in accounting and analytics) | Migrations; see [../architecture/SERVICE_BOUNDARIES.md](../architecture/SERVICE_BOUNDARIES.md) |
| Nested config repository with no remote | `backend/config-repo/` (TD-07) |
| Frontend lockfile excluded from Docker context | TD-08 |
| Node version differs between local (24.5) and the image (20) | `Dockerfile.mfe`; no `engines` field |
| `docs/quality/REQUEST_10_PROMPT.txt` stored inside documentation | Stray prompt file |
| Documentation sets that disagree (`docs/completion/`, `docs/quality/`, `docs/handover/`) | Several files claim completion the code does not show |

## Duplicate or obsolete candidates

Not removed in this phase. Each needs its own reference check before deletion (Phase 1, [../assessment/CODE_CLEANUP_REPORT.md](CODE_CLEANUP_REPORT.md)):

- `backend/legacy/`: candidate to archive.
- `frontend/mfe-order/src/pages/AdminAnalyticsDashboard.tsx`: duplicate of `mfe-analytics`.
- `docs/quality/SECURITY_AUDIT.md` and `docs/completion/SECURITY_AUDIT.md`: overlapping.
- `docs/technical/SYSTEM_ARCHITECTURE.md` and `docs/architecture/SYSTEM_ARCHITECTURE.md`: overlapping.
- `docs/quality/REQUEST_10_PROMPT.txt`: not documentation.

## Stale documents

These describe an earlier state and should be rewritten or marked superseded in Phase 9:

- Anything that says "no Docker daemon" or "Docker unavailable". The daemon was running in this pass.
- `docs/completion/FINAL_COMPLETION_REPORT.md`, `docs/handover/PROJECT_COMPLETION_REPORT.md` and similar: earlier completion claims.
- `docs/architecture/IMPLEMENTATION_ROADMAP.md` and `docs/completion/IMPLEMENTATION_PROGRESS.md`: superseded by `docs/status/`.

## Gaps against the plan

Not started: the complete documentation tree in the plan's section 12 (business, modules, implementation, roadmap and operations folders), the CODE_CLEANUP_REPORT, SECURITY_ASSESSMENT, TEST_RESULTS, OPEN_ISSUES, the `docs/completion` set, the root README rewrite, GitHub push, and local deployment verification. Phases 1 to 9 are unstarted.
