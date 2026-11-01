# Test and Verification Report

Date: 2026-11-01. Repository HEAD `881fe882` with uncommitted changes listed in [CHANGELOG_CURRENT.md](CHANGELOG_CURRENT.md).
Every result below comes from a command run in this audit. Nothing is carried over from earlier reports.

## Environment

| Item | Value | Limitation |
|---|---|---|
| OS | Windows 11, PowerShell 5.1 and Git Bash | — |
| Java | Temurin/Oracle 17.0.11 | — |
| Maven | Wrapper `backend/mvnw.cmd` (Maven 3.6.3) | — |
| Node | v24.5.0 | — |
| Docker | Client present; **daemon not running** | No image build, no compose `up`, no Testcontainers. |
| Kubernetes | `kubectl` present; no cluster | `kubectl kustomize` only. |
| Postgres | Not available | Postgres-gated tests skipped. |
| Python | Not installed | Workflow YAML parsed with the `yaml` package from `frontend/node_modules`. |

## Tests discovered

| Area | Count | Source |
|---|---|---|
| Backend test methods (static `@Test` count) | 393 across 18 modules | Grep of `src/test` |
| Frontend node test cases | 76 (shared-library 39, mfe-order 20, mfe-product 17) | `npm test` output |
| Frontend test files | 4 shared-library, 2 mfe-order, 2 mfe-product, 1 mfe-inventory | File listing |
| Frontend workspaces with no test file | 12 of 16 | File listing |

Backend static counts per module: user 13, product 77, order 99, accounting 27, inventory 27, delivery 37, api-gateway 38, notification 9, stock 9, cart 7, procurement 8, loyalty 8, analytics 8, error-stack 6, qa 16, crm 2, config-server 1, discovery-server 1.

## Build results

| Command | Result |
|---|---|
| `npm run build --workspace=<w>` for `shared-library` and 15 apps (16 total) | **16 of 16 pass** (exit 0) |
| Build of `mfe-cart` and `mfe-delivery` from a clean checkout | Required a local `node_modules/@ecommerce/shared-library` link, because `package-lock.json` has no entry for the workspace link. Local only; not committed. |

## Typecheck (`tsc --noEmit -p <tsconfig>`)

| Workspace | Before this audit | After this audit | Notes |
|---|---|---|---|
| shell-app | 19 | 19 | Unused imports; TS1484 in shared-library sources |
| mfe-order | 35 | 35 | Unused imports and locals |
| mfe-user | 21 | **11** | Missing `Button` import fixed (TS2304) |
| mfe-product | 8 | **7** | `noWrap` prop fixed (TS2769) |
| mfe-accounting | 12 | 12 | Unused imports |
| mfe-analytics | 11 | 11 | Unused imports |
| mfe-cart | 4 | 4 | TS1484 in shared-library sources |
| shared-library, mfe-inventory, mfe-stock, mfe-loyalty, mfe-error-stack, mfe-crm, mfe-procurement, mfe-super-admin, mfe-delivery | — | Pass (no errors) | — |

No TS2304, TS2769, TS2322, TS2339 or TS2551 errors remain in any workspace.

## Lint (`npm run lint`)

| Workspace | Result |
|---|---|
| shell-app, mfe-order, mfe-product, mfe-user | Pass (exit 0) |
| Others | No lint script defined |

## Frontend unit tests (`npm test --workspace=<w>`)

| Workspace | Tests | Pass | Fail |
|---|---|---|---|
| shared-library | 39 | 39 | 0 |
| mfe-order | 20 | 20 | 0 |
| mfe-product | 17 | 17 | 0 |

Output includes a logged `Failed to decode JWT token` line from a test that feeds an invalid token on purpose; it is expected.

## Backend tests

### Targeted run after the B-03 change

`./mvnw -B -ntp -pl accounting-service test`

| Class | Run | Failures | Errors | Skipped |
|---|---|---|---|---|
| AccountingSecurityAndControllerTest | 10 | 0 | 0 | 0 |
| AccountingServiceTest | 12 | 0 | 0 | 0 |
| AccountingTenantIsolationPostgresTest | 5 | 0 | 0 | 5 (requires `UCP_TEST_ACCOUNTING_POSTGRES_URL`) |
| **Module total** | **27** | **0** | **0** | **5** |

