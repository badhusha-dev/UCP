# Advanced Feature Catalog

Prepared 2026-11-01 against `main` at `5a2acc0e` plus the account-lockout change described in [§9](#9-implementation-record).
Current-state facts come from the audit in [docs/status/](../status/PROJECT_CURRENT_STATUS.md) and from source inspection for this catalog. Phasing is in [FEATURE_EXPANSION_ROADMAP.md](FEATURE_EXPANSION_ROADMAP.md).

**Legend**

- **Type**: `EXTEND` builds on code that exists today (named in the entry). `NEW` has no implementation in the repository.
- **Priority**: P0 critical foundation · P1 core business capability · P2 advanced capability · P3 future enhancement.
- **Complexity**: S (under 1 week) · M (1–3 weeks) · L (3–6 weeks) · XL (over 6 weeks), for one experienced developer, including tests.
- **Approved**: P0 and P1 entries. They carry the full architecture rows required by Step 5 (owning service, tenant isolation, failure handling, observability, testing). P2 and P3 entries name the owning service only; their architecture is settled when they are scheduled.

## Contents

1. [Existing functionality baseline](#1-existing-functionality-baseline)
2. [Competitor capability comparison](#2-competitor-capability-comparison)
3. [Feature gap analysis](#3-feature-gap-analysis)
4. [Proposal summary](#4-proposal-summary)
5. [Technical architecture recommendations](#5-technical-architecture-recommendations)
6. [Platform-wide features](#6-platform-wide-features)
7. [Application features](#7-application-features)
8. [Deferred and rejected ideas](#8-deferred-and-rejected-ideas)
9. [Implementation record](#9-implementation-record)

---

## 1. Existing functionality baseline

What is in the code today. Nothing in this section is a proposal. Statuses are from [PROJECT_CURRENT_STATUS.md](../status/PROJECT_CURRENT_STATUS.md).

| Application | Existing capability (source) | Audit status |
|---|---|---|
| Super Admin | Tenants, application entitlements, user-to-application grants with audited revoke (`user-service` `PlatformTenantController`, `TenantAccessService`); `mfe-super-admin` UI | PARTIALLY COMPLETED |
| E-commerce Admin | Order administration and state machine, returns and RMA (`order-service`); catalog, variants, categories, pricing and promotions, storefront studio (`product-service`); role and permission editor (`mfe-user`); CSV import pre-check (`mfe-product` tests) | PARTIALLY COMPLETED |
| Storefront | Shell and storefront screens; cart with abandoned-cart reminders (`cart-service`); wishlist, recently viewed, product reviews (`product-service`); coupons and checkout (`order-service`); loyalty accounts and tier rules (`loyalty-service`) | PARTIALLY COMPLETED |
| Inventory | Warehouses, locations, warehouse stock, transfers, adjustments, cost layers, reservations, movements, back-in-stock alerts (`inventory-service` `OpsEntities`, `stock-service`); suppliers, purchase orders and receipts (`procurement-service`) | PARTIALLY COMPLETED |
| Accounting | Ledger, journals with draft approval, reversal by compensation, invoices, refunds, period close, bank reconciliation, supplier payables, audit log; tenant-scoped since V19 (`accounting-service`) | PARTIALLY COMPLETED |
| Delivery | Deliveries with OTP proof codes, status sync to orders, transactional outbox (`delivery-service`); recipient tracking permissions (`user-service` V8) | PARTIALLY COMPLETED |
| Vendor / Restaurant | Restaurant order states and settings (`order-service` `OrderStateMachine`, `OrderSettingsService`); suppliers exist in procurement but there is no vendor marketplace | PARTIALLY COMPLETED |
| CRM | Customer records API (46 mappings) and a Kafka consumer (`crm-service`); segment-related code in `crm-service`, `mfe-crm` and `product-service` (not verified as rule-based segmentation) | PARTIALLY COMPLETED |
| Tester / Quality Center | Quality runs that execute Maven and Node (`qa-service`); error groups and records with ingest API keys and rate limiting (`error-stack-service`); Quality Center page in `shell-app` | PARTIALLY COMPLETED |
| Customer Support | Nothing | NOT IMPLEMENTED |
| Platform | Gateway JWT validation with tenant allow-list; constant-time service token; notifications with DLQ; Kafka in 7 producers and 6 consumers; outbox in delivery only; Prometheus and Zipkin configuration; springdoc annotations; an "Analytics Copilot" panel in `mfe-analytics` and `mfe-order` (front-end text only; no AI backend); **account lockout (this change)** | Mixed; see the status report |

---

## 2. Competitor capability comparison

Purpose: find capabilities merchants expect, not to copy any product. Entries describe what each product family publicly documents as available to a merchant, as known to the author (knowledge to mid-2026). They were not re-checked online for this catalog, and editions and plans differ. "Ext." means available through the vendor's app or extension marketplace. A dash means not native, or not assessed with confidence.

| Capability | Shopify | WooCommerce | Adobe Commerce / Magento | Odoo | Zoho (Commerce, Inventory, Books, Desk) | Salesforce (Commerce, Service Cloud) | **UCP today** |
|---|---|---|---|---|---|---|---|
| Catalog with variants | Native | Native | Native | Native | Native | Native | Partial |
| Discounts and promo codes | Native | Native | Native | Native | Native | Native | Partial (codes hard-coded in UI, B-14) |
| Gift cards | Native | Ext. | Native (Commerce edition) | Native | — | — | None |
| Multi-currency storefront | Native | Ext. | Native | Native | — | Native | None |
| Shipping zones and carrier rates | Native | Native | Native | Native | Native | Native | None |
| Multi-location inventory | Native | Ext. | Native (MSI) | Native | Native | — | Partial (warehouses exist) |
| General-ledger accounting | Ext. | Ext. | Ext. | Native | Native | Ext. | Partial (tenant-scoped ledger) |
| CRM and segmentation | Partial | Partial | Native (Commerce edition) | Native | Native | Native | Partial |
| Help-desk ticketing | Partial | Ext. | Ext. | Native | Native | Native | None |
| Multi-vendor marketplace | Ext. | Ext. | Ext. | Ext. | — | Ext. | None |
| Restaurant POS and kitchen | Partial | Ext. | Ext. | Native | — | — | Partial (order states) |
| Workflow automation and approvals | Native | Ext. | Ext. | Native | Native | Native | Partial (journal approval only) |
| Outbound webhooks | Native | Native | — | — | — | Native | None |
| App marketplace | Native | Native | Native | Native | Native | Native | None |
| Subscription billing to end customers | Ext. | Ext. | Ext. | Native | Native | Native | None |
| Staff MFA | Native | Ext. | Native | Native | Native | Native | None |
| AI assistant for merchants | Native | Ext. | Native | Partial | Native | Native | Partial (UI text only) |

**Positioning.** The listed products are either single-merchant platforms (WooCommerce, Magento) or SaaS where each customer runs one store or organisation. UCP's distinctive shape is one multi-tenant platform running ten cooperating applications, including restaurants, vendors, delivery and support. The largest competitive gaps are payments, shipping, multi-currency, help desk, a marketplace, and an extension and webhook model. Those gaps drive Phases 1–2 and 6 of the roadmap.

---

## 3. Feature gap analysis

| Application | Industry-standard features missing | Proposals |
|---|---|---|
| Super Admin | Tenant lifecycle (trial, suspend, cancel), plan limits, impersonation with audit, usage visibility | SA-01, SA-02, SA-03, PLT-07, PLT-09 |
| E-commerce Admin | Customer management screen, tenant staff accounts, gift cards and store credit | EA-01, EA-02, EA-03 |
| Storefront | Tenant resolution, server-side pricing and promo validation, shipping rates, multi-currency, payments | SF-01, SF-02, SF-03, SF-04, PLT-04 |
| Inventory | Atomic reservations tied to orders, reorder automation, cycle counts and barcode workflows | INV-01, INV-02, INV-03, PLT-14 |
| Accounting | Database-enforced immutability, PSP settlement reconciliation, ledger exports | ACC-01, ACC-02, ACC-03 |
| Delivery | Automatic delivery creation, route planning and batching | DEL-01, DEL-02 |
| Vendor / Restaurant | Vendor accounts and onboarding, commissions and payouts, kitchen display | VR-01, VR-02, VR-03 |
| CRM | Unified customer timeline, consent management, automated journeys | CRM-01, CRM-02, CRM-03 |
| Quality Center | Synthetic journey monitoring, tenant-scope decision | QC-01, QC-02 |
| Customer Support | Ticketing, portal, email intake, knowledge base, order actions | CS-01, CS-02, CS-03, CS-04 |
| Platform | Lockout, asymmetric JWT, tenant isolation framework, payments, outbox, session lifecycle, entitlements, notifications, billing, webhooks, workflow, reporting, documents, audit, extensions, AI, mobile | PLT-01 to PLT-18 |

---

## 4. Proposal summary

| ID | Application | Feature | Type | Priority | Complexity | Owning service | Status |
|---|---|---|---|---|---|---|---|
| PLT-01 | Platform | Account lockout and login throttling | EXTEND | P0 | S | user-service; api-gateway | **Implemented** (throttling is per gateway instance; Redis later) |
| PLT-02 | Platform | Asymmetric JWT with issuer and audience | EXTEND | P0 | M | user-service; all verifiers | Proposed |
| PLT-03 | Platform | Tenant isolation framework | EXTEND | P0 | L | each owning service | Proposed |
| PLT-04 | Platform | Payment service | NEW | P0 | XL | payment-service (new) | Proposed; PSP decision needed |
| PLT-05 | Platform | Shared transactional outbox | EXTEND | P1 | M | shared module `backend/outbox` | **Implemented for all producers**; envelope, metrics and live verification remain |
| PLT-06 | Platform | Session lifecycle: refresh, revocation, reset, MFA | EXTEND | P1 | L | user-service; api-gateway | Proposed |
| PLT-07 | Platform | Plans, entitlements and usage limits | EXTEND | P1 | M | user-service | Proposed |
| PLT-08 | Platform | Notification center | EXTEND | P1 | L | notification-service | Proposed |
| PLT-09 | Platform | SaaS subscription billing | NEW | P2 | L | billing-service (new) | Proposed |
| PLT-10 | Platform | Outbound webhooks and tenant API keys | NEW | P2 | L | integration-service (new) | Proposed |
| PLT-11 | Platform | Approval workflow engine | NEW | P2 | L | workflow-service (new) | Proposed |
| PLT-12 | Platform | Reporting and export engine | EXTEND | P2 | L | analytics-service | Proposed |
| PLT-13 | Platform | Document management | NEW | P2 | M | document-service (new) | Proposed |
| PLT-14 | Platform | Multi-location operations | EXTEND | P2 | L | inventory-service; user-service | Proposed |
| PLT-15 | Platform | Central audit and activity timeline | EXTEND | P2 | M | analytics-service | Proposed |
| PLT-16 | Platform | Extension framework and app marketplace | NEW | P3 | XL | integration-service | Proposed |
| PLT-17 | Platform | AI assistant | EXTEND | P3 | L | analytics-service | Proposed |
| PLT-18 | Platform | Mobile and PWA capabilities | NEW | P3 | L | shell-app; notification-service | Proposed |
| SA-01 | Super Admin | Tenant lifecycle and suspension | EXTEND | P1 | M | user-service; api-gateway | Proposed |
| SA-02 | Super Admin | Plan and entitlement console | EXTEND | P1 | M | mfe-super-admin | Proposed |
| SA-03 | Super Admin | Audited impersonation | NEW | P2 | M | user-service | Proposed |
| EA-01 | E-commerce Admin | Customer management | EXTEND | P1 | M | user-service; order-service | Proposed |
| EA-02 | E-commerce Admin | Tenant staff and roles | EXTEND | P1 | M | user-service | Proposed |
| EA-03 | E-commerce Admin | Gift cards and store credit | NEW | P2 | L | payment-service | Proposed |
| SF-01 | Storefront | Storefront tenant resolution | EXTEND | P1 | M | product-service; order-service | Proposed |
| SF-02 | Storefront | Pricing quote and promo validation | EXTEND | P1 | M | product-service | Proposed |
| SF-03 | Storefront | Shipping zones and rates | NEW | P1 | L | order-service | Proposed |
| SF-04 | Storefront | Multi-currency and tax-inclusive display | NEW | P2 | L | product-service | Proposed |
| INV-01 | Inventory | Atomic order reservations | EXTEND | P0 | L | inventory-service; order-service | Proposed |
| INV-02 | Inventory | Reorder automation | EXTEND | P2 | M | inventory-service; procurement-service | Proposed |
| INV-03 | Inventory | Cycle counts and barcode workflows | EXTEND | P2 | M | inventory-service | Proposed |
| ACC-01 | Accounting | Posted-line immutability | EXTEND | P0 | S | accounting-service | Proposed |
| ACC-02 | Accounting | PSP settlement reconciliation | EXTEND | P1 | L | accounting-service | Proposed |
| ACC-03 | Accounting | Ledger exports | EXTEND | P2 | M | accounting-service | Proposed |
| DEL-01 | Delivery | Delivery created on fulfilment | EXTEND | P1 | M | delivery-service | Proposed |
| DEL-02 | Delivery | Route planning and batching | NEW | P2 | L | delivery-service | Proposed |
| VR-01 | Vendor / Restaurant | Multi-vendor domain and onboarding | NEW | P2 | XL | vendor-service (new) | Proposed |
| VR-02 | Vendor / Restaurant | Commissions and payouts | NEW | P2 | L | payment-service; accounting-service | Proposed |
| VR-03 | Vendor / Restaurant | Kitchen display and throttling | EXTEND | P2 | M | order-service | Proposed |
| CRM-01 | CRM | Customer 360 timeline | EXTEND | P1 | M | crm-service | Proposed |
| CRM-02 | CRM | Consent and communication preferences | NEW | P1 | M | crm-service; notification-service | Proposed |
| CRM-03 | CRM | Segments and automated journeys | EXTEND | P2 | L | crm-service | Proposed |
| QC-01 | Quality Center | Synthetic journey monitoring | EXTEND | P2 | M | qa-service | Proposed |
| QC-02 | Quality Center | Tenant-scoped test plans | EXTEND | P2 | M | qa-service | BLOCKED (scope decision) |
| CS-01 | Customer Support | Ticketing core | NEW | P1 | L | support-service (new) | Proposed |
| CS-02 | Customer Support | Customer portal and email-to-ticket | NEW | P2 | M | support-service | Proposed |
| CS-03 | Customer Support | Knowledge base | NEW | P2 | M | support-service | Proposed |
| CS-04 | Customer Support | Order and refund actions from tickets | EXTEND | P2 | M | support-service → order-service | Proposed |

48 proposals: 6 P0, 16 P1, 23 P2, 3 P3.

---

## 5. Technical architecture recommendations

### 5.1 Principles

1. **One owner per capability.** Notifications belong to notification-service, reports to analytics-service, approvals to workflow-service, outbound webhooks to integration-service. No other service builds its own version.
2. **New services only for a missing bounded context.** Six are proposed: payment, billing, integration, workflow, document and support. A seventh, vendor-service, waits until VR-01 is scheduled. Everything else extends an existing service.
3. **Stay on the current stack.** Spring Boot 3.2, PostgreSQL per service, Kafka, React with Module Federation, and the Spring Cloud gateway. The one stack addition is Redis being used for real (rate limits, revocation denylist, locks), since it is already provisioned but idle (B-32).
4. **Additive migrations only.** New Flyway versions, `DEFAULT` values for new non-null columns, and no edits to applied migrations.

### 5.2 Tenant isolation pattern (applies to every entry)

- Every new table has `tenant_id UUID NOT NULL`, and every unique constraint includes `tenant_id`.
- The tenant comes from the verified token claim forwarded by the gateway (`X-Tenant-Id`), never from the request body.
- Repositories filter by tenant in every query. A cross-tenant read returns 404, not 403, so ids are not confirmed.
- Kafka events carry `tenantId`; consumers reject events whose tenant does not match the stored aggregate.
- Cache keys and object-storage paths are prefixed `t/{tenantId}/`.
- Each feature ships a cross-tenant test: tenant A's token cannot read or modify tenant B's data.

### 5.3 Event contract

All new events use one envelope: `eventId` (UUID), `type` (`<domain>.<entity>.<verb>`, for example `support.ticket.created`), `version`, `tenantId`, `occurredAt`, `actor`, `payload`. Producers write through the shared outbox (PLT-05). Consumers are idempotent on `eventId` and route poison messages to `<topic>.DLT`.

### 5.4 Authentication and authorization

Permission keys follow the existing `<module>:<action>` form (for example `support:tickets:manage`). New permissions are seeded by a user-service Flyway migration, as V5 and V7–V13 already do. Services verify permissions from the token rather than trusting role headers. That is the K10 and S9 direction; it depends on PLT-02.

### 5.5 Frontend integration

Each application stays a Module Federation remote loaded by `shell-app`. Cross-cutting UI (notification inbox, tenant switcher, approval tray) lives in `shared-library`, so remotes do not duplicate it. New remotes are added only for new applications (`mfe-support`; `mfe-vendor` when VR-01 starts).

### 5.6 Failure handling and observability (defaults)

- Outbound calls use timeouts and Resilience4j circuit breakers; the gateway already has the dependency.
- Writes that cross services use the outbox plus idempotency keys, never distributed transactions.
- Each service exposes RED metrics (rate, errors, duration) with a tenant label for operator dashboards, propagates trace ids (Zipkin is configured), and logs as JSON without personal data.

### 5.7 Duplication to remove

`accounting-service` and `analytics-service` contain their own `Order`, `OrderItem`, `Coupon`, `CustomerCart` and `OrderReturn` JPA entities. They should become read models built from order events, so that order-service is the only writer of order data. Planned under PLT-05 and PLT-12.

---

## 6. Platform-wide features

### PLT-01 Account lockout and login throttling

| Field | Detail |
|---|---|
| Application / Type | Platform identity · EXTEND (`user-service` `AuthController`) · **Implemented: lockout (user-service) and per-address throttling (api-gateway, in memory)** |
| Priority / Complexity | P0 / S |
| Business problem | Login accepted unlimited password guesses (B-20). |
| Proposed functionality | After N failed sign-ins (default 5), lock the account for a period (default 15 min). Applies to both `/login` and `/login/application`. A correct password during the lock is refused. Success clears the counter. Second part: per-IP rate limit at the gateway. |
| User roles | All users. Platform operators set the thresholds. |
| Frontend screens | Login screens show the 423 message (shell-app and each application login). No new screen. |
| Backend APIs | Existing login endpoints return `423 Locked` while locked. Later: `POST /api/platform/users/{id}/unlock` (permission `users:manage`). |
| Database changes | `users.failed_login_attempts INTEGER NOT NULL DEFAULT 0`, `users.locked_until TIMESTAMPTZ` (V16). |
| Events and integrations | Later: `identity.user.locked` for the notification center and audit. |
| Dependencies | None. A cluster-wide limit later needs Redis. |
| Security considerations | Counter updated by single SQL statements, so concurrent failures are not lost. `/register` already reveals whether an email exists (409), so 423 adds no new enumeration channel. A wrong-email request skips password hashing, which leaves a timing difference (pre-existing, recorded as a limitation). |
| Business value | Removes the cheapest account-takeover path. |
| Acceptance criteria | Five failures lock the account; the correct password then returns 423. The lock expires. Success resets the counter. Unknown emails always return 401. Both login endpoints behave the same. |
| Owning service | user-service (lockout); api-gateway (throttling). |
| Tenant isolation | Lockout is per user account, which is platform-level identity. Throttling is keyed by client address. |
| Failure handling | A database failure while recording a failure surfaces as a 5xx; sign-in does not silently succeed. |
| Observability | WARN log `User {id} locked until {time} after {n} failed sign-in attempts`. Later: a lockout counter metric. |
| Testing | Five integration tests in `AuthAndUserIntegrationTest` and three in `LoginRateLimitTest` (all passing). |

### PLT-02 Asymmetric JWT with issuer and audience

| Field | Detail |
|---|---|
| Application / Type | Platform identity · EXTEND (`JwtUtil` in user-service and the verifiers) |
| Priority / Complexity | P0 / M |
| Business problem | Seven services share one HS256 secret, and validation ignores `iss` and `aud`. Any service can mint an admin token (B-05). |
| Proposed functionality | user-service signs with RS256 or ES256. Other services verify with the public key from a JWKS endpoint. Tokens carry `iss`, `aud` (application code) and `kid`. Key rotation with overlap. |
| User roles | None directly. |
| Frontend screens | None. |
| Backend APIs | `GET /.well-known/jwks.json` (user-service, public). |
| Database changes | None. Keys are held in Vault or a Kubernetes Secret. |
| Events and integrations | None. |
| Dependencies | Key-management decision (BLOCKED). |
| Security considerations | The private key never leaves user-service. Verifiers cache the JWKS with a bounded TTL. HS256 tokens are rejected once the migration window ends. |
| Business value | Contains a breach of any single service. Required for enterprise security reviews. |
| Acceptance criteria | A token signed by another key is refused; a wrong `aud` is refused; rotation causes no failed requests. |
| Owning service | user-service (issuer); every verifier. |
| Tenant isolation | `tid` claim unchanged; verified before any tenant header is trusted. |
| Failure handling | JWKS fetch failure falls back to the cached keys; with no keys, requests fail closed (401). |
| Observability | Metric for rejected tokens by reason. |
| Testing | Unit tests for verification; integration tests in the gateway and one downstream service. |

### PLT-03 Tenant isolation framework

| Field | Detail |
|---|---|
| Application / Type | Platform · EXTEND (pattern already used in accounting V19 and the gateway `TenantRoutePolicy`) |
| Priority / Complexity | P0 / L (nine slices) |
| Business problem | Nine services return every tenant's data to platform tokens (B-02). |
| Proposed functionality | A small shared library: tenant context from the verified header, a repository helper that requires a tenant argument, and a test fixture for cross-tenant tests. Applied service by service: crm, loyalty, cart, stock, notification, qa, error-stack, analytics, legacy procurement. |
| User roles | All tenant users; platform operators keep explicit cross-tenant read permission. |
| Frontend screens | None. Existing screens show tenant-scoped data. |
| Backend APIs | Unchanged paths. Behaviour changes: cross-tenant ids return 404. |
| Database changes | One migration per service: `tenant_id UUID NOT NULL DEFAULT '<legacy tenant>'`, indexes, and tenant-inclusive unique constraints. |
| Events and integrations | Events gain `tenantId` (B-27). |
| Dependencies | PLT-02 is preferred, so services trust claims rather than headers. Can start before it. |
| Security considerations | Never accept a tenant from the body. Platform cross-tenant reads need an explicit permission and are audited. |
| Business value | Precondition for selling to more than one tenant. |
| Acceptance criteria | Each service has a passing cross-tenant test; the gateway allow-list then opens that service to tenant tokens. |
| Owning service | Each service owns its slice; the shared library is owned by the platform team. |
| Tenant isolation | This feature is the isolation work itself. |
| Failure handling | A missing tenant context fails the request with 400 instead of defaulting. |
| Observability | Log and metric for requests rejected for a missing tenant. |
| Testing | Per-service cross-tenant tests; migration test on a scratch Postgres. |

### PLT-04 Payment service

| Field | Detail |
|---|---|
| Application / Type | Platform payments · NEW (`payment-service`). Replaces the shared-token callbacks in order-service (K1). |
| Priority / Complexity | P0 / XL |
| Business problem | No real payment processing; payment confirmation is accepted on a shared token (B-01). |
| Proposed functionality | PSP adapter interface with one provider first; payment intents; signed webhook intake; refunds; idempotency keys; settlement import for reconciliation (ACC-02). Card data never touches UCP (PSP-hosted fields or redirect). |
| User roles | Customers (pay), tenant finance (refunds), platform operators (provider configuration). |
| Frontend screens | Checkout payment step (PSP-hosted component); payment detail panel in the order admin. |
| Backend APIs | `POST /api/payments/intents`, `POST /api/payments/{id}/refunds`, `POST /webhooks/psp/{provider}` (signature-verified), `GET /api/payments?orderId=`. |
| Database changes | New `payment_db`: `payment_intents`, `payment_attempts`, `refunds`, `psp_events` (raw, unique by provider event id), `idempotency_keys`. All tenant-scoped. |
| Events and integrations | Publishes `payment.intent.succeeded`, `payment.intent.failed`, `payment.refund.succeeded`. Consumed by order-service (confirm or cancel) and accounting (postings). |
| Dependencies | PSP decision (BLOCKED); PLT-05 outbox; PLT-03 for tenant context. |
| Security considerations | Webhook signature verification, replay protection by event id, PCI scope kept to SAQ-A by never handling card data, and secrets in Vault. |
| Business value | Enables real revenue; prerequisite for billing, gift cards and payouts. |
| Acceptance criteria | Successful payment confirms the order exactly once; a duplicate webhook is ignored; a failed payment releases the reservation (INV-01); a refund posts to accounting. |
| Owning service | payment-service. |
| Tenant isolation | `tenant_id` on every table; PSP account per tenant (connected accounts) or one platform account with tenant metadata, decided with the PSP. |
| Failure handling | Webhooks acknowledged after persisting the raw event; processing retried from the store; order confirmation through the outbox. |
| Observability | Payment success-rate and latency per provider and tenant; alert on webhook signature failures. |
| Testing | Contract tests against the provider sandbox; duplicate and out-of-order webhook tests; end-to-end checkout test. |

### PLT-05 Shared transactional outbox

| Field | Detail |
|---|---|
| Application / Type | Platform events · EXTEND (generalise `delivery-service` `DeliveryOutboxPublisher`) |
| Priority / Complexity | P1 / M |
| Business problem | Six producers publish from the request thread; a broker outage slows or loses events (B-16). |
| Proposed functionality | A shared module: `outbox_events` table, a writer used inside the business transaction, a scheduled publisher with backoff, and the envelope from §5.3. Adopt in cart, inventory, loyalty, order, stock and notification. |
| User roles | None. |
| Frontend screens | None. |
| Backend APIs | None public. Internal admin endpoint lists stuck events. |
| Database changes | `outbox_events` in each adopting service (new migration each). |
| Events and integrations | All existing topics move to the envelope with a version bump. |
| Dependencies | None. |
| Security considerations | Payloads exclude secrets and minimise personal data. |
| Business value | Reliable events are the base for webhooks, notifications and analytics. |
| Acceptance criteria | State change and event commit together; broker outage does not fail requests; events publish once the broker returns. |
| Owning service | Platform module; each service owns its table. |
| Tenant isolation | Envelope carries `tenantId`; the table includes it. |
| Failure handling | Exponential backoff; events failing N times are flagged for operators. |
| Observability | Outbox depth and age metrics per service. |
| Testing | Transactional-commit test; broker-down test using an embedded broker. |

### PLT-06 Session lifecycle: refresh tokens, revocation, password reset, MFA

| Field | Detail |
|---|---|
| Application / Type | Platform identity · EXTEND (`user-service`) |
| Priority / Complexity | P1 / L (MFA is a P2 follow-on) |
| Business problem | No refresh tokens, logout does not revoke, no password reset, no MFA (F-05 to F-08). |
| Proposed functionality | Short access tokens (15 min) with rotating refresh tokens and reuse detection; logout and admin revoke add the token id to a Redis denylist checked by the gateway; single-use reset tokens by email; TOTP MFA required for platform and tenant admin roles. |
| User roles | All users; admins for forced logout. |
| Frontend screens | Forgot-password and reset pages; MFA enrolment and challenge; "sign out everywhere" in profile. |
| Backend APIs | `POST /api/users/token/refresh`, `POST /api/users/logout`, `POST /api/users/password/forgot`, `POST /api/users/password/reset`, `POST /api/users/mfa/enrol`, `POST /api/users/mfa/verify`. |
| Database changes | `refresh_tokens` (hashed, family id), `password_reset_tokens` (hashed, expiry), `user_mfa` (encrypted secret). |
| Events and integrations | `identity.password.reset_requested` to notification-service (email). |
| Dependencies | PLT-02 (claims), Redis, PLT-08 (email). |
| Security considerations | Store only hashes of tokens. Reset requests reply identically for unknown emails. Rate-limit reset requests. Encrypt MFA secrets at rest. |
| Business value | Required by most customers' security policies. |
| Acceptance criteria | A reused refresh token revokes the family; a logged-out token is refused within one gateway cache TTL; a reset token works once; MFA is enforced for admin roles. |
| Owning service | user-service; api-gateway for the denylist check. |
| Tenant isolation | Identity is platform-level; application tokens keep their tenant claim. |
| Failure handling | If Redis is unavailable, the gateway fails closed for admin routes and open for storefront browsing (decision recorded). |
| Observability | Metrics for refresh reuse, resets and MFA failures. |
| Testing | Integration tests for each flow; gateway test for revoked tokens. |

### PLT-07 Plans, entitlements and usage limits

| Field | Detail |
|---|---|
| Application / Type | Platform SaaS · EXTEND (`user-service` application entitlements and grants) |
| Priority / Complexity | P1 / M |
| Business problem | Entitlements are on or off per application; there are no plans, limits or feature flags, which blocks pricing tiers. |
| Proposed functionality | Plans (for example Starter, Growth, Enterprise) bundle application entitlements, feature flags and numeric limits (staff seats, products, orders per month, locations). Services check flags and limits through one cached client. |
| User roles | Platform operators define plans; tenant owners view their plan. |
| Frontend screens | Plan catalog and tenant plan assignment in Super Admin (SA-02); "Your plan" page for tenant owners. |
| Backend APIs | `GET/POST /api/platform/plans`, `PUT /api/platform/tenants/{id}/plan`, internal `GET /internal/entitlements/{tenantId}`. |
| Database changes | `plans`, `plan_features`, `plan_limits`, `tenant_plans`, `tenant_usage_counters`. |
| Events and integrations | `tenant.plan.changed`; usage counters fed by events (order created, product created). |
| Dependencies | PLT-03 for tenant context; PLT-05 for usage events. |
| Security considerations | Limits enforced server-side; the UI only reflects them. |
| Business value | Enables monetisation tiers and upsell. |
| Acceptance criteria | Exceeding a limit returns 402 or 403 with a clear code; a flag off hides the feature in the UI and refuses the API. |
| Owning service | user-service. |
| Tenant isolation | All rows keyed by tenant; only platform operators change plans. |
| Failure handling | Entitlement client caches the last known value; on a cold start with user-service down, deny paid features. |
| Observability | Usage per tenant against limits; alert at 90 percent. |
| Testing | Limit-enforcement tests in one consuming service; plan change takes effect within the cache TTL. |

### PLT-08 Notification center

| Field | Detail |
|---|---|
| Application / Type | Platform · EXTEND (`notification-service` with DLQ) |
| Priority / Complexity | P1 / L |
| Business problem | Notifications are event-driven but have no templates, channels, preferences or in-app inbox. Each new feature would otherwise build its own. |
| Proposed functionality | Tenant-branded templates; email, SMS and in-app channels; per-user preferences honouring consent (CRM-02); in-app inbox; delivery status and retries. |
| User roles | Tenant admins (templates), all users (inbox and preferences). |
| Frontend screens | Inbox bell in `shared-library` (shell-app already has `NotificationBell`); template editor; preference page. |
| Backend APIs | `GET /api/notifications/inbox`, `POST /api/notifications/inbox/{id}/read`, `GET/PUT /api/notifications/preferences`, `GET/PUT /api/notifications/templates`. |
| Database changes | `notification_templates`, `notification_preferences`, `notification_deliveries`, all with `tenant_id`. |
| Events and integrations | Consumes domain events; email and SMS providers behind adapters. |
| Dependencies | PLT-03, PLT-05, CRM-02. |
| Security considerations | Template rendering escapes variables; no secrets in templates; unsubscribe links are signed. |
| Business value | One place for every customer and staff message; supports retention journeys (CRM-03). |
| Acceptance criteria | An order event produces an email using the tenant template; opted-out users receive none; failed deliveries retry and land in the DLQ. |
| Owning service | notification-service. |
| Tenant isolation | Templates and preferences per tenant; provider credentials per tenant or platform. |
| Failure handling | Provider failures retried with backoff; persistent failures to the DLQ with operator view. |
| Observability | Delivery rate and failure rate per channel and tenant. |
| Testing | Template rendering tests; consent enforcement test; DLQ test. |

### PLT-09 SaaS subscription billing

| Field | Detail |
|---|---|
| Application / Type | Platform · NEW (`billing-service`) |
| Priority / Complexity | P2 / L |
| Business problem | The platform cannot charge tenants for plans. |
| Proposed functionality | Subscriptions tied to PLT-07 plans; invoices; proration on plan change; dunning; usage-based add-ons from usage counters. |
| User roles | Platform finance; tenant owners. |
| Frontend screens | Billing page for tenant owners; platform billing console in Super Admin. |
| Backend APIs | `GET /api/billing/subscription`, `POST /api/billing/subscription/change`, `GET /api/billing/invoices`. |
| Database changes | `subscriptions`, `billing_invoices`, `billing_invoice_lines`, `dunning_attempts`. |
| Events and integrations | Consumes `tenant.plan.changed`; uses payment-service for charges; emits `billing.invoice.paid` and `billing.subscription.past_due` (suspension via SA-01). |
| Dependencies | PLT-04, PLT-07, SA-01. |
| Security considerations | Only tenant owners see billing; invoices are immutable once issued. |
| Business value | Platform revenue. |
| Acceptance criteria | Plan change prorates correctly; failed payment triggers dunning and eventually suspension. |
| Owning service | billing-service. |

### PLT-10 Outbound webhooks and tenant API keys

| Field | Detail |
|---|---|
| Application / Type | Platform integrations · NEW (`integration-service`) |
| Priority / Complexity | P2 / L |
| Business problem | Tenants cannot integrate with their own systems (only error-stack has ingest keys). |
| Proposed functionality | Tenant API keys with scopes (hashed at rest) accepted by the gateway; webhook subscriptions per event type; HMAC-signed deliveries with retries, backoff and a delivery log. |
| User roles | Tenant admins and developers. |
| Frontend screens | Developer settings: API keys, webhook endpoints, delivery log with replay. |
| Backend APIs | `POST/GET/DELETE /api/integrations/api-keys`, `POST/GET/DELETE /api/integrations/webhooks`, `POST /api/integrations/webhooks/{id}/test`. |
| Database changes | `api_keys`, `webhook_subscriptions`, `webhook_deliveries`. |
| Events and integrations | Consumes the event envelope (PLT-05) and fans out to subscribers. |
| Dependencies | PLT-05, PLT-03. |
| Security considerations | Keys shown once; SSRF protection on endpoint URLs (block private ranges); signatures with timestamp. |
| Business value | Foundation for the marketplace (PLT-16) and enterprise integrations. |
| Acceptance criteria | Signed delivery verifiable by the receiver; failing endpoint retried then disabled; replay works. |
| Owning service | integration-service. |

### PLT-11 Approval workflow engine

| Field | Detail |
|---|---|
| Application / Type | Platform · NEW (`workflow-service`). Existing accounting journal-draft approval migrates onto it later. |
| Priority / Complexity | P2 / L |
| Business problem | Approvals exist only for accounting journals; purchase orders, refunds, price changes and payouts need them too. |
| Proposed functionality | Configurable approval policies (amount thresholds, roles, sequential or parallel steps); approval tray; escalation timers. |
| User roles | Tenant admins configure; approvers act. |
| Frontend screens | Approval tray in `shared-library`; policy editor. |
| Backend APIs | `POST /api/workflows/requests`, `POST /api/workflows/requests/{id}/decision`, `GET /api/workflows/tray`. |
| Database changes | `approval_policies`, `approval_requests`, `approval_steps`. |
| Events and integrations | Emits `workflow.request.approved` and `workflow.request.rejected`; requesting services act on them. |
| Dependencies | PLT-05, PLT-03. |
| Security considerations | Requesters cannot approve their own request; decisions are audited. |
| Business value | Financial control for growing businesses. |
| Acceptance criteria | A purchase order over the threshold waits for approval; self-approval is refused. |
| Owning service | workflow-service. |

### PLT-12 Reporting and export engine

| Field | Detail |
|---|---|
| Application / Type | Platform · EXTEND (`analytics-service`, `mfe-analytics`) |
| Priority / Complexity | P2 / L |
| Business problem | Analytics aggregates across tenants (B-04) and has no scheduled reports or exports. |
| Proposed functionality | Tenant-scoped report definitions over read models built from events; scheduled CSV and XLSX exports with formula-injection protection; replaces the copied order entities (§5.7). |
| User roles | Tenant admins and analysts. |
| Frontend screens | Report builder; schedules; export history. |
| Backend APIs | `GET/POST /api/reports`, `POST /api/reports/{id}/run`, `GET/POST /api/reports/{id}/schedules`. |
| Database changes | Read-model tables in `analytics_db` with `tenant_id`; `report_definitions`, `report_schedules`, `report_runs`. |
| Events and integrations | Consumes order, payment, inventory and support events; exports go to document-service (PLT-13). |
| Dependencies | PLT-03, PLT-05, PLT-13. |
| Security considerations | Row-level tenant filter; exports expire. |
| Business value | Self-service insight; removes duplicate entities. |
| Acceptance criteria | Reports show only the caller's tenant; a scheduled export is delivered and expires. |
| Owning service | analytics-service. |

### PLT-13 Document management

| Field | Detail |
|---|---|
| Application / Type | Platform · NEW (`document-service`). The returns evidence storage in order-service migrates to it later. |
| Priority / Complexity | P2 / M |
| Business problem | Invoices, packing slips, exports and attachments are stored ad hoc or not at all. |
| Proposed functionality | Object storage abstraction; generated PDFs from templates; attachments with virus scan hook; retention policies; signed download links. |
| User roles | All applications as clients. |
| Frontend screens | Attachment widget in `shared-library`. |
| Backend APIs | `POST /api/documents`, `GET /api/documents/{id}/link`, `POST /api/documents/render`. |
| Database changes | `documents` (metadata, `tenant_id`, retention). |
| Events and integrations | Emits `document.created`. |
| Dependencies | PLT-03. |
| Security considerations | Signed short-lived links; content-type allow-list; tenant path prefix. |
| Business value | Professional invoices and compliance retention. |
| Acceptance criteria | Another tenant's document link is refused; expired links fail. |
| Owning service | document-service. |

### PLT-14 Multi-location operations

| Field | Detail |
|---|---|
| Application / Type | Platform · EXTEND (`inventory-service` warehouses and locations) |
| Priority / Complexity | P2 / L |
| Business problem | Warehouses exist in inventory, but stores and branches are not modelled across orders, staff and delivery. |
| Proposed functionality | Location as a tenant entity used by inventory, orders (fulfilment location, pickup in store), staff scoping and delivery origin. |
| User roles | Tenant admins; location managers. |
| Frontend screens | Location manager; location switcher for staff. |
| Backend APIs | Extend inventory location APIs; `locationId` on order fulfilment. |
| Database changes | `locations` (or reuse `Warehouse` with a type), `staff_locations`. |
| Events and integrations | `location.created`; order events carry `fulfilmentLocationId`. |
| Dependencies | PLT-03, INV-01. |
| Security considerations | Staff restricted to their locations. |
| Business value | Required for restaurant chains and multi-store retail. |
| Acceptance criteria | An order is fulfilled from the nearest location with stock; staff see only their locations. |
| Owning service | inventory-service (locations); user-service (staff scoping). |

### PLT-15 Central audit and activity timeline

| Field | Detail |
|---|---|
| Application / Type | Platform · EXTEND (audit tables in accounting and user-service) |
| Priority / Complexity | P2 / M |
| Business problem | Audit records are spread across services with no single view. |
| Proposed functionality | Services publish `audit.*` events; analytics-service stores them per tenant; searchable timeline for operators and tenant admins. |
| User roles | Tenant admins; platform operators. |
| Frontend screens | Activity log page. |
| Backend APIs | `GET /api/audit?entity=&actor=&from=&to=`. |
| Database changes | `audit_events` in `analytics_db`. |
| Events and integrations | `audit.<domain>.<action>` events. |
| Dependencies | PLT-05. |
| Security considerations | Append-only storage; personal data minimised. |
| Business value | Compliance and support investigations. |
| Acceptance criteria | A role change and a ledger reversal both appear in the timeline within a minute. |
| Owning service | analytics-service. |

### PLT-16 Extension framework and app marketplace

| Field | Detail |
|---|---|
| Application / Type | Platform · NEW (in `integration-service`) |
| Priority / Complexity | P3 / XL |
| Business problem | No way for partners to extend the platform. |
| Proposed functionality | OAuth apps with scopes, install flow per tenant, embedded app pages in the shell, prebuilt connectors (accounting export, carriers, marketplaces). |
| User roles | Partners; tenant admins installing apps. |
| Frontend screens | App catalog; install consent; embedded app frame. |
| Backend APIs | OAuth authorize and token endpoints; app registry APIs. |
| Database changes | `apps`, `app_installations`, `app_scopes`. |
| Events and integrations | Built on PLT-10. |
| Dependencies | PLT-02, PLT-10. |
| Security considerations | Scope consent; app review; sandboxed iframes. |
| Business value | Ecosystem and revenue share. |
| Acceptance criteria | An installed app receives only consented scopes. |
| Owning service | integration-service. |

### PLT-17 AI assistant

| Field | Detail |
|---|---|
| Application / Type | Platform · EXTEND (the "Analytics Copilot" panel in `mfe-analytics` and `mfe-order` is front-end text only) |
| Priority / Complexity | P3 / L |
| Business problem | The assistant panel has no backend and cannot answer real questions. |
| Proposed functionality | Tenant-scoped assistant answering questions from report read models (PLT-12) through read-only tools; product description drafts; ticket reply drafts for support. |
| User roles | Tenant admins and agents. |
| Frontend screens | Existing panel wired to the backend. |
| Backend APIs | `POST /api/assistant/messages` (streams). |
| Database changes | `assistant_conversations` (optional, retention-limited). |
| Events and integrations | LLM provider behind an adapter. |
| Dependencies | PLT-12, PLT-03. |
| Security considerations | Tools are read-only and tenant-filtered; prompts never contain other tenants' data; output is labelled as generated. |
| Business value | Productivity; competitive parity. |
| Acceptance criteria | Answers cite the report used; cross-tenant prompt-injection test fails to leak data. |
| Owning service | analytics-service. |

### PLT-18 Mobile and PWA capabilities

| Field | Detail |
|---|---|
| Application / Type | Platform · NEW |
| Priority / Complexity | P3 / L |
| Business problem | No installable storefront or staff apps; couriers and kitchen staff need mobile screens. |
| Proposed functionality | PWA manifest and offline shell for the storefront; push notifications through PLT-08; mobile layouts for the courier (DEL) and kitchen (VR-03) screens. |
| User roles | Customers, couriers, kitchen staff. |
| Frontend screens | PWA shell; courier and kitchen mobile views. |
| Backend APIs | `POST /api/notifications/push-subscriptions`. |
| Database changes | `push_subscriptions`. |
| Events and integrations | Web Push. |
| Dependencies | PLT-08. |
| Security considerations | Push payloads carry no personal data beyond a title. |
| Business value | Engagement and staff productivity. |
| Acceptance criteria | Storefront installs and loads offline; push arrives for order updates. |
| Owning service | shell-app; notification-service. |

---

## 7. Application features

### Super Admin

#### SA-01 Tenant lifecycle and suspension

| Field | Detail |
|---|---|
| Type | EXTEND (`user-service` tenants) |
| Priority / Complexity | P1 / M |
| Business problem | Tenants exist but have no lifecycle; a non-paying or abusive tenant cannot be suspended. |
| Proposed functionality | States: trial, active, suspended, cancelled, with dates. Suspended tenants keep data but receive 403 at the gateway (read-only mode optional). |
| User roles | Platform operators. |
| Frontend screens | Tenant detail with state actions in `mfe-super-admin`. |
| Backend APIs | `POST /api/platform/tenants/{id}/suspend`, `/reactivate`, `/cancel`. |
| Database changes | `tenants.status`, `status_changed_at`, `trial_ends_at` (new migration). |
| Events and integrations | `tenant.status.changed` (gateway cache, billing). |
| Dependencies | None; PLT-09 later drives automatic suspension. |
| Security considerations | Only platform operators; every change audited. |
| Business value | Basic SaaS operability. |
| Acceptance criteria | A suspended tenant's tokens are refused within the cache TTL; reactivation restores access. |
| Owning service | user-service; api-gateway enforces. |
| Tenant isolation | Platform-level action on one tenant. |
| Failure handling | Gateway caches status; on a cache miss with user-service down, existing tokens stay valid until expiry. |
| Observability | Count of tenants by state. |
| Testing | Gateway test refusing a suspended tenant; user-service state transition tests. |

#### SA-02 Plan and entitlement console

| Field | Detail |
|---|---|
| Type | EXTEND (`mfe-super-admin`) |
| Priority / Complexity | P1 / M |
| Business problem | Operators cannot manage plans and limits without SQL. |
| Proposed functionality | UI over PLT-07: plan catalog, assignment, usage against limits. |
| User roles | Platform operators. |
| Frontend screens | Plans list, plan editor, tenant plan tab. |
| Backend APIs | Those of PLT-07. |
| Database changes | None beyond PLT-07. |
| Events and integrations | None. |
| Dependencies | PLT-07. |
| Security considerations | Requires a platform permission (`platform:plans:manage`). |
| Business value | Operator productivity. |
| Acceptance criteria | Changing a plan updates the tenant's access without a deploy. |
| Owning service | mfe-super-admin (UI); user-service (API). |
| Tenant isolation | Platform-level. |
| Failure handling | UI shows API errors; no optimistic updates for plan changes. |
| Observability | — |
| Testing | UI route test; API tests in PLT-07. |

#### SA-03 Audited impersonation

| Field | Detail |
|---|---|
| Type | NEW (owning service: user-service) |
| Priority / Complexity | P2 / M |
| Business problem | Support staff cannot see what a tenant admin sees. |
| Proposed functionality | Time-boxed, read-only "view as" token with reason, banner, and audit record; optional tenant consent. |
| User roles | Platform support with a dedicated permission. |
| Frontend screens | "View as" action; persistent banner. |
| Backend APIs | `POST /api/platform/impersonations`. |
| Database changes | `impersonation_sessions`. |
| Events and integrations | `identity.impersonation.started` to audit. |
| Dependencies | PLT-02, PLT-15. |
| Security considerations | Read-only by default; cannot impersonate platform roles; 30-minute limit. |
| Business value | Faster support resolution. |
| Acceptance criteria | Writes during impersonation are refused; the session appears in the audit log. |

### E-commerce Admin

#### EA-01 Customer management

| Field | Detail |
|---|---|
| Type | EXTEND (`user-service` users and addresses; `order-service` orders) |
| Priority / Complexity | P1 / M |
| Business problem | Admins have no customer screen (gap doc Phase 8). |
| Proposed functionality | Customer list and search per tenant; profile with addresses, orders, returns, loyalty, notes; block or anonymise on request. |
| User roles | Tenant admins and staff with `customers:read` and `customers:manage`. |
| Frontend screens | Customers list and detail in `mfe-order` or a new admin route in `shell-app`. |
| Backend APIs | `GET /api/customers?query=`, `GET /api/customers/{id}`, `POST /api/customers/{id}/notes`, `POST /api/customers/{id}/anonymise`. |
| Database changes | `customer_notes` (user-service), `tenant_customers` link if customers are tenant-scoped. |
| Events and integrations | `customer.anonymised` consumed by order, CRM and support. |
| Dependencies | PLT-03, CRM-02. |
| Security considerations | Personal data access audited; anonymisation irreversible and confirmed twice. |
| Business value | Core admin productivity. |
| Acceptance criteria | Staff see only their tenant's customers; anonymisation removes personal data across services. |
| Owning service | user-service (profile); order-service (order summary API). |
| Tenant isolation | Customer-to-tenant link required; cross-tenant lookup returns 404. |
| Failure handling | Customer detail degrades gracefully if order-service is down (section shows unavailable). |
| Observability | Audit of personal-data views. |
| Testing | Cross-tenant test; anonymisation test across services. |

#### EA-02 Tenant staff and roles

| Field | Detail |
|---|---|
| Type | EXTEND (`user-service` RBAC, `mfe-user` role editor) |
| Priority / Complexity | P1 / M |
| Business problem | Roles are platform-wide; tenant owners cannot invite staff with tenant-scoped roles. |
| Proposed functionality | Invite staff by email; tenant-scoped role assignment; seat count against PLT-07 limits; deactivate staff. |
| User roles | Tenant owners and admins. |
| Frontend screens | Staff list, invite dialog, role assignment. |
| Backend APIs | `POST /api/tenants/{id}/staff/invitations`, `GET /api/tenants/{id}/staff`, `PUT /api/tenants/{id}/staff/{userId}/role`. |
| Database changes | `staff_invitations`; tenant scope on role assignments. |
| Events and integrations | Invitation email via PLT-08. |
| Dependencies | PLT-07 (seats), PLT-08. |
| Security considerations | Role hierarchy enforced server-side (a role cannot grant more than it holds); invitation tokens single-use. |
| Business value | Required for any multi-person merchant. |
| Acceptance criteria | Invited staff get only the assigned role in that tenant; seat limit enforced. |
| Owning service | user-service. |
| Tenant isolation | Assignments keyed by tenant. |
| Failure handling | Email failure leaves the invitation re-sendable. |
| Observability | Invitations sent and accepted. |
| Testing | Hierarchy enforcement test; seat-limit test. |

#### EA-03 Gift cards and store credit

| Field | Detail |
|---|---|
| Type | NEW (owning service: payment-service) |
| Priority / Complexity | P2 / L |
| Business problem | No gift cards or store credit (common competitor feature; useful for refunds). |
| Proposed functionality | Issue and sell gift cards; redeem at checkout; store credit for refunds; balance ledger. |
| User roles | Tenant admins; customers. |
| Frontend screens | Gift card admin; balance check; checkout redemption. |
| Backend APIs | `POST /api/gift-cards`, `POST /api/gift-cards/redeem`, `GET /api/gift-cards/{code}/balance`. |
| Database changes | `gift_cards` (hashed code), `gift_card_transactions`. |
| Events and integrations | Postings to accounting (liability). |
| Dependencies | PLT-04, ACC-01. |
| Security considerations | Codes hashed; brute-force rate limits on balance check. |
| Business value | Revenue and retention. |
| Acceptance criteria | Balance never goes negative under concurrent redemptions. |

### Storefront

#### SF-01 Storefront tenant resolution

| Field | Detail |
|---|---|
| Type | EXTEND (`order-service` checkout accepts a `storefront` slug; `product-service` storefront) |
| Priority / Complexity | P1 / M |
| Business problem | All orders land in the legacy tenant because no UI sends the storefront (K4, B-25). |
| Proposed functionality | Resolve tenant from the request host (custom domain or subdomain) or path slug; storefront UI sends it on cart and checkout. |
| User roles | Customers; tenant admins configure domains. |
| Frontend screens | Domain settings; storefront bootstraps tenant on load. |
| Backend APIs | `GET /api/storefront/resolve?host=`; `PUT /api/storefront/domains`. |
| Database changes | `storefront_domains` (unique host). |
| Events and integrations | Order events carry `tenantId`. |
| Dependencies | PLT-03 (cart and order slices). |
| Security considerations | Domain ownership verification (DNS TXT); unknown hosts get no tenant. |
| Business value | Precondition for any second merchant. |
| Acceptance criteria | Orders from two domains land in two tenants; cross-tenant cart access fails. |
| Owning service | product-service (resolution); order-service (checkout). |
| Tenant isolation | This feature establishes it for storefront traffic. |
| Failure handling | Resolution cached; unresolved host returns a neutral 404 page. |
| Observability | Orders per tenant; unresolved-host count. |
| Testing | Two-tenant checkout integration test. |

#### SF-02 Pricing quote and promo validation

| Field | Detail |
|---|---|
| Type | EXTEND (`product-service` pricing and promotions; `PricingClient` in order-service) |
| Priority / Complexity | P1 / M |
| Business problem | Promo codes are hard-coded in the UI and not validated before checkout (K24, B-14). |
| Proposed functionality | Customer-facing quote endpoint returning line prices, applied promotions and totals; checkout re-prices server-side with the same engine. |
| User roles | Customers. |
| Frontend screens | Cart and checkout call the quote API; remove hard-coded codes from `mfe-order`. |
| Backend APIs | `POST /api/pricing/quote`. |
| Database changes | None (promotions exist). |
| Events and integrations | None. |
| Dependencies | SF-01 (tenant). |
| Security considerations | Rate-limit quote calls to slow code guessing; never trust client totals. |
| Business value | Revenue correctness. |
| Acceptance criteria | An invalid code is refused; checkout total equals the last quote or the order is re-quoted. |
| Owning service | product-service. |
| Tenant isolation | Promotions filtered by tenant. |
| Failure handling | If pricing is down, checkout fails rather than charging an unpriced cart. |
| Observability | Quote latency and invalid-code rate. |
| Testing | Promotion rules unit tests; checkout re-pricing integration test. |

#### SF-03 Shipping zones and rates

| Field | Detail |
|---|---|
| Type | NEW (owning service: order-service) |
| Priority / Complexity | P1 / L |
| Business problem | No shipping charges or zones (no code found). |
| Proposed functionality | Zones by country and postcode; flat, weight-based and free-over-threshold rates; carrier rate adapters later; pickup option from PLT-14 locations. |
| User roles | Tenant admins; customers. |
| Frontend screens | Shipping settings; checkout shipping step. |
| Backend APIs | `GET/PUT /api/shipping/zones`, `POST /api/shipping/rates/quote`. |
| Database changes | `shipping_zones`, `shipping_rates`. |
| Events and integrations | Rate included in the order and in delivery creation (DEL-01). |
| Dependencies | SF-02 (quote), SF-01. |
| Security considerations | Rates recomputed server-side. |
| Business value | Required to sell physical goods. |
| Acceptance criteria | The correct rate applies by address; free-shipping threshold works. |
| Owning service | order-service. |
| Tenant isolation | Zones per tenant. |
| Failure handling | Carrier adapter failures fall back to configured flat rates. |
| Observability | Quote failures by carrier. |
| Testing | Zone matching unit tests; checkout integration test. |

#### SF-04 Multi-currency and tax-inclusive display

| Field | Detail |
|---|---|
| Type | NEW (owning service: product-service) |
| Priority / Complexity | P2 / L |
| Business problem | Single currency; no tax-inclusive pricing for VAT regions. |
| Proposed functionality | Presentment currencies with rates; rounding rules; tax-inclusive display per region; settlement currency stays the tenant base. |
| User roles | Tenant admins; customers. |
| Frontend screens | Currency selector; currency settings. |
| Backend APIs | `GET /api/pricing/currencies`; quote accepts a currency. |
| Database changes | `exchange_rates`, `tenant_currencies`. |
| Events and integrations | Rate feed adapter; accounting records both amounts. |
| Dependencies | SF-02, ACC-02. |
| Security considerations | Rates from a trusted source; stale rates block checkout. |
| Business value | International sales. |
| Acceptance criteria | Totals in presentment and base currency reconcile to the cent. |

### Inventory

#### INV-01 Atomic order reservations

| Field | Detail |
|---|---|
| Type | EXTEND (`StockReservation` in inventory-service and stock-service) |
| Priority / Complexity | P0 / L |
| Business problem | Reservations are not tied to order creation and can oversell (gap doc Phase 4). |
| Proposed functionality | Order creation reserves stock through a reservation API with an idempotency key; payment success commits; failure or timeout releases. One authoritative stock owner (resolve inventory-service vs stock-service overlap). |
| User roles | System; inventory managers see reservations. |
| Frontend screens | Reservation column in the inventory UI. |
| Backend APIs | `POST /internal/reservations`, `POST /internal/reservations/{id}/commit`, `POST /internal/reservations/{id}/release`. |
| Database changes | Reservation expiry and idempotency columns (new migration). |
| Events and integrations | Consumes `payment.intent.succeeded` and `payment.intent.failed`; emits `inventory.reservation.expired`. |
| Dependencies | PLT-05; PLT-04 for commit; PLT-03. |
| Security considerations | Internal endpoints use the service token (or PLT-02 service tokens). |
| Business value | Prevents overselling. |
| Acceptance criteria | 100 concurrent orders for 10 units produce exactly 10 reservations; expired reservations release stock. |
| Owning service | inventory-service (after consolidating the stock owner). |
| Tenant isolation | Reservations keyed by tenant and location. |
| Failure handling | Reservation timeouts release automatically; duplicate requests return the same reservation. |
| Observability | Oversell attempts and expiry count. |
| Testing | Concurrency test on Postgres; idempotency test. |

#### INV-02 Reorder automation

| Field | Detail |
|---|---|
| Type | EXTEND (stock-service low-stock endpoint; procurement purchase orders) |
| Priority / Complexity | P2 / M |
| Business problem | Low stock is reported but no purchase is suggested. |
| Proposed functionality | Reorder points and quantities per item and location; suggested purchase orders drafted for approval (PLT-11). |
| User roles | Inventory and purchasing managers. |
| Frontend screens | Reorder rules; suggestions list. |
| Backend APIs | `GET/PUT /api/inventory/reorder-rules`, `GET /api/procurement/suggestions`. |
| Database changes | `reorder_rules`. |
| Events and integrations | `inventory.low_stock` drives suggestions. |
| Dependencies | PLT-11, PLT-14. |
| Security considerations | Suggestions never auto-send without approval. |
| Business value | Less manual work; fewer stock-outs. |
| Acceptance criteria | Crossing a reorder point drafts one suggestion per supplier. |

#### INV-03 Cycle counts and barcode workflows

| Field | Detail |
|---|---|
| Type | EXTEND (`StockAdjustment` in inventory-service) |
| Priority / Complexity | P2 / M |
| Business problem | Counting stock is manual; no scanning. |
| Proposed functionality | Scheduled cycle counts by location; scan-to-count and scan-to-receive using camera barcode scanning; variance approval. |
| User roles | Warehouse staff; managers. |
| Frontend screens | Count sheet (mobile); variance review. |
| Backend APIs | `POST /api/inventory/counts`, `POST /api/inventory/counts/{id}/lines`, `POST /api/inventory/counts/{id}/approve`. |
| Database changes | `stock_counts`, `stock_count_lines`. |
| Events and integrations | Approved variance creates adjustments and accounting impact. |
| Dependencies | PLT-14, ACC-01. |
| Security considerations | Approver differs from counter. |
| Business value | Inventory accuracy. |
| Acceptance criteria | Approved count updates stock and posts the variance. |

### Accounting

#### ACC-01 Posted-line immutability

| Field | Detail |
|---|---|
| Type | EXTEND (reversal by compensation already exists) |
| Priority / Complexity | P0 / S |
| Business problem | Posted ledger lines can be changed directly in the database (B-29). |
| Proposed functionality | Database trigger refusing UPDATE and DELETE on posted lines; corrections only by reversal; period-closed check in the database as well as the service. |
| User roles | None new. |
| Frontend screens | None. |
| Backend APIs | Unchanged. |
| Database changes | New migration adding the trigger. |
| Events and integrations | None. |
| Dependencies | Scratch Postgres for testing. |
| Security considerations | Migration owner role must not be used by the application. |
| Business value | Audit-grade books. |
| Acceptance criteria | Direct UPDATE of a posted line fails; reversal still works. |
| Owning service | accounting-service. |
| Tenant isolation | Unchanged (tenant-scoped since V19). |
| Failure handling | Trigger error surfaces as 409 if reached through the API. |
| Observability | Count of refused modifications. |
| Testing | Postgres-gated test alongside the existing tenant-isolation test. |

#### ACC-02 PSP settlement reconciliation

| Field | Detail |
|---|---|
| Type | EXTEND (bank reconciliation exists in accounting-service) |
| Priority / Complexity | P1 / L |
| Business problem | Payments, refunds and fees from a PSP are not reconciled to the ledger. |
| Proposed functionality | Import PSP settlement reports; match to payment and refund postings; post fees; exception queue for mismatches. |
| User roles | Tenant finance. |
| Frontend screens | Reconciliation workspace in `mfe-accounting`. |
| Backend APIs | `POST /api/accounting/settlements/import`, `GET /api/accounting/settlements/{id}/exceptions`. |
| Database changes | `settlement_batches`, `settlement_lines`, `reconciliation_matches`. |
| Events and integrations | Consumes payment events; settlement files from payment-service. |
| Dependencies | PLT-04, ACC-01. |
| Security considerations | Only finance permission; imports idempotent by batch id. |
| Business value | Accurate books; less month-end work. |
| Acceptance criteria | A settlement with fees reconciles to zero difference; mismatches appear in the queue. |
| Owning service | accounting-service. |
| Tenant isolation | Batches per tenant. |
| Failure handling | Partial imports roll back; re-import is idempotent. |
| Observability | Unreconciled amount per tenant. |
| Testing | Matching unit tests; import idempotency test. |

#### ACC-03 Ledger exports

| Field | Detail |
|---|---|
| Type | EXTEND (`accounting-service` ledger and CSV handling) |
| Priority / Complexity | P2 / M |
| Business problem | Tenants using external accountants cannot export in standard formats. |
| Proposed functionality | General-ledger and trial-balance exports in CSV and common import formats; scheduled through PLT-12. |
| User roles | Tenant finance. |
| Frontend screens | Export dialog. |
| Backend APIs | `POST /api/accounting/exports`. |
| Database changes | None beyond PLT-12. |
| Events and integrations | Uses PLT-13 for files. |
| Dependencies | PLT-12, PLT-13. |
| Security considerations | Formula-injection escaping (existing practice in analytics). |
| Business value | Removes manual rekeying. |
| Acceptance criteria | An exported trial balance sums to zero. |

### Delivery

#### DEL-01 Delivery created on fulfilment

| Field | Detail |
|---|---|
| Type | EXTEND (`delivery-service` with outbox; status sync to order-service) |
| Priority / Complexity | P1 / M |
| Business problem | Deliveries are not created automatically when orders are fulfilled (gap doc Phase 7). |
| Proposed functionality | order-service emits `order.fulfilment.ready`; delivery-service creates the delivery with origin location, address and shipping method; customer notified with tracking link. |
| User roles | System; dispatchers. |
| Frontend screens | None new; dispatcher list shows auto-created deliveries. |
| Backend APIs | None new. |
| Database changes | `deliveries.source_event_id` unique (idempotency). |
| Events and integrations | Consumes `order.fulfilment.ready`; emits `delivery.created`. |
| Dependencies | PLT-05 in order-service; SF-03 for the method. |
| Security considerations | Address data minimised in events (reference id plus delivery-relevant fields only). |
| Business value | Removes manual dispatch entry. |
| Acceptance criteria | Fulfilling an order creates exactly one delivery and one customer notification. |
| Owning service | delivery-service. |
| Tenant isolation | Delivery carries the order's tenant. |
| Failure handling | Duplicate events ignored by `source_event_id`. |
| Observability | Time from fulfilment to delivery creation. |
| Testing | Event consumer test; end-to-end order-to-delivery test. |

#### DEL-02 Route planning and batching

| Field | Detail |
|---|---|
| Type | NEW (owning service: delivery-service) |
| Priority / Complexity | P2 / L |
| Business problem | Dispatchers plan routes manually. |
| Proposed functionality | Batch deliveries by zone and time window; suggested stop order through a routing provider adapter; courier assignment. |
| User roles | Dispatchers; couriers. |
| Frontend screens | Route board; courier route view (PLT-18). |
| Backend APIs | `POST /api/delivery/routes/plan`, `PUT /api/delivery/routes/{id}/assign`. |
| Database changes | `delivery_routes`, `route_stops`. |
| Events and integrations | Routing provider adapter. |
| Dependencies | DEL-01, PLT-14. |
| Security considerations | Courier sees only assigned stops. |
| Business value | Lower delivery cost per order. |
| Acceptance criteria | A batch of deliveries produces an ordered route within time windows. |

### Vendor / Restaurant

#### VR-01 Multi-vendor domain and onboarding

| Field | Detail |
|---|---|
| Type | NEW (owning service: vendor-service, new) |
| Priority / Complexity | P2 / XL |
| Business problem | No vendor accounts; marketplaces and food aggregators are a key segment. |
| Proposed functionality | Vendor accounts under a tenant; onboarding with documents (PLT-13); vendor-owned products and stock; vendor portal (`mfe-vendor`). |
| User roles | Tenant (marketplace operator); vendor staff. |
| Frontend screens | Vendor onboarding, vendor portal, operator vendor list. |
| Backend APIs | `POST /api/vendors`, `GET /api/vendors/{id}`, vendor-scoped catalog APIs. |
| Database changes | `vendors`, `vendor_users`; `vendor_id` on products and order lines. |
| Events and integrations | `vendor.approved`; order lines split by vendor. |
| Dependencies | PLT-03, EA-02, PLT-13. |
| Security considerations | Vendor isolation inside a tenant (second scoping level). |
| Business value | Opens the marketplace segment. |
| Acceptance criteria | A vendor sees only its own products and orders. |

#### VR-02 Commissions and payouts

| Field | Detail |
|---|---|
| Type | NEW (owning services: payment-service, accounting-service) |
| Priority / Complexity | P2 / L |
| Business problem | No commission calculation or vendor payouts. |
| Proposed functionality | Commission rules per vendor or category; payout schedules; statements; payouts through the PSP. |
| User roles | Marketplace finance; vendors. |
| Frontend screens | Commission rules; payout statements. |
| Backend APIs | `GET/PUT /api/vendors/{id}/commission`, `GET /api/vendors/{id}/payouts`. |
| Database changes | `commission_rules`, `payouts`, `payout_lines`. |
| Events and integrations | Postings to accounting; approvals through PLT-11. |
| Dependencies | VR-01, PLT-04, ACC-02, PLT-11. |
| Security considerations | Payout destination changes require re-verification. |
| Business value | Marketplace revenue. |
| Acceptance criteria | Statement equals sales minus commission minus refunds. |

#### VR-03 Kitchen display and throttling

| Field | Detail |
|---|---|
| Type | EXTEND (restaurant states in `OrderStateMachine`) |
| Priority / Complexity | P2 / M |
| Business problem | Restaurant orders have states but no kitchen screen or capacity control. |
| Proposed functionality | Kitchen display with tickets by station; bump to advance state; prep-time estimates; pause or throttle online orders when the kitchen is busy. |
| User roles | Kitchen staff; restaurant managers. |
| Frontend screens | Kitchen display (tablet layout). |
| Backend APIs | `GET /api/orders/kitchen?location=`, `POST /api/orders/{id}/kitchen/bump`, `PUT /api/orders/settings/throttle`. |
| Database changes | `kitchen_stations`, prep-time columns. |
| Events and integrations | Live updates over SSE or WebSocket. |
| Dependencies | PLT-14. |
| Security considerations | Station devices use limited-scope tokens. |
| Business value | Restaurant throughput. |
| Acceptance criteria | A new order appears on the display within 2 s; throttling stops new orders. |

### CRM

#### CRM-01 Customer 360 timeline

| Field | Detail |
|---|---|
| Type | EXTEND (`crm-service` records and Kafka consumer) |
| Priority / Complexity | P1 / M |
| Business problem | Customer interactions are spread across orders, returns, loyalty and (soon) support. |
| Proposed functionality | Timeline built from events: orders, returns, loyalty, tickets, notes; lifetime value and last activity. |
| User roles | Tenant staff with `crm:read`. |
| Frontend screens | Timeline tab in `mfe-crm` and in EA-01 customer detail. |
| Backend APIs | `GET /api/crm/customers/{id}/timeline`. |
| Database changes | `crm_timeline_entries` with `tenant_id`. |
| Events and integrations | Consumes order, return, loyalty and support events. |
| Dependencies | PLT-03 (crm slice), PLT-05, CS-01 for tickets. |
| Security considerations | Personal data access audited. |
| Business value | Better service and retention. |
| Acceptance criteria | A new order appears on the timeline within a minute; other tenants' entries never appear. |
| Owning service | crm-service. |
| Tenant isolation | Entries keyed by tenant. |
| Failure handling | Consumer is idempotent on `eventId`; lag does not block other services. |
| Observability | Consumer lag. |
| Testing | Consumer tests; cross-tenant test. |

#### CRM-02 Consent and communication preferences

| Field | Detail |
|---|---|
| Type | NEW (owning services: crm-service, notification-service) |
| Priority / Complexity | P1 / M |
| Business problem | No record of marketing consent; required before any campaign (privacy law in most markets). |
| Proposed functionality | Consent records by purpose and channel with timestamp and source; preference centre; data export and erasure requests. |
| User roles | Customers; tenant admins. |
| Frontend screens | Preference centre; consent log in customer detail. |
| Backend APIs | `GET/PUT /api/crm/customers/{id}/consents`, `POST /api/crm/privacy-requests`. |
| Database changes | `consents`, `privacy_requests`. |
| Events and integrations | `crm.consent.changed` consumed by notification-service. |
| Dependencies | PLT-03, PLT-08. |
| Security considerations | Consent history append-only. |
| Business value | Legal compliance; enables marketing. |
| Acceptance criteria | Withdrawn consent stops marketing messages; erasure request completes across services. |
| Owning service | crm-service. |
| Tenant isolation | Per tenant. |
| Failure handling | Erasure is tracked per service until all confirm. |
| Observability | Open privacy requests by age. |
| Testing | Consent enforcement test in notification-service. |

#### CRM-03 Segments and automated journeys

| Field | Detail |
|---|---|
| Type | EXTEND (segment code in crm and product; abandoned-cart reminders in cart-service) |
| Priority / Complexity | P2 / L |
| Business problem | Abandoned-cart reminders exist, but there is no general segmentation or journey builder. |
| Proposed functionality | Rule-based segments (spend, recency, location); journeys triggered by events with waits and branches; messages through PLT-08; absorb the cart reminder into a journey. |
| User roles | Tenant marketers. |
| Frontend screens | Segment builder; journey canvas; results. |
| Backend APIs | `GET/POST /api/crm/segments`, `GET/POST /api/crm/journeys`. |
| Database changes | `segments`, `journeys`, `journey_runs`. |
| Events and integrations | Consumes commerce events; sends through PLT-08. |
| Dependencies | CRM-01, CRM-02, PLT-08. |
| Security considerations | Respects consent; frequency caps. |
| Business value | Retention and revenue. |
| Acceptance criteria | A cart abandoned for 1 hour triggers the journey once for consenting customers. |

### Tester / Quality Center

#### QC-01 Synthetic journey monitoring

| Field | Detail |
|---|---|
| Type | EXTEND (`qa-service` runner; `scripts/smoke-test.sh`) |
| Priority / Complexity | P2 / M |
| Business problem | Smoke tests exist as scripts but nothing runs them continuously. |
| Proposed functionality | Scheduled synthetic journeys (browse, add to cart, checkout in a test tenant) with alerting through Alertmanager. |
| User roles | Platform operators; QA. |
| Frontend screens | Journey results in the Quality Center. |
| Backend APIs | `POST /api/qa/synthetics`, `GET /api/qa/synthetics/{id}/runs`. |
| Database changes | `synthetic_checks`, `synthetic_runs`. |
| Events and integrations | Results exported as Prometheus metrics. |
| Dependencies | Container runtime (F-14). |
| Security considerations | Runs against a dedicated test tenant only. |
| Business value | Detects outages before customers. |
| Acceptance criteria | A broken checkout raises an alert within one interval. |

#### QC-02 Tenant-scoped test plans

| Field | Detail |
|---|---|
| Type | EXTEND (`qa-service`) · **BLOCKED** on the platform-only or tenant decision |
| Priority / Complexity | P2 / M |
| Business problem | The Quality Center is platform-only; tenants with custom storefronts cannot record their own test plans. |
| Proposed functionality | Test plans and run history per tenant, if the product decision is "tenant". |
| User roles | Tenant QA. |
| Frontend screens | Test plan editor. |
| Backend APIs | `GET/POST /api/qa/plans`. |
| Database changes | `tenant_id` on qa tables. |
| Events and integrations | None. |
| Dependencies | Decision; PLT-03. |
| Security considerations | Runner execution stays platform-only. |
| Business value | Enterprise customers expect it. |
| Acceptance criteria | Defined once the decision is made. |

### Customer Support

#### CS-01 Ticketing core

| Field | Detail |
|---|---|
| Type | NEW (owning service: support-service, new; frontend `mfe-support`, new) |
| Priority / Complexity | P1 / L |
| Business problem | Customer Support is one of the ten applications and has no implementation. |
| Proposed functionality | Tickets with status (new, open, pending, resolved, closed), priority, assignee, tags, internal notes and public replies; SLA targets per priority; queues per team. |
| User roles | Support agents, team leads, tenant admins (`support:tickets:read`, `support:tickets:manage`, `support:settings:manage`). |
| Frontend screens | Ticket list with filters; ticket detail with conversation and side panel (customer, orders); SLA dashboard. |
| Backend APIs | `GET/POST /api/support/tickets`, `GET/PATCH /api/support/tickets/{id}`, `POST /api/support/tickets/{id}/messages`, `GET /api/support/queues`. |
| Database changes | New `support_db`: `tickets`, `ticket_messages`, `ticket_events`, `sla_policies`, `queues`; all with `tenant_id`. |
| Events and integrations | Emits `support.ticket.created`, `support.ticket.resolved` (CRM-01, PLT-08). |
| Dependencies | PLT-03 pattern; PLT-05; PLT-08 for email replies. |
| Security considerations | Internal notes never visible to customers; attachment scanning through PLT-13. |
| Business value | Completes the tenth application; retention. |
| Acceptance criteria | Agents see only their tenant's tickets; SLA breach is flagged; public replies notify the customer. |
| Owning service | support-service. |
| Tenant isolation | Built in from the first migration. |
| Failure handling | Email sending through the outbox; ticket writes never depend on email success. |
| Observability | Open tickets by age; SLA breaches. |
| Testing | Cross-tenant test; SLA calculation unit tests; status transition tests. |

#### CS-02 Customer portal and email-to-ticket

| Field | Detail |
|---|---|
| Type | NEW (owning service: support-service) |
| Priority / Complexity | P2 / M |
| Business problem | Customers cannot raise or follow tickets. |
| Proposed functionality | Storefront "My requests" page; inbound email address per tenant creating tickets; threading by reference. |
| User roles | Customers. |
| Frontend screens | My requests list and detail in the storefront. |
| Backend APIs | `GET/POST /api/support/my/tickets`; inbound email webhook. |
| Database changes | `inbound_addresses`. |
| Events and integrations | Email provider inbound parse. |
| Dependencies | CS-01, SF-01. |
| Security considerations | Inbound email verified (SPF/DKIM results); customers see only their own tickets. |
| Business value | Self-service. |
| Acceptance criteria | A reply by email appends to the right ticket. |

#### CS-03 Knowledge base

| Field | Detail |
|---|---|
| Type | NEW (owning service: support-service) |
| Priority / Complexity | P2 / M |
| Business problem | No self-help content. |
| Proposed functionality | Articles with categories, search, draft and publish, public and internal visibility; suggested articles on ticket creation. |
| User roles | Agents (authors); customers (readers). |
| Frontend screens | Article editor; public help centre. |
| Backend APIs | `GET/POST /api/support/articles`, `GET /api/support/articles/search`. |
| Database changes | `kb_articles`, `kb_categories`. |
| Events and integrations | None. |
| Dependencies | CS-01. |
| Security considerations | Sanitised rich text. |
| Business value | Ticket deflection. |
| Acceptance criteria | Published articles searchable; internal articles hidden from customers. |

#### CS-04 Order and refund actions from tickets

| Field | Detail |
|---|---|
| Type | EXTEND (order and returns APIs in `order-service`) |
| Priority / Complexity | P2 / M |
| Business problem | Agents must switch applications to act on an order. |
| Proposed functionality | From a ticket: view the order, start a return, request a refund (through approval PLT-11 above a threshold). |
| User roles | Agents with the matching order permissions. |
| Frontend screens | Ticket side panel actions. |
| Backend APIs | Calls existing order and returns endpoints; no duplicates. |
| Database changes | `ticket_links` (ticket to order or return). |
| Events and integrations | Ticket timeline records the action. |
| Dependencies | CS-01, PLT-11. |
| Security considerations | Agent permissions checked by order-service, not by support-service. |
| Business value | Faster resolution. |
| Acceptance criteria | A refund requested from a ticket appears in the returns workflow with the ticket reference. |

---

## 8. Deferred and rejected ideas

| Idea | Decision | Reason |
|---|---|---|
| Per-application notification or approval code | Rejected | Duplicates PLT-08 and PLT-11 (§5.1). |
| A second analytics store in each service | Rejected | Reports belong to analytics-service (PLT-12). |
| Native mobile apps | Deferred | PWA first (PLT-18); revisit after usage data. |
| Live chat widget | Deferred | After CS-01 and CS-02; needs WebSocket infrastructure. |
| Headless storefront API marketplace | Deferred | After PLT-10 and PLT-16. |
| Point-of-sale hardware integration | Deferred | No hardware partner chosen. |
| Cluster-wide login throttling | Deferred | The gateway limiter added for PLT-01 counts per instance; move it to Redis with PLT-06. |

---

## 9. Implementation record

Implemented in this change (details in [docs/status/IMPLEMENTATION_PROGRESS.md](../status/IMPLEMENTATION_PROGRESS.md)):

- **PLT-01, account lockout.** `user-service`: migration `V16__account_lockout.sql`, `LoginAttemptService`, `AuthController` (both login endpoints), three atomic `UserRepository` updates, OpenAPI responses 401 and 423, and five integration tests. User-service suite: 17 tests, 0 failures. The V16 migration was not run against Postgres; the tests build the schema from entities with Flyway off.
- **PLT-01, gateway throttling.** `api-gateway` `LoginRateLimitFilter`: 20 sign-in requests per client address per minute across both login endpoints, then `429` with `Retry-After`. Three tests in `LoginRateLimitTest`; gateway suite 41 tests, 0 failures. Counters are per instance.
- **PLT-05, shared outbox (partial).** New module `backend/outbox` (`ucp-outbox`): `OutboxWriter`, scheduled `OutboxPublisher` with exponential backoff, claim-based multi-instance safety, retention cleanup, portable SQL. Adopted by cart-service and loyalty-service with unchanged payloads (V2 migrations). Tests: module 7, cart 8, loyalty 9, all passing. Then adopted by order-service (TEMPLATE) and inventory and stock (new RAW mode, byte-for-byte equal to their previous JsonSerializer output). Tests: module 10, order 100, inventory 29, stock 11. Remaining: the event envelope, backlog metrics, consumer idempotency review.

Not implemented: every other entry in this catalog. The reasons are in the roadmap: most P0 items wait on decisions (PSP, key management), and the rest follow in dependency order.
