# UCP SaaS Transformation — Repository Assessment

**Assessed:** 2026-10-28  
**Scope:** `D:/practical/ecommerce-platform` source tree; read-only archive inspection; baseline builds and tests.

## Executive summary

UCP is an existing Spring Boot microservice platform with reusable commerce features, a gateway, a shared user service, a React/Vite shell, independently buildable microfrontend workspaces, Flyway migrations, Docker Compose, Kubernetes, CI workflows and a Quality Center. The repository is not a greenfield project.

The requested SaaS boundary is not implemented yet. Users have one global role, JWTs do not carry tenant/application context, inventory records are globally keyed by product, and there are no tenant memberships, application entitlements or user application grants. The Inventory service also accepts permissive requests and relies on gateway-injected role headers for its admin checks. Its current integration tests exercise several API routes that do not exist.

The source tree and deployment paths are structurally usable. The first implementation should extend `user-service` as the identity/control plane and `inventory-service` as the owner of inventory data, then prove the Client A / Client B authorization scenario before adding the other SaaS applications.

## Repository and build layout

| Area | Observed state | Assessment |
| --- | --- | --- |
| Backend | 17 Maven modules under `backend/`; parent POM is `backend/pom.xml`; wrapper is `backend/mvnw` / `backend/mvnw.cmd`. | Existing module boundaries are reusable. Run Maven from `backend/`. |
| Frontend | One npm workspace under `frontend/`, with shared library, shell and 13 `mfe-*` workspaces. | Many MFE builds already exist, but they do not equal the ten requested product applications. |
| Federation | Vite federation remotes are defined in the shell. Remote URLs currently point to localhost ports. | Good local composition; runtime URL registry and per-environment origins are missing. |
| Frontend CI | Workflow matrix builds and publishes shell plus product, order and user only. | CI does not yet validate all workspaces or ten application artifacts. |
| Backend CI | Workflow matrix covers seven of 17 modules. | CI coverage does not match the full backend reactor. |
| Compose | `docker compose config --quiet` passes; a path check found every configured build context and Dockerfile. | Current local topology parses. Compose remains a combined deployment rather than ten app deployments. |
| Kubernetes | Service, ingress, database and infrastructure manifests exist. | SaaS application routing, tenant-aware settings and per-app deployment values are not present. |
| Archives | No ZIP is stored in the project root. Four neighboring release ZIPs were inspected without extracting or modifying them; all entries had relative, non-traversing paths. | No malformed archive paths required normalization. Original archives were preserved. |
| Git boundary | `ecommerce-platform` has no local `.git` directory; `git rev-parse` resolves to `D:/practical`. | The project folder is not its own Git checkout in this workspace. No Git operations should be scoped as if it were a separate repository. |

## Existing implementation inventory

| Capability | Status | Evidence and gap |
| --- | --- | --- |
| Shared identity | Partial | `user-service` supports login/register and bcrypt passwords. Identity is not separated from the single global `users.role` authorization field. |
| JWT | Partial | HS256 tokens are signed and expiration is checked. Tokens have no configured issuer/audience, use a shared symmetric secret and have no persisted revocation check. |
| Gateway header handling | Partial | Gateway validates bearer JWTs, removes caller-supplied `X-User-*` headers and injects values from verified claims. Downstream Inventory does not independently validate that context. |
| RBAC | Partial | Global role/permission tables and a Super Admin role exist. Tenant-scoped memberships, application grants, entitlements and resource scopes do not. |
| Super Admin | Partial | Bootstrap and global role administration exist. There is no client lifecycle, application registry, entitlement or client configuration workflow. |
| Inventory | Partial | Inventory, reservation, movement and alert entities, repositories and business methods exist. `inventory_item.product_id` is globally unique; there is no tenant ownership. |
| Inventory API | Incomplete | `InventoryAdminController` exposes catalog metadata under `/api/inventory/admin/**`. Baseline tests also expect availability, reservation and stock-adjustment routes which currently return `404`. |
| Independent Inventory login | Missing | `mfe-inventory` builds separately and has an Nginx Dockerfile, but its app renders the catalog directly and has no application-scoped login or tenant selection. |
| Ten product applications | Partial | 13 domain-oriented MFE workspaces plus a shell exist. Dedicated Super Admin, Delivery, Vendor/Restaurant and Support apps do not exist as separate frontends. Existing MFE workspaces are not yet tenant/application gated. |
| Quality Center | Partial | `qa-service`, an admin page, catalog, run history and reports exist. The current work should reuse this rather than add a second tester app implementation. |
| Restaurant domain | Missing/undocumented | Existing product/inventory/order services are retail-shaped; no restaurant menu, modifier, branch-hours or kitchen workflow was found in the inspected service boundaries. |
| Configurable URLs | Missing | Shell remote URLs and frontend API base URLs use localhost defaults. No approved application URL registry or origin allowlist was found. |
| Tenant data isolation | Missing | No tenant model or tenant-scoped database queries/migrations were found in user, gateway or inventory source. |