Includes `testInternalAccountingRefusesWrongServiceToken`, added in this audit.

### Full run

`./mvnw -B -ntp -fae test` from `backend/`, log in the scratchpad (`backend-full-test.log`).

_Pending at the time of writing. This section is updated when the run completes._

### Earlier targeted run (before this audit)

`./mvnw clean test -pl accounting-service,order-service,inventory-service,procurement-service,stock-service -am`, run before commit `e7fe0017`:

| Module | Run | Failures | Errors | Skipped |
|---|---|---|---|---|
| Inventory Service | 27 | 0 | 0 | 0 |
| Stock Service | 9 | 0 | 0 | 0 |
| accounting-service | 26 | 0 | 0 | 5 |
| Supplier & Procurement Service | 8 | 0 | 0 | 0 |
| order-service | 99 | 0 | 0 | 0 |

## Database migration results

Static audit only; no Postgres available.

- No duplicate Flyway version numbers in any of the 18 services.
- Version numbers are non-contiguous in several services (for example accounting-service has no V2, V3, V6–V9). Flyway accepts gaps. Whether any applied migration was removed cannot be determined without the live `flyway_schema_history`.
- Delivery V1–V2 and inventory V4 have never been applied to Postgres (K11). The H2 tests apply migrations; Postgres behaviour is not verified.
- Migration files were not edited in this audit.

## Infrastructure validation

| Check | Command | Result |
|---|---|---|
| Compose syntax and variable resolution | `docker compose config --quiet` with a temporary `SERVICE_TO_SERVICE_TOKEN` | Pass. 46 services. |
| Compose without the token | `docker compose config` with no token set | Fails as designed (K16). |
| Kubernetes manifests | `kubectl kustomize kubernetes` | Pass. 34 Deployments, 18 NetworkPolicies. Requires the gitignored `base/02-secrets.yml` (B-18). |
| GitHub workflows | Parsed with `yaml` | Pass. Backend matrix 18 services, frontend matrix 15 apps. |
| Dockerfile references | Every backend matrix Dockerfile exists | Pass (18 of 18). |

## API, authentication and tenant-isolation tests

| Area | Evidence | Status |
|---|---|---|
| Gateway tenant boundary | `api-gateway` `GatewayTenantBoundaryTest` (6 `@Test` methods) | Included in the full run; result pending. |
| Accounting internal endpoints reject wrong or missing token | `testInternalAccountingRefusesWrongServiceToken` | Passed (targeted run). |
| Accounting customer and admin access rules | `AccountingSecurityAndControllerTest` | Passed (targeted run). |
| Accounting cross-tenant isolation on Postgres | `AccountingTenantIsolationPostgresTest` | **Not executed.** Skipped unless `UCP_TEST_ACCOUNTING_POSTGRES_URL` points at a scratch database. |
| Cross-tenant tests for the nine non-tenant services | None exist | **Missing.** |

## Kafka event tests

Two notification-service test files reference Kafka. No other module tests event publication or consumption. Cart's Kafka test was made hermetic before this session (gap doc). No end-to-end event test exists.

Status: **NOT VERIFIED** for producers and consumers across services.

## End-to-end workflows

`scripts/smoke-test.sh` and `scripts/smoke-test.ps1` exist. They require the full compose stack, which could not be started. **Not executed.**

No cross-service workflow test exists in the repository. The business workflows in the audit brief (checkout to payment, return to refund, purchase order to goods receipt, and similar) are **not verified end to end**.

## Runtime verification

Not performed. No container was started. No browser session was run against a live stack.

## Summary of executed results

| Category | Executed | Passed | Failed | Skipped or not run |
|---|---|---|---|---|
| Frontend builds | 16 | 16 | 0 | — |
| Frontend typecheck | 16 | 9 | 7 | — (failures are unused-import and type-import issues; see table) |
| Frontend lint | 4 | 4 | 0 | 12 have no lint script |
| Frontend unit tests | 76 | 76 | 0 | 12 workspaces have no tests |
| Backend targeted tests | 27 | 22 | 0 | 5 skipped (Postgres) |
| Backend full run | pending | pending | pending | — |
| Compose and Kubernetes validation | 4 | 4 | 0 | — |
| Container, cluster and E2E tests | 0 | 0 | 0 | All not run (environment) |
