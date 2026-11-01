# Feature Expansion Roadmap

Phases for the proposals in [ADVANCED_FEATURE_CATALOG.md](ADVANCED_FEATURE_CATALOG.md). Feature IDs (`PLT-`, `SA-`, `EA-`, `SF-`, `INV-`, `ACC-`, `DEL-`, `VR-`, `CRM-`, `QC-`, `CS-`) refer to that catalog.
Phase 1 here covers the same P0 work as Phase 0 of [docs/status/COMPLETION_ROADMAP.md](../status/COMPLETION_ROADMAP.md). That document still governs bug fixes and technical debt. This one governs new capability.

Complexity uses the catalog scale: S (under 1 week), M (1–3 weeks), L (3–6 weeks), XL (over 6 weeks), for one experienced developer including tests. Phase totals are the sum of item sizes. Items without dependencies on each other can run in parallel, so calendar time is shorter.

## Definition of Done (every item)

1. New behaviour has passing unit and integration tests, plus a cross-tenant test for any tenant-scoped data.
2. Schema changes are new Flyway migrations, verified against a scratch Postgres as well as H2.
3. Events use the envelope in catalog §5.3 and are published through the outbox once PLT-05 exists.
4. OpenAPI annotations describe new endpoints, including error responses.
5. User-facing behaviour is documented, and `docs/status/IMPLEMENTATION_PROGRESS.md` and the catalog's status column are updated.
6. Frontend changes pass `npm run build` and `tsc --noEmit` for the affected workspace.

---

## Phase 1: Critical foundations

| Item | Size | Depends on | Status |
|---|---|---|---|
| PLT-01 Account lockout | S | — | **Done (this change)** |
| PLT-01 Gateway per-IP throttling | S | Redis in the gateway | Pending |
| Credential removal from tracked files (status roadmap 0.1) | S | — | Pending |
| Credential rotation | S | Owner confirms live values | BLOCKED |
| PLT-02 Asymmetric JWT | M | Key-management decision | BLOCKED |
| PLT-03 Tenant isolation framework, nine slices | L | — (PLT-02 preferred) | Pending |
| PLT-05 Shared transactional outbox | M | — | Pending |
| ACC-01 Posted-line immutability | S | Scratch Postgres | Pending |
| PLT-04 Payment service | XL | PSP decision, PLT-05 | BLOCKED |

- **Complexity:** about 4 S + 2 M + 1 L + 1 XL of remaining work once unblocked.
- **Deliverables:** lockout and throttling; no literal credentials in tracked files; tokens verifiable only with the user-service public key; nine services tenant-scoped; one outbox module adopted by six producers; immutable posted ledger lines; a payment service with one PSP.
- **Tests:**
  - Lockout and 429 tests.
  - Forged-key and wrong-audience tests.
  - A cross-tenant test per service.
  - Outbox broker-down test.
  - Trigger test on Postgres.
  - Duplicate-webhook test.
- **Acceptance:** a security review finds no shared signing secret, no cross-tenant read, and no unauthenticated payment confirmation.

## Phase 2: Core commerce enhancements

| Item | Size | Depends on |
|---|---|---|
| INV-01 Atomic order reservations | L | PLT-05, PLT-04 (commit on payment) |
| SF-01 Storefront tenant resolution | M | PLT-03 (cart and order slices) |
| SF-02 Pricing quote and promo validation | M | SF-01 |
| SF-03 Shipping zones and rates | L | SF-02 |
| ACC-02 PSP settlement reconciliation | L | PLT-04, ACC-01 |
| DEL-01 Delivery created on fulfilment | M | PLT-05, SF-03 |
| PLT-07 Plans, entitlements and usage limits | M | PLT-03 |
| SA-01 Tenant lifecycle and suspension | M | — |
| SA-02 Plan and entitlement console | M | PLT-07 |
| EA-01 Customer management | M | PLT-03, CRM-02 (anonymisation) |
| EA-02 Tenant staff and roles | M | PLT-07 (seats) |

- **Complexity:** 3 L + 8 M.
- **Deliverables:** a second merchant can sell on its own domain with correct prices, shipping and payment. Stock is reserved atomically. Deliveries appear automatically. Operators can suspend tenants and assign plans. Tenant owners manage staff and customers.
- **Tests:** two-tenant checkout end to end; 100-concurrent-order reservation test; re-pricing test; zone matching; settlement reconciliation; suspended-tenant refusal; seat limit.
- **Acceptance:** an order placed on tenant B's domain is priced, shipped, paid, reserved and delivered entirely within tenant B, and does not appear in tenant A.

## Phase 3: Operational improvements

| Item | Size | Depends on |
|---|---|---|
| PLT-06 Session lifecycle (refresh, revocation, reset; MFA follow-on) | L | PLT-02, Redis, PLT-08 for email |
| PLT-08 Notification center | L | PLT-03, PLT-05 |
| CS-01 Ticketing core | L | PLT-03 pattern, PLT-05, PLT-08 |
| PLT-11 Approval workflow engine | L | PLT-05 |
| PLT-13 Document management | M | PLT-03 |
| PLT-14 Multi-location operations | L | INV-01 |
| INV-02 Reorder automation | M | PLT-11, PLT-14 |
| INV-03 Cycle counts and barcode workflows | M | PLT-14, ACC-01 |
| DEL-02 Route planning and batching | L | DEL-01, PLT-14 |
| VR-03 Kitchen display and throttling | M | PLT-14 |
| QC-01 Synthetic journey monitoring | M | Container runtime |
| CS-04 Order and refund actions from tickets | M | CS-01, PLT-11 |