Documentation is extensive, but this assessment treats implementation as present only where corresponding code and runtime/test evidence were found.

## Security findings relevant to the first slice

1. `JwtAuthenticationFilter` in the Gateway verifies the signature and expiration and sanitizes user headers before forwarding. It does not enforce a tenant, application code, issuer or audience.
2. Inventory `SecurityConfig` permits all requests. `InventoryAdminController` authorizes by `X-User-Role`; direct service calls can bypass gateway header sanitization. A role header is not an acceptable service authorization boundary.
3. `User` has a single global role. A valid identity therefore does not currently mean a tenant- and application-scoped identity.
4. Existing admin routes operate over globally keyed inventory data. Adding a frontend permission check alone would not isolate tenants.
5. Inventory public route classification in the gateway broadly permits GETs under `/api/inventory`; newly tenant-scoped routes must be explicitly excluded from the public path and checked again inside Inventory.

## Reproducible baseline

Run from the indicated directories:

```powershell
# From D:\practical\ecommerce-platform
docker compose config --quiet

# From D:\practical\ecommerce-platform\backend
.\mvnw.cmd -B -pl user-service,inventory-service,api-gateway -am test
.\mvnw.cmd -B -pl api-gateway test

# From D:\practical\ecommerce-platform\frontend
npm run build --workspace=@ecommerce/shared-library
npm run build --workspace=mfe-inventory
```

Observed on 2026-10-28:

- Compose configuration and configured context/Dockerfile paths: pass.
- `mfe-inventory` plus shared-library build: pass (Vite emits deprecation warnings for its current React/esbuild integration).
- User service suites: 12 tests pass.
- Gateway suites: 12 tests pass.
- Inventory integration suite: 7 tests run, 6 fail with `404`; only the CSV export test passes. The failing tests expect missing availability, reservation and adjustment endpoints. Inventory also logs Kafka connection warnings when its test context starts without a broker.
- The combined Maven command stops at Inventory, so Gateway was skipped in that combined invocation; Gateway was then run separately and passed.

This is a focused baseline, not a full reactor or all-frontend verification. Existing Quality Center test outcomes from the prior local implementation are documented separately; they are not a substitute for these baseline checks.

## First-slice data ownership and migration constraints

- `user-service` owns users, tenants, tenant memberships, application registry, tenant entitlements, user application grants and access audit events in its own database.
- `inventory-service` owns inventory rows and must store the tenant identifier locally. It must not query the user database directly.
- Tenant IDs are stable UUIDs shared as identifiers between services; no cross-database foreign key is introduced.
- Existing inventory rows need a deterministic `legacy` tenant backfill before tenant-scoped constraints are enforced. Product uniqueness becomes tenant plus product, not global product alone.
- Expand-and-contract migrations are required. Keep legacy data and applied migrations intact; do not rewrite historical Flyway migrations or remove persistent volumes.

## Readiness conclusion

The project is ready for an incremental tenant-aware Inventory vertical slice. It is not ready for broad tenant feature rollout: resource isolation, application-scoped authentication, direct-service authorization, the inventory API baseline and independent app access policies must be established first.

## First-slice implementation status

After the baseline above, the first slice added tenant and app access persistence to `user-service`, tenant ownership and scoped queries to `inventory-service`, scoped login and a current-grant check, gateway enforcement, and standalone Super Admin and Inventory frontends. The original baseline above remains a pre-change record.

The first slice now has a PostgreSQL-backed user access scenario and PostgreSQL-backed Inventory migration/API tests. The initial implementation still covers only Super Admin and Inventory workflows. The state of the later application boundaries and non-Inventory tenant migrations remains as assessed above.
