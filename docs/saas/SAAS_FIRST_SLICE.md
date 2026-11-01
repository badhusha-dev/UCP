# UCP SaaS First Slice — Local Setup and Status

This first vertical slice extends the existing platform. It proves tenant creation, an Inventory entitlement, tenant membership and role grant, app-specific login, tenant-scoped Inventory APIs, immediate grant revocation and audit history. The standalone Super Admin and Inventory entry points are implemented; this does not complete the other eight SaaS applications or broader tenant migration.

## Architecture

- `user-service` is the identity and access control plane. It owns tenant, application, entitlement, membership, grant, role assignment and access audit records.
- `inventory-service` owns inventory data and filters every query and mutation by the tenant ID from the verified app token.
- The Gateway removes caller-supplied user, tenant, application and service-token headers. It validates Inventory tokens, enforces platform routing policy, and blocks the internal authorization endpoint from public routing.
- A tenant application role cannot inherit platform-wide Super Admin authority; Inventory grants are limited to Inventory permission codes.
- Inventory revalidates current access with the user service before every protected API request. It fails closed if identity authorization is unavailable.
- Existing rows map to the reserved `legacy` tenant `00000000-0000-4000-8000-000000000001`.

Diagram source: [saas-first-slice.mmd](../diagrams/saas-first-slice.mmd).

Detailed contracts are in [API_CONTRACTS.md](API_CONTRACTS.md); migration recovery is in [ROLLBACK_AND_RECOVERY.md](ROLLBACK_AND_RECOVERY.md).

## Local build and start

Run these from PowerShell. Compose requires a running Docker Engine. The Compose topology may start additional services required by the existing Gateway routes.

```powershell
cd D:\practical\ecommerce-platform\frontend
npm ci
npm run build --workspace=@ecommerce/shared-library
npm run build --workspace=mfe-inventory
npm run build --workspace=mfe-super-admin

cd D:\practical\ecommerce-platform\backend
.\mvnw.cmd -B -pl user-service,inventory-service,api-gateway -am -DskipTests package

cd D:\practical\ecommerce-platform
docker compose config --quiet
docker compose up --build api-gateway inventory-service mfe-inventory mfe-super-admin
```

The apps are available at `http://localhost:3013` (Super Admin) and `http://localhost:3004` (Inventory); the API Gateway is `http://localhost:8080`.

The local Compose deployment for this slice is currently running. On 2026-10-28, both app entry points and the Gateway health endpoint returned HTTP 200. The Super Admin app login was also verified from its browser origin after adding the missing Super Admin, Stock and Procurement origins to the Gateway CORS allowlist. The local Super Admin UI opens on the seeded legacy tenant; no client workspace was created as part of the deployment check.

Set unique `SUPER_ADMIN_EMAIL`, `SUPER_ADMIN_PASSWORD`, `JWT_SECRET`, and `SERVICE_TO_SERVICE_TOKEN` values in the local environment before using shared or production-like environments. The Compose fallback token is for isolated local development only. The Super Admin adds an existing employee identity to a client; create that identity through the existing user onboarding flow first.

## Test commands

```powershell
cd D:\practical\ecommerce-platform\backend
.\mvnw.cmd -B -pl user-service test
.\mvnw.cmd -B -pl inventory-service -am test
.\mvnw.cmd -B -pl api-gateway test
```

The user-service integration test starts PostgreSQL through Testcontainers when Docker is available, or uses the isolated database configured with `UCP_TEST_POSTGRES_*`. Inventory tests use PostgreSQL migrations and data when `UCP_TEST_INVENTORY_POSTGRES_*` is set; otherwise they use the H2 test profile.

## Verification completed

- User service: 13 tests passed against an isolated PostgreSQL 15 database; Flyway V1–V6 and Hibernate schema validation passed, including tenant creation, entitlement, scoped grants/login, cross-tenant denial, revocation, audit and platform Super Admin checks.
- Inventory service: 9 tests passed against an isolated PostgreSQL 15 database; Flyway V1–V3 and Hibernate schema validation passed, including tenant isolation, spoofed tenant header rejection, role checks and reservation lifecycle.
- API Gateway: 15 tests passed, including tenant Super Admin boundary checks, hidden internal authorization routing and platform CORS preflight.
- Frontend install dry run passed; shared library, Inventory and Super Admin production builds passed.
- Backend package build passed for `user-service`, `inventory-service` and `api-gateway`; `docker compose config --quiet` passed.
- Docker Compose runtime check: required service containers were up; Inventory and its dependencies with health checks were healthy. Super Admin (`3013`), Inventory (`3004`) and Gateway health (`8080/actuator/health`) returned HTTP 200.
- Browser sign-in to the Super Admin portal succeeded after correcting the Gateway CORS allowlist for local app origins.

## Current implementation boundary

- Implemented: user/client/application access tables, Inventory scoped login, Super Admin tenant and grant console, Inventory service tenant data boundaries, authenticated current-grant check, revocation behavior, audit, independent app builds, CI entries and PostgreSQL migration tests.
- Still planned: standalone applications for the remaining domains, per-tenant configuration publishing and version rollback, OIDC/MFA/key rotation, tenant migrations for other services, rate limiting, production TLS/ingress, full smoke/E2E workflows and production operations.

This is a locally validated first slice, not a completed production SaaS transformation. Keep the database volumes when stopping the local stack; use `docker compose stop` to stop it without removing persisted data.
