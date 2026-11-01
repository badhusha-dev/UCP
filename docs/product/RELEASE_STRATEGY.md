# Release Strategy: specification coverage

Where the repository stands against the releases in [specification section 18](PRODUCT_REQUIREMENTS.md) and the phases in section 15.
Date 2026-11-01, at commit `bb1b7b0e`. Status labels as in [APPLICATION_CATALOG.md](APPLICATION_CATALOG.md). A release being planned does not mean any of it is implemented.

## MVP release

**Not releasable.** Every MVP item has some implementation, none is COMPLETED, and four blockers need a decision, an environment or the owner rather than more code.

| MVP item | Status | What stands between it and COMPLETED |
|---|---|---|
| Secure tenant onboarding | PARTIALLY COMPLETED | Tenant lifecycle states (A-01); nine services not tenant-scoped (K2), which needs Postgres-backed tests (BLOCKED) |
| User membership and application grants | PARTIALLY COMPLETED | Grants and revocation are audited; Accounting has no tenant login route (K17) |
| Subscription and entitlement foundation | PARTIALLY COMPLETED | Entitlements exist; plans, limits and billing do not |
| Product catalog | PARTIALLY COMPLETED | Brands, attributes, bundles |
| Pricing | PARTIALLY COMPLETED | Promo codes not validated server-side (B-14) |
| Storefront | PARTIALLY COMPLETED | Storefront does not resolve its tenant from domain or slug in the UI (B-25, K4) |
| Cart and checkout | PARTIALLY COMPLETED | Cart not tenant-scoped; checkout depends on payment |
| Payment integration | NOT IMPLEMENTED | **BLOCKED**: PSP not chosen (B-01) |
| Orders | PARTIALLY COMPLETED | Payment confirmation arrives only by service-token callback |
| Inventory reservations | PARTIALLY COMPLETED | Reservation not atomic with order creation (A-04); needs a concurrent test on Postgres |
| Basic delivery | PARTIALLY COMPLETED | Delivery not created on fulfilment (A-06) |
| Basic accounting integration | PARTIALLY COMPLETED | No payment postings; posted-line immutability trigger (B-29) |
| Customer management | PARTIALLY COMPLETED | crm-service not tenant-scoped (BLOCKED) and refuses SUPER_ADMIN (B-36) |
| Notifications | PARTIALLY COMPLETED | No password-reset or verification messages |
| Essential operational dashboards | PARTIALLY COMPLETED | Analytics is platform-only; tenant dashboards do not exist |

Blockers that code alone cannot clear:

1. **PSP choice** for the payment service.
2. **A Docker daemon or scratch Postgres** for the tenant slices, the immutability trigger and the migration run.
3. **Credential rotation** of the values removed from tracked files (owner).
4. **Key-storage decision** for asymmetric JWT signing.

## Business release

Procurement (PARTIALLY COMPLETED), full accounting (PARTIALLY COMPLETED), CRM (PARTIALLY COMPLETED), advanced delivery (PARTIALLY COMPLETED). Retail POS, restaurant operations, support and multi-branch operations are NOT IMPLEMENTED; multi-branch needs the branch entity that the tenant hierarchy (section 2.2) defines and that does not exist yet.

## Enterprise release

HR and payroll, assets, projects, AI assistant, workflow automation, integration marketplace and plugins are NOT IMPLEMENTED. Advanced analytics is PARTIALLY COMPLETED (order, inventory and accounting reports only).

## Specification phases mapped to the current roadmap

The executable roadmap is [../status/COMPLETION_ROADMAP.md](../status/COMPLETION_ROADMAP.md); its item numbers are used below.

| Phase | Status | Roadmap items | Notes |
|---|---|---|---|
| 0 Repository stabilisation | PARTIALLY COMPLETED | Status documents, full backend run, frontend typecheck gate | Inventory, builds and tests are recorded with evidence. Defects have priorities but no named owners. |
| 1 SaaS foundation and security | PARTIALLY COMPLETED | 0.1, 0.3 to 0.6, 1.4, 1.5, 1.6, 1.12, 1.13, 2.5 | Done: credentials out of tracked files, analytics tenant scope, lockout and sign-in rate limit, Kubernetes secrets template. Open: tenant slices (BLOCKED), asymmetric JWT (BLOCKED), refresh tokens, logout, legal entities and branches. |
| 2 Core commerce | PARTIALLY COMPLETED | 0.8, 1.3, 1.7, 1.8, 1.9, 2.12, 2.13 | Outbox done for all producers. Payment BLOCKED. |
| 3 Inventory and procurement | PARTIALLY COMPLETED | 1.7, 1.10 | Requisitions, RFQs, stock counts and serials are not on the roadmap yet |
| 4 Full accounting | PARTIALLY COMPLETED | 0.7 | Trial balance, aging, bank accounts, tax codes, assets and budgets are not on the roadmap yet |
| 5 Retail, restaurant, delivery | NOT IMPLEMENTED (delivery PARTIALLY COMPLETED) | 1.8, 2.2 | POS and restaurant need a scope and a service decision |
| 6 CRM and support | PARTIALLY COMPLETED (support NOT IMPLEMENTED) | 2.1, 2.3 | Support scope BLOCKED |
| 7 HR and payroll | NOT IMPLEMENTED | none | Depends on legal entities and branches |
| 8 Assets and projects | NOT IMPLEMENTED | none | Depends on the ledger (phase 4) |
| 9 Analytics and AI | NOT IMPLEMENTED (analytics PARTIALLY COMPLETED) | none | Depends on stable events (section 16, item 12) |
| 10 Integrations and enterprise | NOT IMPLEMENTED | 2.8, 2.9, 3.5 | Webhooks and plugins not started |

## Next steps in dependency order

Section 16 puts identity and tenant management before any tenant-specific module. Applied to what is open:

1. Decisions: PSP, JWT key storage, Customer Support scope, Quality Center scope, and whether to adopt `/api/v1`.
2. Environment: a Docker daemon or scratch Postgres, then the tenant slices in roadmap order (crm, loyalty, cart, stock, notification, qa, error-stack, legacy procurement).
3. Without either: tenant hierarchy design (legal entity and branch in user-service, additive migrations), storefront tenant resolution in the UI (SF-01), server-side promo validation (SF-02), frontend tests (TD-04).
