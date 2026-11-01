# Code Cleanup Report

Recorded 2026-11-01 on `feature/ucp-completion-modernization`. **Nothing was removed in this pass.** The cleanup rules in the completion plan require a reference check, a runtime check and an owner's confirmation before deletion. The items below have had the first check, and the rest is deferred.

## Removed

None.

## Retained, with reasons

| Item | Reason |
|---|---|
| Procurement's legacy `suppliers`, `purchase_orders` and `goods_receipts` tables | Still in migrations and the legacy API. Whether any caller still uses them was not checked, so they are kept. |
| `stock_legacy_import` and `STOCK_LEGACY_*` settings | Used by the stock import path in `docker-compose.yml`. |
| Order migrations V1 and V4 to V11 (no V2 or V3) | Flyway accepts the gap. The reason is not recorded, and no file is removed. |
| Applied Flyway migrations of any service | The completion plan forbids editing applied migrations. |

## Candidates, deferred

| Item | Checked | Deferred because |
|---|---|---|
| `backend/legacy/` (43 files: `accounting`, `analytics`, `order-migrations`) | Not referenced by `backend/pom.xml`, `docker-compose.yml` or `.github/workflows/*.yml`. | Needs the owner's confirmation before archive or delete (OPEN_ISSUES 8). |
| `frontend/mfe-order/src/pages/AdminAnalyticsDashboard.tsx` | Identical to `mfe-analytics`, same SHA-256. | Deleting one copy changes which workspace owns the component (OPEN_ISSUES 9). |
| `docs/quality/REQUEST_10_PROMPT.txt` | Present in the repository; a prompt, not documentation. | Not searched for references yet. |
| `docs/quality/SECURITY_AUDIT.md` and `docs/completion/SECURITY_AUDIT.md` | Both exist; their contents overlap. | Needs a decision on which one is current. |
| `docs/technical/SYSTEM_ARCHITECTURE.md` and `docs/architecture/SYSTEM_ARCHITECTURE.md` | Both exist; their contents overlap. | Same. |
| Stale status documents that say no Docker daemon is available | Found in `docs/completion/`, `docs/status/` and `docs/quality/`. | Scheduled for Phase 9, with the documentation rewrite. |
| `info/` | A separate git repository, untracked by UCP. | Not part of UCP's history. Left unchanged. |

## Refactored

None in this pass.

## Found, not cleanup

- No `TODO` or `FIXME` markers in source (excluding docs and build output).
- `ProductRepository.searchTenantProducts` (SF-01) repeats the filters of `searchProducts` with a tenant condition. The two queries are kept separate so the default catalog's behaviour does not change. A shared query would be a cleanup candidate once the default catalog's scope is decided (OPEN_ISSUES 2).

## Scratch resources created in this session

These are not part of the repository. They are listed so they can be removed:

- Databases in the `ucp-test-pg` container: `ucp_scratch_phase1_accounting`, `_user`, `_delivery`, `_inventory`, `_stock`, `_cart`, `_loyalty`, `_order`, and `ucp_scratch_full_accounting`, `_user`, `_delivery`, `_inventory`.
- Local branch `backup/pre-modernization-faeea04a`.
