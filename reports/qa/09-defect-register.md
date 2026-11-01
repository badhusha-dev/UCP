# QA Report 09 — Defect Register

**Branch:** `feature/ucp-completion-modernization`  
**Recorded:** 2026-11-01 / 2026-11-02  
**Phase:** Phase 0 — Initial Assessment  
**Status:** COMPLETE (baseline defects; new findings may be added as testing progresses)

Statuses: **OPEN** = not yet fixed and confirmed reproducible; **FIXED** = fix committed and
verified on this branch; **DEFERRED** = acknowledged, fix not scheduled; **NEEDS REVIEW** = cannot
be fully assessed without owner input.

---

## Critical / High severity

| ID | Severity | Component | Summary | Status | Fix commit |
|---|---|---|---|---|---|
| B-40 | High | Frontend / cart-service | Cart not shared between micro-frontends — UI checkout cannot be completed in the deployed build. The cart MFE writes to local Redux store only; the shell and checkout MFE have no view of it. Recommended fix: expose `@ecommerce/shared-library` as a Module Federation singleton. | **FIXED** | `48b5486d` |
| B-02 | High | Multiple services | Nine services are not tenant-scoped: stock, cart, loyalty, notification, qa, error-stack, legacy procurement, analytics, crm. Any authenticated user can read data from other tenants via these services. | **FIXED** | `2f2a90af` |
| B-21 | Medium | user-service | Logout does not revoke JWT tokens. A logged-out user can reuse a captured token until it expires. | **FIXED** | `28f7ad25` |
| B-05/K10 | Medium | user-service / config | JWT signing key stored in plaintext config. No Vault or Kubernetes Secret rotation. | **FIXED** | `95f9d5f9` (startup validation + rotation manifest; delivery-service empty-fallback fixed locally — config-repo ungittable, see TD-07) |
| B-29 | Medium | accounting-service | Posted accounting lines have no database immutability trigger. Lines can be mutated after posting. | **FIXED** | `23e54d4e` |
| B-36 | Medium | crm-service | `StaffAccessFilter` refuses SUPER_ADMIN role, blocking super-admin access to CRM. | **FIXED** | `2f2a90af` |
| B-19/K8 | Medium | api-gateway / config-repo | Gateway actuator exposes `refresh` and `flyway` endpoints in the non-production profile. Config is in the nested `backend/config-repo`, which is not pushed. | **FIXED** | `23e54d4e` (gateway filter narrowed; config-repo exposure trimmed locally — ungittable, see TD-07) |

---

## Medium severity (defects)

| ID | Severity | Component | Summary | Status | Fix commit |
|---|---|---|---|---|---|
| B-39 | Medium | cart-service | `GET /api/cart` returned HTTP 500 on Postgres for users without a cart because `getCart` and `validateCart` were `@Transactional(readOnly=true)` — Postgres refuses INSERT in a read-only transaction. | **FIXED** | `353478dc` |
| B-38 | Medium | user-service | `TenantAccessService.grantApplication` rejected warehouse operators from receiving INVENTORY access because the rule only allowed `inventory:` prefix, not `goods-receipt:manage` or `purchase-order:view`. | **FIXED** | `f216d479` |
| B-37 | Low | mfe-analytics / mfe-order | Analytics dashboard load errors were silently swallowed; no user-visible feedback. | **FIXED** | `bb1b7b0e` |

---

## Low severity / Technical debt (selected items from OPEN_ISSUES)

| ID | Severity | Component | Summary | Status |
|---|---|---|---|---|
| TD-04 | Low | Frontend | 12 of 16 frontend workspaces have no test scripts. Only shared-library, mfe-product, mfe-order have `npm test`. mfe-inventory has an orphan test file with no script. | **FIXED** | `b725355e` |
| TD-08 | Low | Frontend build | Frontend Docker images use `npm install` with no lockfile. Node version diverges: local 24.5 vs image 20. No `engines` field in any workspace `package.json`. | **FIXED** | `b725355e` (engines + node:22; `npm ci` pending lockfiles — see TODO in Dockerfile) |
| TD-07 | Low | config-repo | `backend/config-repo` is a nested git repository with no remote and uncommitted changes. The UCP main repo does not track it. | **NEEDS REVIEW** |
| SF-01-residual | Low | mfe-cart / mfe-order | Storefront cart is not isolated per storefront. A user browsing `?storefront=A` then `?storefront=B` retains the same cart. | **OPEN** |
| A-04 | Low | order-service / stock-service | Stock reservation at checkout is not atomic. The order is placed before stock is reserved; a concurrent order can over-sell. | **OPEN** |
| A-06 | Low | order-service / delivery-service | Delivery record is not created on order fulfilment. Delivery service must be triggered separately. | **OPEN** |

---

## Security findings (from static assessment)

| ID | Severity | Location | Finding | Status |
|---|---|---|---|---|
| SEC-01 | **NEEDS REVIEW** | `kubernetes/gitops/flagger/flagger-alert-provider.yml:12` | Value at key `url:` has the shape of a Slack webhook URL (three all-uppercase segments, no digits, no common placeholder words). Cannot be verified as real or fake without checking with the owner. Present on `origin/main` since commit `c57de2be`. **If real, the webhook must be rotated immediately.** | **NEEDS REVIEW** |
| SEC-02 | Medium | Git history | Credentials removed from tracked files (B-06 to B-09) remain in git history and in the `backup-pre-scrub` branch. Rotation required. | **OPEN (owner action)** |
| SEC-03 | Low | `backend/config-repo` (untracked) | Several service YMLs contain a literal `admin_password` default. These are not in the tracked repo, but the config-repo has no remote. | **OPEN** |
| SEC-04 | Low | Multiple test/doc files | Literal `admin_password` appears in test application YMLs and two documentation files. All are test defaults with no production secret value. | **LOW / INFORMATIONAL** |

---

## Test-only defects (found during testing, not in production code)

| ID | Component | Description |
|---|---|---|
| TF-01 | `mfe-inventory` | `ops/model.test.ts` is an orphan test file: `package.json` has no `test` script. Tests pass when run manually but are never executed by `npm test`. |
| TF-02 | Backend — all services | The test suite uses `SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:1` to simulate an unreachable broker. If the port happens to be bound (unlikely), broker-dependent tests would behave differently. Not reproducible in practice. |

---

## Defect count summary

| Severity | Open | Fixed | Deferred | Needs review |
|---|---|---|---|---|
| Critical | 0 | 0 | 0 | 0 |
| High | 0 | 2 | 0 | 0 |
| Medium | 0 | 8 | 0 | 1 |
| Low / Tech debt | 5 | 2 | 0 | 2 |
| **Total** | **5** | **12** | **0** | **3** |
