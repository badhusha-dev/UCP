# Bugs, Security Issues and Technical Debt

Verified 2026-11-01 against HEAD `881fe882` and the uncommitted working-tree changes noted per item.
Status values: **FIXED** (change made and checked here), **OPEN**, **PLANNED** (fix identified, not yet applied).
Feature IDs (`F-`, `A-`, `O-`) refer to [INCOMPLETE_FEATURES.md](INCOMPLETE_FEATURES.md).

## Critical (P0)

| ID | Area | Finding | Evidence | Status | Recommended fix |
|---|---|---|---|---|---|
| B-01 | Payments | Payment and refund confirmations are accepted on a shared token only. | K1; order-service `X-Service-Token` callbacks | OPEN | Build `payment-service` with signed webhooks and idempotency keys (F-01). |
| B-02 | Multi-tenancy | Nine services have no tenant column; platform tokens read all tenants' data. | K2; gateway refuses tenant tokens only | OPEN | Per-service Flyway migration adding `tenant_id`, query filters, cross-tenant tests (F-02). |
| B-03 | Security | `InternalAccountingController` compares the service token with `String.equals` (lines 28 and 35). Other services use `MessageDigest.isEqual`. | `grep` across backend | **FIXED** (uncommitted) | Both endpoints now call `requireServiceToken`, which uses `MessageDigest.isEqual`. Test `testInternalAccountingRefusesWrongServiceToken` added; accounting module passes (22 tests, 5 Postgres-gated skipped). |
| B-04 | Analytics | `InventoryAnalyticsController` aggregated across all tenants and authorised with `role.contains("ADMIN")` on a role header that a direct caller can forge. | K7, S5 | **FIXED in service**; gateway policy decision pending | The query filters by the verified token's tenant, and the controller requires `analytics:read` from the token. Two tests in `CatalogAdminTenantIsolationTest`. The gateway still refuses tenant tokens on this route; opening it needs a decision on which tenant roles get `analytics:read`. |
| B-05 | Identity | One HS256 secret is shared by seven services; tokens are not checked for issuer or audience. Any holder of the secret can mint admin tokens. | K10, S7 | OPEN | Asymmetric signing with `iss`/`aud` checks (F-04). Needs a key-management decision. |
| B-06 | Secrets | Committed Alertmanager bearer token (value redacted here) in `devops/prometheus/alertmanager.yml:70`. | `git grep` at HEAD | **FIXED in files**; rotation pending | Removed: the target is a health endpoint that needs no credentials. Rotate the old token wherever it was used. |
| B-07 | Secrets | A PagerDuty routing key (value redacted here) appears in three committed Alertmanager configs. It is probably a placeholder, but its name says production. | `git grep` at HEAD | **FIXED in files**; confirm and rotate pending | Replaced with `REPLACE_ME_PAGERDUTY_ROUTING_KEY` in all three configs. Inject at deploy time (F-16). Rotate if the old key was live. |
| B-08 | Secrets | Committed database password (value redacted here) in `kubernetes/database/flyway-job.yml:15` and in docker-compose defaults. | `git grep` at HEAD | **FIXED in files**; rotation pending | Compose uses `${POSTGRES_PASSWORD:?}` (6 places, plus error-stack now receives it), the Flyway job reads `ecommerce-secrets`, Helm defaults to `REPLACE_ME`, the Vault seed scripts read the environment, and error-stack's local default is `${POSTGRES_PASSWORD}`. The legacy `devops/docker-compose.yml` was also fixed. |
| B-09 | Secrets | Committed Grafana admin password (value redacted here) at `docker-compose.yml:151`, in `kubernetes/monitoring/prometheus-grafana.yml`, and in two devops test scripts. | `git grep` at HEAD | **FIXED in files**; rotation pending | Compose uses `${GRAFANA_ADMIN_PASSWORD:?}`, Kubernetes reads key `GRAFANA_ADMIN_PASSWORD` from `ecommerce-secrets` (add it to your local `02-secrets.yml`), and the scripts read `$env:GRAFANA_ADMIN_PASSWORD`. |
| B-35 | Secrets | The JWT signing secret in the local `.env` was also committed in `devops/helm/ecommerce-platform/values.yaml` and both Vault seed scripts. | `git grep` at HEAD | **FIXED in files**; rotation pending | Helm defaults to `REPLACE_ME`, which fails fast at startup because it is too short to be an HS256 key. The Vault scripts read `JWT_SECRET` and refuse to run without it. Rotate the secret: it remains in git history. |

