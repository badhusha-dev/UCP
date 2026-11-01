# Incomplete Features

Verified against the source on 2026-11-01 (HEAD `881fe882` plus the uncommitted frontend fixes listed in [CHANGELOG_CURRENT.md](CHANGELOG_CURRENT.md)).
Statuses follow the six classes defined in [PROJECT_CURRENT_STATUS.md](PROJECT_CURRENT_STATUS.md#status-definitions).
A feature is listed as COMPLETED only where tests in this repository pass; none of the features below meets that bar.

Priorities: **P0** security, tenant isolation, data integrity, payment correctness, application-breaking. **P1** core business functionality. **P2** operational features and integrations. **P3** enhancements.

Known-issue IDs (`K#`) refer to [docs/completion/KNOWN_ISSUES.md](../completion/KNOWN_ISSUES.md). Security IDs (`S#`) refer to [docs/completion/SECURITY_AUDIT.md](../completion/SECURITY_AUDIT.md).

## Shared platform

| ID | Application/Module | Feature | Current Status | What's Missing | Dependencies | Priority |
|---|---|---|---|---|---|---|
| F-01 | Shared / Payments | Payment service (PSP adapter, webhooks, idempotency, refunds, reconciliation) | NOT IMPLEMENTED | No `payment-service` module. order-service exposes payment/refund callbacks guarded only by the shared `X-Service-Token` (K1). | PSP decision (BLOCKED on choice of provider), F-10 outbox | P0 |
| F-02 | Shared / Multi-tenancy | Tenant scoping in nine services: analytics, cart, crm, error-stack, loyalty, notification, legacy procurement, qa, stock | NOT IMPLEMENTED | No `tenant_id` columns or tenant filters. The gateway refuses tenant tokens for them (K2, S2 partly), but platform tokens still see all data. | Flyway `V<n+1>` migrations per service, F-04 | P0 |
| F-03 | Shared / Identity | Account lockout and login throttling | PARTIALLY COMPLETED | Lockout implemented on both login endpoints (V16, `LoginAttemptService`; 5 tests pass). Missing: gateway per-IP throttling, admin unlock endpoint, lockout notification. V16 not yet run on Postgres. | F-09 (Redis) for throttling | P0 |
| F-04 | Shared / Identity | Asymmetric JWT signing and issuer/audience validation | NOT IMPLEMENTED | One HS256 secret is shared by seven services; `validateToken` checks only signature and expiry (K10, S7). | Key management decision (BLOCKED) | P0 |
| F-05 | Shared / Identity | Refresh tokens | NOT IMPLEMENTED | Access tokens only; no refresh endpoint or refresh-token table (`grep refresh` returns nothing in `user-service`). | F-04 | P1 |
| F-06 | Shared / Identity | Password reset | NOT IMPLEMENTED | No reset token model, endpoint or email. | F-05 (for session handling), notification-service | P1 |
| F-07 | Shared / Identity | Multi-factor authentication | NOT IMPLEMENTED | No TOTP/MFA code anywhere in the repo. | F-04 | P2 |
| F-08 | Shared / Sessions | Logout and token revocation | PARTIALLY COMPLETED | Application-grant revocation works (`TenantAccessService.revokeApplication`, audited). Logout does not invalidate issued JWTs; there is no denylist. | F-05 or F-09 | P1 |
| F-09 | Shared / Infrastructure | Redis caching and distributed locking | NOT IMPLEMENTED | `spring-boot-starter-data-redis` is declared only in product-service and is never used (no `RedisTemplate`, `@Cacheable` or lock code). Redis is provisioned in compose. | None | P2 |
| F-10 | Shared / Events | Transactional outbox for all event producers | PARTIALLY COMPLETED | Outbox exists only in delivery-service (`DeliveryOutboxPublisher`). Cart, inventory, loyalty, order, stock and notification publish directly from the request thread (K5). | None | P1 |
| F-11 | Shared / Events | Kafka DLQ handling for all consumers | PARTIALLY COMPLETED | DLQ topic and handling exist in notification-service (`kubernetes/messaging/kafka-dlq.yml`). Six other consumers (accounting, analytics, crm, error-stack, product, notification's second listener) were not checked for retry/DLQ configuration. | None | P2 |
| F-12 | Shared / Service security | Istio AuthorizationPolicy for the ten services added in v2.0.0 | NOT IMPLEMENTED | The original five services have policies; the new ones do not (K25). | Mesh rollout | P2 |
| F-13 | Shared / Service security | Constant-time service-token comparison | COMPLETED | Every service-token check found by grep (user, order, inventory, product, stock, accounting) uses `MessageDigest.isEqual`. The accounting case was fixed in `bfdb9f2a` (B-03). Negative tests exist for stock and accounting; the full backend run passed. | None | P1 |
| F-14 | Shared / Operations | Container runtime verification | NOT VERIFIED | 46 compose services; no Docker daemon was available, so no image was built or started in this audit. README still claims "23/23 containers UP". | Docker host | P1 |
| F-15 | Shared / Operations | Kubernetes production overlay | NOT VERIFIED | Manifests build offline (`kubectl kustomize kubernetes`). `kubernetes/base/02-secrets.yml` is gitignored but referenced by `kustomization.yml`, so a fresh clone fails to build. Nothing applied to a cluster. | Secret-management decision (BLOCKED) | P1 |
| F-16 | Shared / Operations | Alertmanager Slack and PagerDuty routing | BLOCKED | Slack webhook URLs were replaced with `REPLACE_ME` in commit `c57de2be`. Alerts cannot reach Slack until real URLs are injected at deploy time. | Secret injection (BLOCKED) | P2 |
| F-17 | Shared / Gateway | Gateway actuator exposure | IMPLEMENTED BUT BROKEN | Non-prod profile exposes `refresh`, `flyway` and `metrics` (K8, S6). The file is in the nested `backend/config-repo`, which has its own uncommitted changes, so it was not edited. | Config-repo owner | P1 |
| F-18 | Shared / Testing | End-to-end business workflow tests | NOT VERIFIED | `scripts/smoke-test.sh` and `.ps1` need a running stack; not run. No automated cross-service workflow test exists in the repo. | F-14 | P1 |

## Applications

| ID | Application/Module | Feature | Current Status | What's Missing | Dependencies | Priority |
|---|---|---|---|---|---|---|
| A-01 | 1. Super Admin (`mfe-super-admin`, user-service `PlatformTenantController`) | Tenant creation, entitlements and application grants | PARTIALLY COMPLETED | Grants and revocation work in the API. The `mfe-super-admin` UI (16 KB `App.tsx`) has no tests. Tenant lifecycle, plans and limits are not implemented (gap doc Phase 12). | F-04 for tenant admin auth hardening | P1 |
| A-02 | 2. E-commerce Admin (`mfe-order`, `mfe-product`, `mfe-user`) | Admin order, catalog, pricing and user management | PARTIALLY COMPLETED | Customer, staff and report screens are missing (gap doc Phase 8). Tenant consoles show empty data until storefront tenant resolution exists (K4, K21). `mfe-user` had a missing `Button` import that would throw at render (fixed, see BUGS). | A-03 | P1 |
| A-03 | 3. Storefront (`shell-app`, `mfe-product` storefront, `mfe-cart`, checkout in `mfe-order`) | Tenant-aware storefront and checkout | PARTIALLY COMPLETED | Domain-to-tenant resolution missing (Phase 6). Checkout accepts a `storefront` slug, but no UI sends it (K4). Promo codes hard-coded in the UI and not validated by the API (K24). API base URLs hard-coded to `localhost:8080` (K12, S10). | F-02 (cart and order tenancy), F-01 | P1 |
| A-04 | 4. Inventory Management (`mfe-inventory`, `mfe-stock`, `inventory-service`, `stock-service`) | Atomic reservations, order-to-inventory switch, transfers, receiving | PARTIALLY COMPLETED | Reservation logic is not atomic with orders (Phase 4). Stock-service is not tenant-scoped (K2). Inventory migration V4 has never been applied to Postgres (K11). The DB-backed behavior was not verified in this audit. | F-02, F-10 | P1 |
| A-05 | 5. Accounting & Finance (`mfe-accounting`, `accounting-service`) | Tenant-scoped ledger, reversals, refunds, admin console | PARTIALLY COMPLETED | Tenant-scoped since V19 (K19). Missing: DB-level immutability of posted lines (Phase 9), payment and refund postings from a payment service (F-01), tenant login screen in `mfe-accounting` and no tenant has an Accounting grant yet (K17). Tenant-isolation Postgres test is skipped without `UCP_TEST_ACCOUNTING_POSTGRES_URL`. K18 fixed this session. | F-01, Postgres scratch DB | P0 |
| A-06 | 6. Delivery Management (`mfe-delivery`, `delivery-service`) | Delivery creation on fulfilment, OTP and status, outbox | PARTIALLY COMPLETED | Delivery is not created automatically on order fulfilment (Phase 7). Delivery migrations V1 and V2 have never been applied to Postgres (K11). Outbox is implemented here, unlike other services (F-10). | A-02 order fulfilment | P1 |
| A-07 | 7. Vendor / Restaurant | Restaurant order flow and vendor domain/portal | PARTIALLY COMPLETED | Restaurant order states exist in `OrderStateMachine` and `OrderSettingsService` (order-service). No vendor domain, no vendor portal and no kitchen screen (gap doc Phase 10). | A-03, A-02 | P2 |
| A-08 | 8. CRM (`mfe-crm`, `crm-service`) | Tenant-scoped customer records and history | PARTIALLY COMPLETED | `crm-service` has 46 mappings and a Kafka consumer, but no tenant scoping (K2) and the frontend has no tests. The `mfe-crm` frontend is a single 38 KB `App.tsx` plus `CrmRecordManager.tsx`. Host port removed this session (K9). | F-02 | P2 |
| A-09 | 9. Tester / Quality Center (`mfe-error-stack`, `shell-app` QualityCenterPage, `qa-service`) | Quality runs and error stack tracking | PARTIALLY COMPLETED | `qa-service` is platform-only; the tenant-or-platform decision is not made (Phase 13, BLOCKED on decision). Runner execution needs the repository mounted at `/workspace`, which the Kubernetes manifest does not provide (emptyDir). `mfe-error-stack` has no tests. | Product decision (BLOCKED), F-02 | P2 |
| A-10 | 10. Customer Support | Support tickets, agent console, customer-facing ticket view | NOT IMPLEMENTED | No support module, entity or screen. A search for ticket and helpdesk terms finds nothing in backend or frontend source. | Product scope decision (BLOCKED on scope), F-02, F-10 | P2 |

## Other modules outside the ten applications

| ID | Application/Module | Feature | Current Status | What's Missing | Dependencies | Priority |
|---|---|---|---|---|---|---|
| O-01 | Analytics (`analytics-service`, `mfe-analytics`) | Tenant-scoped analytics | IMPLEMENTED BUT BROKEN | `InventoryAnalyticsController` aggregates all tenants and authorises with `role.contains("ADMIN")` (K7, S5). Gateway blocks tenant tokens only. | F-02 | P0 |
| O-02 | Loyalty (`loyalty-service`, `mfe-loyalty`) | Loyalty points and admin | PARTIALLY COMPLETED | Not tenant-scoped (K2). crm-service called it on the wrong port (8086, now corrected to 8087 in config-repo). | F-02 | P2 |
| O-03 | Cart (`cart-service`, `mfe-cart`) | Tenant-aware cart | PARTIALLY COMPLETED | Not tenant-scoped (K2). Publishes Kafka events from the request thread (K5). Its test was not hermetic (Kafka) before this session. | F-02, F-10 | P1 |
| O-04 | Notification (`notification-service`) | Customer notifications from events | PARTIALLY COMPLETED | Not tenant-scoped (K2). Has DLQ handling. No delivery-status or retry UI. | F-02, F-11 | P2 |
| O-05 | Legacy (`backend/legacy`) | Legacy code | NOT VERIFIED | 43 files not in the parent POM. Not built, tested or deployed. Decide whether to delete or document. | Decision | P3 |
