# Known Issues (open)

| ID | Severity | Issue | Workaround / next step |
|---|---|---|---|
| K1 | High | No Payment service; payment and refund confirmations arrive only through `X-Service-Token` callbacks in order-service. | Phase 5 |
| K2 | High | 9 services (analytics, cart, crm, error-stack, loyalty, notification, legacy procurement, qa, stock) are not tenant-scoped. Tenant tokens are now refused by the gateway for them; platform tokens still see all data. Accounting admin is now tenant-scoped (V19). | per-service tenant slices (Phase 2/3) |
| K3 | High | Service-token code defaults (`local-dev-service-token-change-me`) remain in 9 classes; only compose fails closed. Running a jar without the env var still accepts the public value. | replace defaults with required properties + test-profile values |
| K4 | Medium | Checkout can now take a `storefront` slug and then writes the storefront's tenant/store, pricing only that tenant's products (2026-11-01). Remaining: no storefront UI sends it yet; stock is the legacy non-tenant service; `order-events` carry no tenant. | storefront cart/checkout UI; tenant in events |
| K5 | Low | cart- and loyalty-service send Kafka events on the request thread. Bounded this session: tests 500 ms; docker profile (`config-repo/*-docker.yml`) 5 s instead of 60 s. Not redeployed or verified live; a Kafka outage still adds up to 5 s per publishing request. | move to an outbox |
| K6 | — | **Resolved this session**: stock-service now compares the service token with `MessageDigest.isEqual` (test `internalEndpointsRequireTheExactServiceToken`). | — |
| K7 | Medium | `InventoryAnalyticsController` aggregates all tenants and checks `role.contains("ADMIN")`. Blocked for tenant tokens at the gateway only. | tenant-scope the query, require `analytics:read` |
| K8 | Medium | Gateway actuator exposes `refresh`/`flyway` in non-prod profile; not covered by gateway filters. | restrict exposure to health,info,prometheus |
| K9 | Medium | crm (8092) and procurement (8093) publish host ports while trusting role headers. | drop published ports |
| K10 | Medium | Single shared HS256 secret across 7 services; generic validation ignores issuer/audience. | asymmetric keys |
| K11 | Medium | delivery V1/V2 and inventory V4 migrations never applied to Postgres. | scratch-DB run on `localhost:15432` once Docker is up |
| K12 | Low | mfe-order / mfe-product hard-code `http://localhost:8080`. | read API base from runtime config |
| K13 | Low | k8s manifests exist for only 5 business services. | Phase 15 |
| K14 | Info | Authenticated live runs exist only for Accounting and read-only E-commerce Admin (seeded test tenants, `scripts/dev-seed`); other apps still have no tenant-grant login. | seed a test tenant + grant in a scratch DB |
| K15 | Info | Behaviour change from this session: tenant-scoped tokens get 403 on legacy routes (e.g. `GET /api/orders`, `/api/users` directory, `/api/access/**`, accounting, CRM, QA). Platform tokens unaffected. | intended; revisit per service as it becomes tenant-aware |
| K16 | Info | `docker compose up` now fails until `SERVICE_TO_SERVICE_TOKEN` is set in `.env` (the local `.env` does not have it). | add it (see DEPLOYMENT_READINESS) |
| K17 | — | **Resolved 2026-11-01**: user-service V14 set `ACCOUNTING` to ACTIVE (live `user_db` at V14). Remaining: the shared-library TS registry still lists ACCOUNTING as PLANNED (an ACTIVE entry needs a configured origin and a tenant login screen in mfe-accounting, which does not exist), and no tenant has an Accounting entitlement or grant yet. | add a tenant login to mfe-accounting; grant via Super Admin |
| K18 | Low | accounting-service: unknown ledger id → unhandled `IllegalArgumentException` (HTTP 500 instead of 404). | add a `@RestControllerAdvice` |
| K19 | — | **Resolved**: accounting-service redeployed 2026-11-01; live `accounting_db` at V19 (14 ledger rows → legacy tenant), service healthy. | — |
| K20 | — | **Resolved 2026-11-01**: accounting `SecurityConfig` permits `/error`; refusals keep their real status. | — |
| K21 | — | **Resolved 2026-11-01**: user-service V15 set ECOMMERCE_ADMIN to ACTIVE; live tenant login verified (12/12). Remaining: checkout still writes every order to the legacy tenant (K4), so tenant consoles stay empty until storefront tenant resolution exists. | — |
| K22 | — | **Resolved 2026-11-01**: checkout trusted a `userId` in the request body over the authenticated user (order placed as someone else). The verified `X-User-Id` now wins. | — |
| K23 | — | **Resolved 2026-11-01**: mfe-order showed a fabricated "PLACED" receipt and cleared the cart when checkout failed. It now shows the error and keeps the cart. | — |
| K24 | Low | mfe-order promo codes are hard-coded in the UI ("UNIVERSAL20", "SPRING2026") rather than validated by the pricing API before checkout. | expose a customer-facing pricing quote and validate codes there |
