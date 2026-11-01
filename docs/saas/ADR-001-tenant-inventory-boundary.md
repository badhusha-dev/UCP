# ADR-001: Tenant-scoped Inventory using the existing identity service

- **Status:** Accepted for the first vertical slice
- **Date:** 2026-10-28

## Context

The project already has a `user-service` that owns identity, global roles and RBAC, and a separate `inventory-service` that owns inventory data. The current `inventory_item` has a globally unique product ID and no tenant owner. The API Gateway verifies legacy JWTs and sanitizes identity headers, but the Inventory service permits all requests and authorizes admin operations using role headers. There is no tenant membership or app entitlement model.

## Decision

- Keep identity and platform tenant/access administration in `user-service` for the first rollout. Do not create a second identity service or a cross-service database join.
- Add tenant/application grant tables to the user service database; add the stable tenant ID to Inventory-owned data in the Inventory database.
- Issue scoped Inventory tokens only after the identity service verifies the account, tenant membership, tenant Inventory entitlement and user Inventory grant.
- Validate signature and issuer/audience in the Gateway and Inventory. Inventory revalidates current tenant/application access through an authenticated identity API before tenant-protected operations so revocation is effective before token expiry.
- Derive tenant scope from the verified token and persisted grant. Reject caller-supplied tenant/identity headers as authorization input.
- Backfill existing Inventory rows to a reserved legacy tenant and change product uniqueness to `(tenant_id, product_id)` after verification.

## Consequences

- Inventory remains the owner of its records; the user service owns the control plane. Service-to-service APIs are the boundary.
- Protected Inventory operations depend on identity authorization availability. They fail closed when that check fails; public non-sensitive behavior must be explicitly separated and documented.
- The first rollout has a compatibility period: legacy routes may keep their old login for unrelated modules, while tenant-owned Inventory routes require scoped access.
- The app-specific token continues to use the current JWT library/signing setup initially. Production OIDC, asymmetric key management, MFA, refresh/revocation strategy and secret rotation require a dedicated follow-up ADR.
- Existing global Inventory APIs and tests must be replaced or adapted to tenant-scoped APIs; preserving unfiltered list access would defeat tenant isolation.

## Rejected alternatives

- **Create an identity microservice now:** duplicates the current user-service and expands the migration surface before a tenant slice is proven.
- **Trust `X-Tenant-ID` from the browser:** caller-controlled headers can be changed and cannot establish membership or resource ownership.
- **Use tenant ID only at the Gateway:** direct service access and non-Gateway callers would bypass the isolation boundary.
- **Share the user database with Inventory:** breaks database-per-service ownership and creates cross-service coupling.
