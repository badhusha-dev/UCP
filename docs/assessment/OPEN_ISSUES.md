# Open Issues

Recorded 2026-11-01. Each item names the record it comes from and who must act. Items marked **decision** cannot be fixed by code alone.

## Decisions needed from the owner

| # | Issue | Source | What is needed |
|---|---|---|---|
| 1 | Closed 2026-11-01 (B-38, commit `f216d479`): warehouse operators can receive INVENTORY access with goods receipt and PO view. | BUGS_AND_TECHNICAL_DEBT B-38 | None. |
| 1b | INVENTORY_MANAGER and INVENTORY_ANALYST are still refused, because they hold `supplier:view`. Should Inventory access also include supplier view? | BUGS_AND_TECHNICAL_DEBT B-38 | Yes: add `supplier:view` to the allowed set. No: leave them refused. |
| 2 | Default catalog and AI chat return active products from every tenant without a token (SECURITY_ASSESSMENT). | SECURITY_ASSESSMENT, finding 2 | Whether the default (no storefront) catalog should be limited to the legacy tenant, or should stay cross-tenant by design. |
| 3 | Signing key storage for JWT (B-05, K10). | BUGS_AND_TECHNICAL_DEBT | Vault or a Kubernetes Secret. |
| 4 | Payment provider (B-01). | BUGS_AND_TECHNICAL_DEBT | Choice of PSP. |
| 5 | Customer Support scope (roadmap 2.1). | COMPLETION_ROADMAP | Tickets, roles and channels. |
| 6 | Quality Center tenant scope (roadmap 2.4). | COMPLETION_ROADMAP | Platform-only or tenant-scoped. |
| 7 | Whether to push 15 unpushed `main` commits and the feature branch. | Git state | Explicit approval. Pushing the feature branch also pushes the `main` commits it contains. |
| 8 | Legacy code in `backend/legacy/` (43 files). | REPOSITORY_BASELINE | Archive or delete. Nothing in the build refers to it. |
| 9 | The duplicated analytics dashboard in `mfe-analytics` and `mfe-order`. | REPOSITORY_BASELINE | Which workspace owns the shared component. |
| 10 | Whether `order-service` V2 and V3 were removed on purpose. | IMPLEMENTATION_PROGRESS | Confirmation only. Flyway accepts the gap. |

## Requires owner action (no code can close it)

| # | Issue | Source |
|---|---|---|
| 11 | Rotate the credentials removed from tracked files (B-06 to B-09). They remain in git history and in the local checkpoint branch's history. | BUGS_AND_TECHNICAL_DEBT |
| 12 | `backend/config-repo` is a nested git repository with no remote and uncommitted changes (TD-07). | BUGS_AND_TECHNICAL_DEBT |
| 13 | The `info/` folder is a separate git repository, untracked by UCP. | Git state |

## Open defects (code can close them)

| # | Issue | Source |
|---|---|---|
| 14 | Nine services are not tenant-scoped: stock, cart, loyalty, notification, qa, error-stack, legacy procurement, analytics and crm (B-02, K2). The crm slice was blocked on Docker and Postgres, which are now available. | BUGS_AND_TECHNICAL_DEBT |
| 15 | Storefront cart is shared across storefronts (K2, SF-01). | IMPLEMENTATION_PROGRESS |
| 16 | Logout does not revoke tokens (B-21). | BUGS_AND_TECHNICAL_DEBT |
| 17 | Posted accounting lines have no database immutability trigger (B-29). The scratch Postgres now makes this testable. | BUGS_AND_TECHNICAL_DEBT |
| 18 | crm `StaffAccessFilter` refuses SUPER_ADMIN (B-36). | BUGS_AND_TECHNICAL_DEBT |
| 19 | Gateway actuator exposes `refresh` and `flyway` in the non-production profile (B-19, K8). The config is in the nested config-repo. | SECURITY_AUDIT |
| 20 | Atomic stock reservation at checkout (A-04). | COMPLETION_ROADMAP |
| 21 | Delivery is not created on order fulfilment (A-06). | COMPLETION_ROADMAP |
| 22 | Inventory and stock services both own stock tables (SERVICE_BOUNDARIES). | SERVICE_BOUNDARIES |
| 23 | Frontend tests are missing in 13 of 16 workspaces (TD-04). | REPOSITORY_BASELINE |
| 24 | Node version differs between local (24.5) and the frontend image (20); no `engines` field (TD-08 area). | REPOSITORY_BASELINE |
| 25 | Stale status documents say no Docker daemon was available. | TEST_RESULTS |
| 26 | The cart is not shared between micro-frontends, so the UI checkout cannot be completed in the deployed build (B-40). Needs a choice of approach; recommended: share `@ecommerce/shared-library` as a federation singleton. | BUGS_AND_TECHNICAL_DEBT B-40 |