- **Complexity:** 6 L + 6 M.
- **Deliverables:** users can reset passwords and are logged out reliably; one notification system with templates and an inbox; the tenth application (Customer Support) exists with SLAs; approvals for purchase orders and refunds; generated documents; locations shared across inventory, orders and delivery; automated reorder suggestions; kitchen display; continuous checkout monitoring.
- **Tests:** refresh reuse detection; revoked-token refusal; template and consent tests; ticket cross-tenant and SLA tests; self-approval refusal; document link expiry; route ordering; synthetic alert.
- **Acceptance:** each workflow in the phase completes without an operator editing data by hand.
- **Note:** PLT-06 and CS-01 are P1 but sit here because they depend on PLT-02 and PLT-08.

## Phase 4: Customer engagement and marketing

| Item | Size | Depends on |
|---|---|---|
| CRM-02 Consent and communication preferences | M | PLT-03, PLT-08 |
| CRM-01 Customer 360 timeline | M | PLT-03, PLT-05, CS-01 |
| CRM-03 Segments and automated journeys | L | CRM-01, CRM-02, PLT-08 |
| EA-03 Gift cards and store credit | L | PLT-04, ACC-01 |
| CS-02 Customer portal and email-to-ticket | M | CS-01, SF-01 |
| CS-03 Knowledge base | M | CS-01 |
| SF-04 Multi-currency and tax-inclusive display | L | SF-02, ACC-02 |

- **Complexity:** 3 L + 4 M.
- **Deliverables:** consent-respecting marketing journeys (the abandoned-cart reminder becomes one); a complete customer timeline; gift cards; self-service support; international pricing.
- **Tests:** a withdrawn consent stops messages; journeys fire once; gift-card concurrency; email threading; currency reconciliation to the cent.
- **Acceptance:** a marketer can build a win-back journey for a segment, and only consenting customers receive it.

## Phase 5: AI and business intelligence

| Item | Size | Depends on |
|---|---|---|
| PLT-12 Reporting and export engine (replaces copied order entities) | L | PLT-03, PLT-05, PLT-13 |
| PLT-15 Central audit and activity timeline | M | PLT-05 |
| ACC-03 Ledger exports | M | PLT-12, PLT-13 |
| PLT-17 AI assistant (backend for the existing panel) | L | PLT-12 |

- **Complexity:** 2 L + 2 M.
- **Deliverables:** tenant-scoped reports with schedules; one activity log; accountant exports; an assistant that answers from tenant reports with citations.
- **Tests:** report tenant filter; export expiry; trial balance sums to zero; prompt-injection test showing no cross-tenant leakage.
- **Acceptance:** `accounting-service` and `analytics-service` no longer contain their own order entities; every analytics query is tenant-filtered.

## Phase 6: Integrations and extensibility

| Item | Size | Depends on |
|---|---|---|
| PLT-10 Outbound webhooks and tenant API keys | L | PLT-05, PLT-03 |
| PLT-16 Extension framework and app marketplace | XL | PLT-02, PLT-10 |
| PLT-18 Mobile and PWA capabilities | L | PLT-08 |

- **Complexity:** 2 L + 1 XL.
- **Deliverables:** tenants integrate with signed webhooks and scoped keys; partners publish OAuth apps; an installable storefront; courier and kitchen mobile views.
- **Tests:** signature verification; SSRF refusal for private addresses; retry and auto-disable; consented-scope enforcement; offline load.
- **Acceptance:** a third-party app installed by tenant A cannot read tenant B, or any scope tenant A did not grant.

## Phase 7: Enterprise capabilities

| Item | Size | Depends on |
|---|---|---|
| PLT-09 SaaS subscription billing | L | PLT-04, PLT-07, SA-01 |
| VR-01 Multi-vendor domain and onboarding | XL | PLT-03, EA-02, PLT-13 |
| VR-02 Commissions and payouts | L | VR-01, PLT-04, ACC-02, PLT-11 |
| SA-03 Audited impersonation | M | PLT-02, PLT-15 |
| QC-02 Tenant-scoped test plans | M | Product decision (BLOCKED) |
| PLT-06 MFA follow-on, required for admin roles | M | PLT-06 |

- **Complexity:** 2 L + 1 XL + 3 M.
- **Deliverables:** the platform bills tenants automatically; marketplace operators onboard vendors and pay them; support can view as a tenant with a full audit trail; admins must use MFA.
- **Tests:** proration and dunning; vendor isolation inside a tenant; payout statement equals sales minus commission and refunds; impersonation write refusal; MFA enforcement.
- **Acceptance:** an enterprise security questionnaire can be answered "yes" for MFA, audit, tenant isolation, key management and least privilege.

---

## Recommended execution order

1. Credential removal from tracked files, PLT-01 throttling and PLT-05 outbox. None is blocked, and the last two unblock later work.
2. PLT-03 tenant slices, starting with crm and cart (needed by SF-01).
3. ACC-01 immutability, once a scratch Postgres is available.
4. Decisions on PSP and key management, so that PLT-04 and PLT-02 can start.
5. Phase 2 in table order.

## Decisions required before work can start

| Decision | Blocks |
|---|---|
| PSP provider and account model (per tenant or platform) | PLT-04, INV-01 commit step, ACC-02, EA-03, PLT-09, VR-02 |
| Signing-key storage (Vault or Kubernetes Secret) | PLT-02, PLT-06, PLT-16, SA-03 |
| Whether committed credentials are live | Credential rotation |
| Quality Center: platform-only or per tenant | QC-02 |
| Single stock owner (inventory-service or stock-service) | INV-01 |
| Support channels in scope (email, chat, phone) | CS-02 |

## Progress

| Phase | Done | Remaining |
|---|---|---|
| 1 | PLT-01 lockout | Throttling, credentials, PLT-02, PLT-03, PLT-05, ACC-01, PLT-04 |
| 2–7 | — | All |