## Functional bugs

| ID | Area | Finding | Evidence | Status | Recommended fix |
|---|---|---|---|---|---|
| B-10 | mfe-user | `AdminUserList.tsx` uses `<Button>` in three places but never imports it. The component throws `ReferenceError` on render, and the Vite build still passes. | `tsc` TS2304 at lines 421 (×2); `Button` not in the MUI import block | **FIXED** (uncommitted) | Added `Button` to the `@mui/material` import. `tsc` TS2304 count is now zero. |
| B-11 | mfe-product | `ProductPage.tsx` passes `noWrap` inside `sx`, where it is an invalid CSS property. Long product names do not truncate. | `tsc` TS2769 at line 979 | **FIXED** (uncommitted) | Moved `noWrap` to the `Typography` prop. TS2769 is gone. |
| B-12 | accounting | Unknown ledger or transaction IDs return HTTP 500 instead of 404; rejected input returned 500 instead of 400. | K18 | **FIXED** (`e7fe0017`) | `ResponseStatusException` 404 plus `AccountingExceptionHandler` (400). Three tests added. |
| B-13 | crm / loyalty | crm-service calls `http://loyalty-service:8086`, which is cart's port. Loyalty listens on 8087. Every crm→loyalty call would fail. | `backend/config-repo/crm-service-docker.yml` vs `loyalty-service.yml` | **FIXED** (on disk, nested repo, uncommitted) | Changed to 8087. The change must be committed in the nested `config-repo`. |
| B-14 | Checkout | Promo codes ("UNIVERSAL20", "SPRING2026") are hard-coded in the UI and not validated by the pricing API. | K24 | OPEN | Add a pricing quote endpoint and validate codes server-side before checkout. |
| B-15 | Frontends | Frontends hard-coded `http://localhost:8080` or `http://<host>:8080` (call sites in 11 workspaces plus shared-library, not only mfe-order and mfe-product), so they could not run behind any other origin or over HTTPS. | K12, S10 | **FIXED** | Every API call uses a same-origin `/api/...` URL, which nginx (containers) and the Vite dev proxies forward to the gateway. `:8080` now appears only in Vite proxy targets. |
| B-16 | Events | cart and loyalty published Kafka events on the request thread. A broker outage added up to 5 s to each publishing request, and failed events were lost. | K5 | **FIXED** | Both write to the shared outbox inside the request's transaction. A scheduled publisher sends after commit, so an outage delays events instead of slowing requests or losing events. |
| B-17 | Ops | Alertmanager Slack URLs are `REPLACE_ME` placeholders (commit `c57de2be`). Slack notifications do nothing until real URLs are injected. | Commit history; K-log | BLOCKED | Inject from a Secret at deploy time (F-16). |
| B-18 | Ops | `kubernetes/kustomization.yml` references `base/02-secrets.yml`, which is gitignored. A fresh clone cannot build the manifests. | `.gitignore` lists the file; `kubectl kustomize` only passes locally | **FIXED** | `kubernetes/base/02-secrets.example.yml` lists every key the manifests read, and `docs/deployment/KUBERNETES_DEPLOYMENT.md` documents the copy step. Verified: a clone of HEAD fails without the file and builds (34 Deployments) once the template is copied. |
| B-36 | crm | `StaffAccessFilter` admits only the exact roles ADMIN and MANAGER, so a SUPER_ADMIN (or any delegated role with `crm:read`), which the gateway lets through, gets 403 from crm-service. The filter also trusts the role header instead of a verified token. | `crm-service` `StaffAccessFilter.STAFF` | OPEN | Replace it with token verification and permission checks, as part of the crm tenant slice. |
| B-37 | Analytics UI | The admin analytics dashboards (`mfe-analytics` and the copy in `mfe-order`) set an `error` state but never rendered it, and every request swallowed its own failure (`.catch(() => null)`), so a failed load showed no message and kept the previous time range's figures. | `AdminAnalyticsDashboard.tsx` in both | FIXED 2026-11-01 | Each failed section is named in an error alert with a Retry button, and is cleared instead of showing stale data. Verified by typecheck, build, lint and the mfe-order tests; not exercised in a browser. Both workspaces still route to their own identical copy of the file (open: share one component). |
| B-38 | Access (user-service) | Granting the INVENTORY application to a `WAREHOUSE_OPERATOR` is refused with 400. `TenantAccessService.grantApplication` requires every INVENTORY role permission to start with `inventory:`, but V13 gave that role `warehouse:view`, `goods-receipt:manage` and `purchase-order:view`. The H2 suite never runs V13, so only the Postgres test shows it. | `TenantAccessPostgresIntegrationTest` line 109 expects 204, gets 400 (run 2026-11-01 against `ucp_scratch_phase1_user`). The rest of that test has not run. | FIXED 2026-11-01 (commit `f216d479`), by the owner's decision that a warehouse operator's Inventory access includes goods receipt and PO view. The rule now accepts `inventory:view` or `inventory:read` for read access, and only inventory and warehouse permissions plus `goods-receipt:manage` and `purchase-order:view`. Verified: 19 user-service tests pass on Postgres, including the original test (now run to its end) and a new negative test. Still refused: INVENTORY_MANAGER and INVENTORY_ANALYST, which hold `supplier:view`; owner decision pending (OPEN_ISSUES 1b). Not verified: that procurement-service accepts `goods-receipt:manage` from an INVENTORY-scoped token. Earlier analysis, kept for the record: checked on `ucp_scratch_phase1_user` (2026-11-01): no seeded role can receive the INVENTORY application under the current rule. The rule requires `inventory:read` and only `inventory:` permissions. Roles that hold `inventory:read` (ADMIN, MANAGER, SUPER_ADMIN, WAREHOUSE_OPERATOR) also hold non-inventory permissions. Roles from V13 (INVENTORY_MANAGER, INVENTORY_ANALYST, PROCUREMENT_*) hold `inventory:view`, not `inventory:read`. Trimming WAREHOUSE_OPERATOR alone would satisfy this test, but not the other roles. Owner to decide whether a warehouse operator's Inventory access includes goods receipt and purchase-order view (procurement permissions). Yes means widening the rule to the V13 vocabulary; no means trimming the role. Not changed here. |

