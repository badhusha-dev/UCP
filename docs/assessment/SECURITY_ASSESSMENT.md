# Security Assessment

Recorded 2026-11-01 on `feature/ucp-completion-modernization`. This is a code-and-configuration review with tests. It is **not** a penetration test: no live probe of a running stack was made in this pass, and no external review has been done. Earlier findings are in [../completion/SECURITY_AUDIT.md](../completion/SECURITY_AUDIT.md) and are not repeated in full.

## Findings from this pass

| # | Severity | Finding | Evidence | Status |
|---|---|---|---|---|
| 1 | High | **Authorization rules and role data disagree.** The INVENTORY grant rule requires `inventory:read` and only `inventory:` permissions. No seeded role meets both conditions. V13 introduced `inventory:view` and cross-module permissions, so the rule and the role data no longer match. | `TenantAccessService.grantApplication`; role table in `ucp_scratch_phase1_user`; B-38 | Open. Decision needed (OPEN_ISSUES 1). |
| 2 | Medium | **The default catalog and AI chat are anonymous and cross tenants.** `product-service` permits all `/api/products/**` paths without a token. `GET /api/products` with no storefront and `POST /api/products/ai/chat` both read active products from every tenant, including name, price and stock. | `SecurityConfig` permitAll; `ProductService.chatWithAiAssistant` uses `findAllByActiveTrue()`. Gateway routing for these paths was not re-checked. | Open. Decision needed (OPEN_ISSUES 2). Scoped storefront catalogs (SF-01) are tenant-limited. |
| 3 | Medium | **The storefront slug is not trusted for tenant selection.** The browser sends only a slug; the server maps it to a tenant and returns 404 for unknown or inactive storefronts. | `PublicStorefrontService.tenantOf`, `InternalStorefrontController`, `StorefrontCatalogTest` | Verified by tests. Not exercised in a browser. |
| 4 | Medium | **Checkout takes the tenant from the storefront, not the request.** A product the storefront's tenant does not sell is refused, so an order cannot include another tenant's items. The authenticated `X-User-Id` wins over a body `userId`. | `OrderService.placeStorefrontOrder`; `StorefrontCheckoutTest` (6 tests) | Verified by tests. |
| 5 | Medium | **Login abuse controls.** Five failed sign-ins lock an account for 15 minutes (423). The gateway limits sign-ins to 20 per minute per address. Both are per instance, so the limit is not shared across replicas. | `LoginAttemptService`; `LoginRateLimitFilter`; tests | Partly. The per-instance limit is open (B-20). |
| 6 | Medium | **Credentials remain in history.** The literals were removed from tracked files, but they still exist in git history and in any local backup branch. Rotation has not been done. | BUGS_AND_TECHNICAL_DEBT B-06 to B-09 | Owner action (OPEN_ISSUES 11). |

## Carried forward from the earlier audit (still open)

- One HS256 secret is shared by seven services, and issuer and audience are not checked (B-05, K10). Key storage needs a decision.
- Nine services trust gateway headers without verifying the token themselves (B-02). The gateway refuses tenant tokens for them, but platform tokens still see all rows.
- The gateway actuator exposes `refresh` and `flyway` in the non-production profile (B-19).
- Logout does not revoke tokens (B-21). No refresh-token rotation or password reset exists.
- No MFA (F-07).

## Controls verified in this pass

- Service-token comparisons use `MessageDigest.isEqual` (accounting, stock, storefront lookup).
- The storefront lookup refuses requests without the exact service token and fails closed when none is configured.
- Postgres tenant isolation tests pass for accounting (5) and delivery (6). The inventory Postgres tests pass (29 in the module).

## Not verified

- Live authentication and authorization against a running stack.
- Container images and the Kubernetes network policies at runtime.
- Istio authorization policies (K25).
- Dependency vulnerability scanning: no scanner was run in this pass.
- The tenant-isolation rules in the nine unscoped services.
