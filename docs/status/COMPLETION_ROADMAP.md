# Completion Roadmap

Ordered by priority, then by dependency. Items reference IDs in [INCOMPLETE_FEATURES.md](INCOMPLETE_FEATURES.md) (`F-`, `A-`, `O-`) and [BUGS_AND_TECHNICAL_DEBT.md](BUGS_AND_TECHNICAL_DEBT.md) (`B-`, `TD-`).
No completion percentage is given. Phases are sequenced by risk, not by effort.

## Definition of Done (applies to every item)

1. Code merged to `main` with no failing test in the affected modules.
2. New behaviour has at least one regression test that fails before the change and passes after.
3. Any schema change is a new Flyway migration. Applied migrations are never edited.
4. Tenant-scoped data has a cross-tenant test (tenant A cannot read or write tenant B).
5. Security-relevant changes have a negative test (missing, wrong or forged credential refused).
6. The change is recorded in `docs/status/CHANGELOG_CURRENT.md` and the feature's row in `INCOMPLETE_FEATURES.md` is updated.
7. Frontend changes pass `tsc --noEmit` for the affected workspace and `npm run build`.

## Phase 0 — Security, tenancy and data integrity (P0)

Must complete before any real customer data or payments.

| Item | Features | Depends on | Acceptance criteria | Required tests |
|---|---|---|---|---|
| 0.1 Rotate and remove committed credentials | B-06, B-07, B-08, B-09 | None | No literal password or token in tracked files at HEAD. Rotated values injected from Secrets or `.env` with `:?` required syntax. | `git grep` for the literals returns nothing; compose config fails without the variable. |
| 0.2 Constant-time service-token comparison in accounting | B-03, F-13 | None | **Done this audit (uncommitted).** `InternalAccountingController` uses `MessageDigest.isEqual`. | Wrong-token test added; accounting module passes. |
| 0.3 Analytics tenant scope and exact role | B-04, O-01, B-33 | 0.2 | Only platform roles reach the aggregate. Tenant tokens are refused in the service, not only at the gateway. | Tenant token and `DELIVERY_ADMIN` token both get 403. |
| 0.4 Tenant scoping for nine services (one slice per service) | F-02, B-02, B-31 | 0.3 | Each service has `tenant_id NOT NULL DEFAULT 'legacy'`, every query filters by tenant, and cross-tenant reads return 404. | Per-service cross-tenant test; migration test on H2 and on a scratch Postgres. Order: accounting done → crm → loyalty → cart → stock → notification → qa → error-stack → legacy procurement. |
| 0.5 Login lockout and rate limiting | F-03, B-20 | None | N failed logins lock the account for a period; gateway throttles per IP. | Lockout after N failures; unlock after timeout; 429 at the gateway limit. |
| 0.6 Asymmetric JWT with issuer and audience | F-04, B-05 | Key decision | Only user-service holds the private key. Each service checks `iss` and `aud`. | Token signed by another key refused; wrong audience refused. |
| 0.7 Accounting immutability | B-29, A-05 | None | DB trigger refuses UPDATE and DELETE on posted lines. | Trigger test on scratch Postgres. |
| 0.8 Payment service | F-01, B-01 | PSP decision, 1.1 | PSP reference only (no card data); signed webhook verification; idempotency key on every write; refund and settlement reconciliation feeds accounting. | Webhook signature test; duplicate webhook test; refund reconciliation test. |

## Phase 1 — Core business functionality (P1)

Required for a usable MVP.

| Item | Features | Depends on | Acceptance criteria | Required tests |
|---|---|---|---|---|
| 1.1 Frontend typecheck clean | TD-01, TD-02, TD-03 | None | **Done 2026-11-01.** `tsc --noEmit` passes in all 16 workspaces; CI runs it. | CI job. |
| 1.2 Test coverage for each application | TD-04, A-01…A-10 | 1.1 | Each application has route-level smoke tests. | `npm test` in every workspace. |
| 1.3 Outbox for all producers | F-10, B-16, B-34 | None | Cart, inventory, loyalty, order, stock and notification write events in the same transaction as the state change. | Test that an event is written atomically; test that a publisher retry is idempotent. |
| 1.4 Logout and token revocation | F-08, B-21 | 0.6 or Redis | Logout invalidates the session; revoked tokens are refused by the gateway. | Token refused after logout. |
| 1.5 Refresh tokens | F-05 | 0.6 | Short access tokens; refresh rotation; reuse detection. | Refresh rotation test; reused refresh token refused. |
| 1.6 Password reset | F-06 | 1.5, notification | Reset token single use and time limited; no account enumeration. | Expired, reused and unknown-email tests. |
| 1.7 Atomic inventory reservations | A-04 | 0.4 (inventory and stock) | Reservation and order creation commit together or not at all. | Concurrent order test on Postgres. |
| 1.8 Delivery created on fulfilment | A-06 | 1.3 | Fulfilling an order creates a delivery and sends a customer notification. | End-to-end test: order → delivery → notification. |
| 1.9 Storefront tenant resolution | A-03, B-25 | 0.4 (cart and order) | Domain or slug resolves to a tenant; checkout writes that tenant. | Cross-tenant checkout test. |
| 1.10 Apply pending migrations on Postgres | B-28 | Scratch Postgres | Delivery V1–V2 and inventory V4 applied; `flyway_schema_history` matches the repository. | `flyway validate` against a scratch database. |
| 1.11 Container runtime verification | F-14 | Docker host | All 46 compose services start and report healthy. | `docker compose up -d` plus `scripts/health-check.sh`. |
| 1.12 Gateway actuator restriction | F-17, B-19 | Config-repo decision | Non-prod gateway exposes only `health,info,prometheus`. | Actuator `refresh` returns 404. |
| 1.13 Secret-free Kubernetes manifests | F-15, B-18 | 0.1 | `kubectl kustomize` builds from a fresh clone with documented secret creation. | Fresh clone build. |