## Security (other)

| ID | Area | Finding | Evidence | Status | Recommended fix |
|---|---|---|---|---|---|
| B-19 | Gateway | Actuator exposes `refresh`, `flyway` and `metrics` in non-prod (K8, S6). | `backend/config-repo/application.yml:10` | OPEN (config-repo has uncommitted changes, so not edited) | Restrict to `health,info,prometheus` in shared config. |
| B-20 | Identity | Login had no lockout and no rate limiting. | `AuthAndUserIntegrationTest` (5 tests), `LoginRateLimitTest` (3 tests) | **FIXED** | Account lockout in user-service, and a per-address limit of 20 sign-ins per minute in api-gateway. The gateway counter is per instance; move it to Redis for a cluster-wide limit. |
| B-21 | Sessions | Logout does not revoke issued JWTs. | No denylist; no refresh tokens | OPEN | Denylist in Redis, or short-lived access tokens with refresh (F-08). |
| B-22 | Identity | Service-token comparison is not constant-time in `accounting-service`. Duplicate of B-03. | — | **FIXED** (see B-03) | — |
| B-23 | Ops | crm-service and procurement-service no longer publish host ports (K9). Their role-header trust remains (K9, S9). | `docker-compose.yml` | PARTIALLY FIXED | Add JWT verification to both services. |
| B-24 | Mesh | The ten new Kubernetes services have no Istio `AuthorizationPolicy` (K25). | `kubernetes/mesh/authorization-policies.yml` | OPEN | Add per-caller policies (F-12). |

## Integration problems

