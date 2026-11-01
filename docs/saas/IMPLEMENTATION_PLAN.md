# UCP SaaS Transformation — Implementation Plan

## Guardrails

- Extend the current backend and frontend; do not duplicate existing commerce services.
- Finish and verify one tenant-scoped vertical slice before broad product expansion.
- Keep tenant identities stable across service-owned databases; use service APIs for authorization, never cross-service database reads.
- Keep old JWT login available only for legacy non-tenant routes during the transition. New tenant routes require application- and tenant-scoped authorization.
- Validate authorization in the Gateway and the owning service. The Inventory service must not authorize from caller-supplied identity headers.
- Preserve existing databases, release ZIPs and historical Flyway migrations. Use additive migrations and document backfill/recovery.

## Architecture choices for the first slice

1. **Identity/control plane:** extend `user-service`; it already owns accounts, password hashing, roles, permissions and audit records. Do not add a duplicate identity service before evaluating an external OIDC provider in a separate ADR.
2. **Tenant administration:** persist tenants, memberships, application registry, tenant entitlements and per-user application grants in `user-service`'s database.
3. **Inventory ownership:** persist tenant IDs in `inventory-service`'s database and make inventory repository access tenant-scoped. Do not query the user database.
4. **Authentication context:** add an application/tenant login path that issues signed tokens with issuer, audience, expiration, application code, tenant ID and user identity. Keep the legacy token shape for existing routes while migration proceeds.
5. **Immediate revocation:** Inventory checks the signed token locally and asks the identity API to revalidate the token's current membership, entitlement and grant before tenant-protected operations. Fail closed if that authorization check is unavailable. This avoids treating a still-unexpired JWT as an active grant after revocation.
6. **Trusted service call:** authenticate Inventory-to-identity authorization checks with the existing service-to-service credential, moved to environment configuration and never exposed to frontends. Do not trust `X-Tenant-ID`/`X-User-*` as proof.
7. **Frontend reuse:** make the existing `mfe-inventory` independently usable with its own app login/tenant selection. Add a small standalone Super Admin workspace for the tenant/grant workflow, sharing the current UI library and auth contracts. Keep shell/federated use available where compatible.
8. **Data migration:** assign pre-existing inventory data to a reserved `legacy` tenant. New client tenants remain empty until enabled and explicitly granted.

## First vertical slice — required before phase expansion

### Persistence and API

- Add additive Flyway migrations in `user-service` and `inventory-service`.
- Register the platform application codes needed to exercise the slice: `SUPER_ADMIN` and `INVENTORY`.
- Add persisted tenant, membership, entitlement, grant, role assignment and audit models with unique constraints and indexes.
- Add Super Admin APIs to create clients, enable/disable an application for a client, add a member, grant Inventory access and revoke access.
- Add application-scoped login and authorization revalidation APIs.
- Add tenant-scoped inventory list/detail and the catalog operations required by the first workflow. Scope all Inventory admin APIs before they can return tenant-owned data.
- Exclude the tenant-protected routes from anonymous gateway paths. Keep the existing product-facing anonymous routes only if their tenant resolution and non-sensitive output are explicitly documented; otherwise require an authorized tenant context.

### Frontend

- Add a standalone Super Admin sign-in and client-access console for the operations above.
- Add Inventory's separate sign-in experience with tenant selection; the server resolves the selected tenant and confirms entitlement and grant before issuing the scoped token.
- Use runtime public configuration for the API URL. Do not encode secrets or allow arbitrary redirect URLs.
- Show access-denied and revoked-session states without exposing another tenant's records.

### Acceptance tests

1. A Super Admin creates Client A and Client B in persistent storage.
2. Super Admin enables Inventory for A and leaves it disabled for B.
3. An employee gets membership and the Inventory application grant only in A.
4. App-scoped login succeeds for A and is denied for B.
5. The employee can read/write only A's inventory records.
6. Manipulating URL/query/body tenant IDs cannot select B; request tenant comes from the validated token and server-side grant check.
7. An unassigned user cannot log in to Inventory or call its API.
8. A forged `X-User-*` or `X-Tenant-ID` header cannot authorize a request, including a direct call to Inventory.
9. Revoking the grant makes the existing token fail subsequent Inventory authorization.
10. Tenant/application/grant changes and revocation are auditable without logging passwords or access tokens.
11. Tests run against PostgreSQL-compatible migrations and verify data survives application restart. Cross-tenant tests prove the inventory repository filters by tenant.

## Phased rollout after the slice

| Phase | Scope | Exit gate |
| --- | --- | --- |
| 0 | Repository assessment, path/archive checks, baseline and architecture plan. | Assessment and plan are checked into docs; baseline failures recorded. |
| 1 | Tenant model, shared identity context, app registry, grants, tenant-scoped Inventory and audit. | All first-slice acceptance tests pass against persistent PostgreSQL; direct-service and revocation tests pass. |
| 2 | Ten deployable application entry points, login experiences, URL registry, per-app build/deploy/smoke jobs. | Each app builds and deploys separately with validated origins and access policy. Reuse current shell/MFEs. |
| 3 | Super Admin client lifecycle, RBAC, entitlements, typed configuration drafts/publication/rollback. | Published config is versioned, schema validated, audited and cannot override security invariants. |
| 4 | Storefront and E-commerce Admin tenant integration. | Catalog, cart, order and pricing paths enforce the tenant context end-to-end. |
| 5 | Inventory/warehouse, retail vendors, restaurant branches/menus, delivery. | Retail and restaurant flows have separate domain rules and tenant isolation tests. |
| 6 | Payment, refunds, accounting, settlements and reconciliation. | Idempotency, audit, approval and reconciliation tests pass with provider sandbox. |
| 7 | CRM, Support, subscriptions, reporting and integrations. | Data scope and entitlement gates pass for each app. |
| 8 | Tester app, security suite, CI/CD, observability, backup/recovery and production readiness. | Release gates, restore drills, tenant-isolation scans and rollback are verified. |

## Implementation status — 2026-10-28

Phase 0 assessment and plan are recorded. The first vertical slice is implemented in `user-service`, `inventory-service`, the API Gateway, `mfe-super-admin` and `mfe-inventory`. Its user-service and Inventory migrations and tenant access scenario have run against isolated PostgreSQL databases. The exact routes and setup are in `API_CONTRACTS.md` and `SAAS_FIRST_SLICE.md`.

The remaining SaaS phases are not implemented. In particular, the standalone entry points for the other domains, tenant ownership migration in non-Inventory services, configurable tenant workflows, OIDC/MFA and production infrastructure are still planned. Do not treat the first slice as a completed ten-application SaaS transformation.

## Operational approach

- Deploy only `user-service`, `inventory-service`, gateway, Postgres and the two participating frontends for the first local slice; use the existing Compose services and database volumes.
- Add a dedicated migration test database or Testcontainers for tests; never run destructive fixture cleanup against the developer's persistent database.
- Roll forward with additive migrations. Backfill current Inventory rows to the reserved legacy tenant, verify row counts, then enforce `NOT NULL` and `(tenant_id, product_id)` uniqueness.
- Recovery: retain pre-migration database backups, verify the migration history, and restore the targeted service database from backup if a backfill verification fails. Do not drop tenant columns or rewrite applied migrations as a rollback mechanism.
- Production OIDC/MFA/key rotation, rate limiting, per-tenant RLS, all-service data migrations and full ten-app deployment remain later-phase work; do not claim these are completed by the first slice.