## Phase 2 — Operational features and integrations (P2)

| Item | Features | Depends on | Acceptance criteria | Required tests |
|---|---|---|---|---|
| 2.1 Customer support | A-10 | Scope decision | Tickets with states, assignment and customer view. | Ticket lifecycle; tenant isolation. |
| 2.2 Vendor and restaurant portal | A-07 | 1.9 | Vendor domain, portal and kitchen screen. | Order → kitchen → pickup flow. |
| 2.3 CRM tenant scope and history | A-08 | 0.4 | Customer history visible per tenant. | Cross-tenant test; event consumer test. |
| 2.4 Quality Center scope decision | A-09 | Product decision | Platform-only or tenant-scoped, documented; runner paths mounted. | Runner execution test on a host with the repo mounted. |
| 2.5 Super Admin plans and limits | A-01 | 0.6 | Tenant plans and limits enforced; lifecycle states. | Limit enforcement test. |
| 2.6 Redis caching and locking | F-09, B-32 | None | Redis used for at least one cache and one lock, behind an interface; or the dependency removed. | Lock contention test. |
| 2.7 MFA | F-07 | 0.6 | TOTP enrolment and verification. | Wrong code refused; replay refused. |
| 2.8 Istio policies for new services | F-12, B-24 | Mesh rollout | Each new service has an AuthorizationPolicy listing its callers. | Denied call from unlisted principal. |
| 2.9 Alert secret injection | F-16, B-17 | 0.1 | Alertmanager reads webhook and routing keys from Secrets. | Alert delivered to a test channel. |
| 2.10 DLQ for every consumer | F-11 | None | Each consumer has retry and dead-letter handling. | Poison message goes to DLQ. |
| 2.11 Frontend API base from config | B-15 | None | No `localhost:8080` in source. | `git grep localhost:8080` returns only tests. |
| 2.12 Server-side promo validation | B-14 | Pricing quote API | Promo codes validated by the pricing API. | Invalid code refused; valid code priced. |
| 2.13 Storefront UI sends tenant | B-25, K4 | 1.9 | Storefront flows include the tenant. | Browser test. |

## Phase 3 — Enhancements and cleanup (P3)

| Item | Features | Acceptance criteria |
|---|---|---|
| 3.1 Remove or archive legacy code | O-05, TD-05 | `backend/legacy` deleted or archived with a note. |
| 3.2 Repository hygiene | TD-10, TD-11 | `info/` resolved; `backup-pre-scrub` deleted after rotation. |
| 3.3 Reproducible frontend images | TD-08 | `Dockerfile.mfe` uses `npm ci` with a committed Linux-compatible lockfile. |
| 3.4 Documentation refresh | TD-06, TD-09 | README reflects 46 compose services; superseded reports marked. |
| 3.5 Performance baselines | B-33 | Load-test results recorded in `docs/status`. |

## Recommended execution order

1. 0.1 and 0.2 together (same day; no dependencies; removes live exposure).
2. 1.1 (typecheck), so later frontend work runs against a clean gate.
3. 0.3, then the tenant slices in 0.4.
4. 0.5 and 0.6 (identity hardening; 0.6 needs a key decision first).
5. 0.7, then 1.10 (both need a scratch Postgres).
6. 1.3 outbox, then 1.7 and 1.8 (they depend on it).
7. 0.8 payment, once the PSP is chosen.
8. Phase 1 remainder, then Phase 2 in the order listed.

## Decisions required (BLOCKED)

- PSP provider (0.8).
- Asymmetric key management (0.6).
- Customer support scope (2.1).
- Quality Center tenant scope (2.4).
- Secret-management approach for Kubernetes (1.13).