| ID | Area | Finding | Status | Recommended fix |
|---|---|---|---|---|
| B-25 | Checkout | `storefront` slug is accepted by checkout, but no UI sent it, so all orders landed in the legacy tenant (K4). | FIXED 2026-11-01 for the catalog and checkout: the storefront UI resolves the slug (verified host, `<slug>.<base-domain>`, or `?storefront=`) and sends it; product-service lists only that tenant's products; order-service writes that tenant. Cart-service is still not tenant-scoped (K2), so the cart is shared across storefronts; checkout refuses items the storefront's tenant does not sell. Verified 2026-11-01 in the built-in browser (catalog for a storefront, an empty storefront and an unknown slug) and through the gateway on the local deployment (checkout into a storefront's store; an empty tenant's storefront refused). Checkout was not clicked through in the UI. |
| B-26 | Accounting | Tenant login screen missing in `mfe-accounting`, and no tenant holds an Accounting grant (K17). | OPEN | Add the login route and grant through Super Admin. |
| B-27 | Events | `order-events` carried no tenant (K4). | **FIXED** | Every order event has `tenantId`, taken from the order when the caller does not set it, and the outbox row records it. No consumer change was needed (three read a JSON tree; notification's DTO gained the field). Consumers do not yet scope data by it. |

## Database

| ID | Finding | Status | Recommended fix |
|---|---|---|---|
| B-28 | Delivery migrations V1 and V2 and inventory migration V4 have never been applied to Postgres (K11). The H2 tests apply migrations, but Postgres-only behaviour is unverified. | OPEN | Run migrations against a scratch Postgres (`localhost:15432`). |
| B-29 | Accounting posted lines are not protected by a database trigger or permission against UPDATE and DELETE (Phase 9). | OPEN | Add a trigger in a new Flyway migration. |
| B-30 | Accounting Flyway versions are non-contiguous (present: 1, 4, 5, 10–19; missing 2, 3, 6–9). Gaps are legal in Flyway, but this audit cannot confirm no applied migration was removed. | NOT VERIFIED | Compare `flyway_schema_history` in a live database with the repository. |
| B-31 | No migration adds `tenant_id` to the nine non-tenant services (F-02). | OPEN | Additive migrations only, per the project rules. |

## Performance

| ID | Finding | Status | Recommended fix |
|---|---|---|---|
| B-32 | Redis is declared in product-service but never used. There is no cache and no distributed lock anywhere (F-09). | OPEN | Remove the unused dependency, or add caching behind a tested interface. |
| B-33 | Analytics aggregates across the whole table on each request (K7). | OPEN | Bounded queries and tenant scope (B-04). |
| B-34 | Synchronous Kafka publishing on the request path (K5, B-16). | **FIXED** | order, inventory, stock, cart and loyalty write to the outbox; wire bytes unchanged (byte-equality tests for the RAW topics). notification's dead-letter replay is operator-triggered and stays direct. |

## Technical debt

| ID | Area | Finding | Status | Recommended fix |
|---|---|---|---|---|
| TD-01 | Frontend typecheck | `tsc --noEmit` failed in 7 of 16 workspaces: 76 unique errors (58 unused imports, 7 unused locals, 4 non-type-only imports, 1 undeclared Module Federation remote), plus 3 follow-on errors uncovered by the fixes. | **FIXED** | All 16 workspaces report 0 errors. Builds, lint (4) and node tests (76) still pass. |
| TD-02 | Frontend typecheck | `shared-library` imported types without `import type`, which fails under `verbatimModuleSyntax` in consumers (TS1484). | **FIXED** | Type-only imports use `import type`. shared-library compiles JSX with `react-jsx`, like its consumers, so it no longer needs `React` in scope. |
| TD-03 | Build gate | Vite builds do not typecheck, so B-10 shipped past CI, which only builds. | **FIXED** | `ci-frontend.yml` runs `tsc --noEmit` for each app before its build. |
| TD-04 | Test coverage | 12 of 16 frontend workspaces have no test files. CI's unit-test matrix runs 3 workspaces (shared-library, mfe-order, mfe-product). | OPEN | Add smoke tests for each application's routes. |
| TD-05 | Legacy code | `backend/legacy` has 43 files outside the parent POM. They are not built or tested. | OPEN | Delete or move to an archive branch after review (O-05). |
| TD-06 | Documentation drift | README says "23/23 containers UP" and "Docker Containers" badge. Compose defines 46 services. README also uses `d:\practical\ecommerce-platform`. | OPEN | Refresh README from this audit. |
| TD-07 | Versioning | `backend/config-repo` is a gitlink to a nested repository with 27 uncommitted entries. Configuration changes are not versioned in the parent repo. | OPEN | Give the config repo a remote, or move it into the parent. |
| TD-08 | Reproducibility | `frontend/Dockerfile.mfe` runs `npm install` without a lockfile (the lockfile is excluded by `.dockerignore` because it was generated on Windows). Image builds are not reproducible. | OPEN | Commit a Linux-compatible lockfile and use `npm ci`. |
| TD-09 | Documentation volume | `docs/completion/` contains many reports that predate this audit and disagree with each other. | OPEN | Mark superseded reports; this folder is now the source of truth for status. |
| TD-10 | Repository hygiene | `info/` is an empty nested git repo with no commits. `git add` fails on it. | OPEN | Remove the nested `.git` or document it. |
| TD-11 | Local history | The local branch `backup-pre-scrub` still holds the real Slack webhook URLs (see the earlier scrub). | OPEN | Delete it after the URLs are rotated. |
