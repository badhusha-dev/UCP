# Current Changelog

Baseline: `origin/main` at `c57de2be` before this session, and `881fe882` after the push on 2026-11-01. Earlier history (`ffee4dae`, `d76747e5`) was preserved; the secret scrub rewrote only the two unpushed commits (see TD-11 in [BUGS_AND_TECHNICAL_DEBT.md](BUGS_AND_TECHNICAL_DEBT.md)).

## Latest change: account lockout and feature planning

| File | Change | Verification |
|---|---|---|
| `backend/user-service/src/main/resources/db/migration/V16__account_lockout.sql` | New: `failed_login_attempts`, `locked_until` on `users` (additive) | Not run on Postgres (tests use H2 with Flyway off) |
| `backend/user-service/.../model/User.java` | Two lockout fields, hidden from JSON | Suite passes |
| `backend/user-service/.../repository/UserRepository.java` | Three single-statement lockout updates | Suite passes |
| `backend/user-service/.../service/LoginAttemptService.java` | New: lock after N failures for a period; reset on success | Suite passes |
| `backend/user-service/.../controller/AuthController.java` | Both login endpoints check the lock first, record failures and successes, and return 423 while locked; OpenAPI lists 401 and 423 | Suite passes |
| `backend/user-service/src/test/.../AuthAndUserIntegrationTest.java` | Five lockout tests | 17 executed, 0 failed |
| `docs/product/ADVANCED_FEATURE_CATALOG.md`, `docs/product/FEATURE_EXPANSION_ROADMAP.md`, `docs/security/ACCOUNT_LOCKOUT.md` | New documents | — |

API behaviour change: a locked account receives `423 Locked` from `/api/users/login` and `/api/users/login/application`. Responses for unlocked accounts are unchanged.

## Commits on `main` since the baseline

| Commit | Files | Summary | Verification |
|---|---|---|---|
| `94eef1d4` | 30,717 deleted from index, 0 content changes | Stops tracking `node_modules/`, `dist/` and `.idea/`, which `.gitignore` already listed. Files on disk are untouched. | `git ls-files -ci --exclude-standard` returns 0 after the change. Local MFE builds still run. |
| `e7fe0017` | 19 files: 10 Java classes, 5 test configs, 3 accounting files, 1 new | Removes the public `local-dev-service-token-change-me` default from 10 classes. Adds `AccountingExceptionHandler`. Accounting returns 404 for unknown IDs. | Maven: `inventory-service` 27/27, `stock-service` 9/9, `accounting-service` 26 pass with 5 skipped (Postgres-gated), `procurement-service` 8/8, `order-service` 99/99. |
| `024d9859` | 12 files: 1 new Dockerfile, 7 deleted Dockerfiles, compose, two CI workflows, `.dockerignore` | Adds `frontend/Dockerfile.mfe` (build arg `APP`). Compose frontends build from source. CI image matrices expanded to 15 frontends and 18 backend services. crm and procurement stop publishing host ports. | `docker compose config` valid with a temporary token. Workflow YAML parses. `npm run build` passes for `mfe-cart` and `mfe-delivery`. Images not built (no Docker daemon). |
| `45afd047` | 13 files: 10 new service manifests, 1 new policy file, 2 edited policy files, kustomization | Deployments and Services for the 10 services that lacked them. Network rules for their gateway, database, Kafka and config traffic, and for each caller/callee pair. | `kubectl kustomize kubernetes` builds 34 Deployments and 18 NetworkPolicies. Not applied to a cluster. |
| `881fe882` | 2 docs | Marks K3, K9, K13, K18 resolved; adds K25 and K26 open; updates S3 and S9 in the security audit. | Documentation only. |

Earlier in the session (before the baseline): commit `c57de2be` added the frontend shell, Kubernetes manifests, scripts and docs (the untracked import), and replaced the Slack webhook URLs with `REPLACE_ME` placeholders in three Alertmanager configs.

## Uncommitted changes (working tree, 2026-11-01)

| File | Change | Reason | Verification |
|---|---|---|---|
| `frontend/mfe-user/src/pages/AdminUserList.tsx` | Added `Button` to the MUI import | Fixes B-10: the component throws on render | `tsc` TS2304 count is zero; `npm run build --workspace=mfe-user` passes |
| `frontend/mfe-product/src/pages/ProductPage.tsx` | Moved `noWrap` from `sx` to the `Typography` prop | Fixes B-11: long names did not truncate | `tsc` TS2769 gone; build passes |
| `backend/accounting-service/.../controller/InternalAccountingController.java` | Both endpoints use a shared `requireServiceToken` helper with `MessageDigest.isEqual` (was `String.equals`) | Fixes B-03, the last non-constant-time service-token comparison | Accounting module: 22 pass, 5 Postgres-gated skipped |
| `backend/accounting-service/.../AccountingSecurityAndControllerTest.java` | New test `testInternalAccountingRefusesWrongServiceToken` | Regression test for B-03 | Passes |
| `docs/status/*.md` | New status documents | This audit | — |
| `backend/config-repo/crm-service-docker.yml` (nested repo) | `loyalty-service:8086` → `8087` | Fixes B-13 | Config only; not built or tested |

