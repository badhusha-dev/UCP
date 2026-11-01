# Security Audit

Date 2026-11-01. Scope: api-gateway, all backend services' security configuration and header trust, secrets in config and
compose, CI. Method: code reading + gateway tests. No penetration test or live probe was run (Docker daemon was off).
**No PCI or compliance claim is made.**

## Trust model as found

- The gateway (`JwtAuthenticationFilter`) is the only authenticator for most services. It verifies an HS256 JWT signed with
  the shared `JWT_SECRET`, strips client `X-User-*`, `X-Tenant-ID`, `X-Application-Code`, `X-Service-Token`, then injects
  values from the token claims.
- Services that **verify the JWT themselves**: user-service, product-service (`/api/products/admin/**`), order-service
  (`/api/orders/admin/**`), inventory-service, delivery-service, notification-service.
- Services that **trust gateway headers** (Spring Security `permitAll` + role header checks): accounting, analytics, cart,
  crm, error-stack, loyalty, procurement (legacy API), stock, qa. They are safe only while unreachable except via the gateway.
- Service-to-service calls use a shared `X-Service-Token` (`SERVICE_TO_SERVICE_TOKEN`).

## Findings

| # | Severity | Finding | Evidence | Status |
|---|---|---|---|---|
| S1 | High | **Open-endpoint substring bypass**: the gateway treated any path *containing* `swagger`, `api-docs`, `/actuator`, `/fallback` as public, so e.g. `GET /api/orders/admin/orders/swagger` skipped authentication and was forwarded (headers stripped). Any downstream handler that is safe without headers, or a path variable equal to those words, was exposed. | `JwtAuthenticationFilter` `OPEN_API_ENDPOINTS.stream().anyMatch(path::contains)` | **Fixed** — exact/prefix matching + per-service api-docs regex. Test: `GatewayTenantBoundaryTest.substringOfOpenEndpointDoesNotBypassAuthentication` |
| S2 | Critical (multi-tenant) | **Tenant tokens reached platform-wide services**: a tenant administrator's application token (`role=ADMIN`, tenant-scoped) was forwarded with `X-User-Role: ADMIN` to services that have no tenant column and authorise by that header (legacy `/api/orders`, `/api/users` directory, `/api/access` global role editing, accounting, CRM, analytics, QA, loyalty/stock/cart admin, legacy procurement). With the matching permission in the token, one tenant could read or change every tenant's data. | header trust table above; `migrations-with-tenant_id=0` for those services | **Fixed at gateway** — `TenantRoutePolicy` allow-list. Platform tokens unchanged. Tests: `GatewayTenantBoundaryTest` (3 cases) |
| S3 | High | **Well-known service token**: every service defaults `SERVICE_TO_SERVICE_TOKEN` to `local-dev-service-token-change-me`, docker-compose passed the same default, and the local `.env` does not set it — so the running stack accepts the public value on internal callbacks (payment/refund confirmation, stock receive, supplier payables). | 9 `@Value` defaults; 10 compose lines | **Partly fixed** — compose now requires the variable (fail closed), `.env.example` documents it. Code defaults remain (tests rely on them); see KNOWN_ISSUES K3 |
| S4 | Medium | Service-token comparison was not constant-time in stock-service (`String.equals`). | `StockInternalController`, `StockController` | **Fixed** — `MessageDigest.isEqual`; test `internalEndpointsRequireTheExactServiceToken` |
| S5 | Medium | `/api/products/admin/analytics/inventory` (product-service) passes the catalog tenant filter but aggregates all tenants and authorises with `role.contains("ADMIN")` (matches `DELIVERY_ADMIN`, `SUPER_ADMIN`…). | `InventoryAnalyticsController` | Mitigated (tenant tokens blocked at gateway); code fix open |
| S6 | Medium | Gateway actuator exposes `refresh`, `flyway`, `metrics` etc. in non-prod profile (config-repo `application.yml`); gateway `GlobalFilter`s do not apply to the gateway's own actuator. Bound to 127.0.0.1 in compose. | config-repo/application.yml | Open (prod profile limits exposure) |
| S7 | Medium | Generic token validation (`validateToken`) checks signature and expiry only, not issuer/audience. All tokens share one HS256 secret across 7 services, so any service holding the secret can mint admin tokens. | `JwtUtil` | Open — recommend asymmetric signing (user-service private key, others public) |
| S8 | Medium | Plain DB passwords (`admin_password`) committed in config-repo service YAMLs; prod profile defaults Vault token to `root`. | config-repo/*.yml, application-prod.yml | Open (dev defaults; prod must override) |
| S9 | Medium | crm-service (8092) and procurement-service (8093) publish ports to the host (127.0.0.1) while trusting `X-User-Role` — a local process can call them directly with a forged role. | docker-compose.yml | Open — remove published ports or add JWT verification |
| S10 | Low | Frontends hard-code `http://localhost:8080` API bases (mfe-order, mfe-product) — not deployable behind a real origin; CORS lists localhost only. | `const API = 'http://localhost:8080/...'` | Open |
| S11 | Info | No MFA, refresh-token rotation or login lockout found in user-service. | grep for lockout/mfa/totp/refresh_token: no hits | Open (Phase 2 backlog) |
| S12 | Info | k8s NetworkPolicies exist (default deny, gateway→services) but only 5 services have k8s manifests. | kubernetes/security/network-policies.yml | Open |

## Header spoofing — verified
`GatewayHeaderSanitizationTest` (unit test of the real filter): for an authenticated request, spoofed `X-Tenant-ID`
(both spellings), `X-User-Id`, `X-User-Role`, `X-User-Permissions`, `X-Application-Code` are replaced by token values and
`X-Service-Token` is removed; for an anonymous public request all eight headers are removed.
The headers services read were enumerated by grep (`X-User-Role`, `X-User-Id`, `X-User-Email`, `X-User-Permissions`,
`X-User-Roles`, `X-Tenant-Id`, `X-Service-Token`, `X-Application-Code`, `X-Correlation-Id`, `X-Cart-Session`) — every
identity/tenant header is covered.