## Added files (since baseline)

- `frontend/Dockerfile.mfe`: one multi-stage build for every frontend workspace.
- `kubernetes/services/{accounting,analytics,cart,crm,error-stack,inventory,loyalty,procurement,stock,qa}-service.yml`: Deployments and Services.
- `kubernetes/security/service-call-policies.yml`: gateway rule and one rule per caller/callee pair.
- `backend/accounting-service/src/main/java/com/ecommerce/order/accounting/controller/AccountingExceptionHandler.java`.
- `docs/status/` (this folder).

## Deleted files

- Seven frontend Dockerfiles superseded by `Dockerfile.mfe`: `Dockerfile.shell`, `.product`, `.order`, `.user`, `.inventory`, `.super-admin`, `.delivery`.
- 30,717 tracked build and IDE files (see `94eef1d4`).

## Modified files (since baseline)

- Backend: `InternalAccountingController`, `AccountingService` (two `orElseThrow` lines), `AccountingSecurityAndControllerTest` (three tests), `OrderService`, `PricingClient`, `ReturnStockClient`, `StorefrontClient`, `StockMetadataSyncClient`, `StockController`, `StockInternalController`, `StockServiceIntegrationTest`, and test `application.yml` in five services.
- Configuration: `docker-compose.yml` (frontend block rewritten, two port removals, comment), `.github/workflows/ci-backend.yml` (+9 services), `.github/workflows/ci-frontend.yml` (+8 apps, `Dockerfile.mfe`), `frontend/.dockerignore` (`*/dist`), `kubernetes/kustomization.yml`, `kubernetes/security/network-policies.yml`.
- Docs: `docs/completion/KNOWN_ISSUES.md`, `docs/completion/SECURITY_AUDIT.md`.

## Database changes

None. No Flyway migration was added, edited or removed in this session.

## API and behaviour changes

- Accounting: unknown ledger or transaction ID returns 404 (was 500). Rejected input returns 400 with `{"error": ...}` (was 500).
- Services that read `SERVICE_TO_SERVICE_TOKEN` now fail at startup when it is unset (was: accept the public default). Compose and Kubernetes already required it.
- crm-service and procurement-service are no longer reachable from the host on 8092 and 8093. Gateway access is unchanged.
- Storefront shoppers get the storefront's own catalog and check out into its tenant (SF-01, B-25). The storefront is found from the address (a verified custom domain or `<slug>.<base-domain>`), else `?storefront=<slug>`, else the one remembered for the session. `GET /api/products` takes `storefront=<slug>` and returns only that tenant's active products; an unknown or inactive slug gets 404. `GET /api/products/public/storefront/resolve` also accepts the platform address `<slug>.<base-domain>`. Checkout receives the slug from `mfe-order`. The cart is still shared (K2).
- Admin analytics dashboards (`mfe-analytics`, and `/analytics` in `mfe-order`) now name each section that failed to load, offer a Retry, and clear that section rather than keep figures from the previous time range (B-37).
- Frontend containers are built from source. `dist/` is no longer committed, so a checkout without a local build cannot serve the MFEs from bind mounts.

## Configuration changes

- Test profiles for accounting, order, inventory, procurement and stock supply `SERVICE_TO_SERVICE_TOKEN=test-service-token`.
- Kubernetes: 10 new Deployments with `replicas: 1` and resource limits. `qa-service` runner paths are `emptyDir` volumes.

## Impact on other modules

- Any environment that starts these five services without `SERVICE_TO_SERVICE_TOKEN` now fails fast. That is intended, but it affects any local script that relied on the default.
- Clients that called accounting for unknown IDs and expected 500 now receive 404. No code in this repository depends on the 500.
- The network policies allow only the listed caller/callee pairs. A pair missing from `service-call-policies.yml` will time out in a cluster; this was not tested against a live cluster.

## Verification status

- Full backend test run: see [TEST_AND_VERIFICATION_REPORT.md](TEST_AND_VERIFICATION_REPORT.md).
- Frontend: all 16 workspace builds pass. Tests pass in shared-library (39), mfe-order (20) and mfe-product (17).
- Not verified: container images, Kubernetes apply, Postgres tenant-isolation test, end-to-end smoke test.
