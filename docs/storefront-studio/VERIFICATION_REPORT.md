# Verification report — Storefront Management & Content Studio

Working tree on `saas/delivery-verification-and-app-registry`; nothing committed or pushed.
Environment: Windows, offline Maven. Tests use H2 (PostgreSQL mode) with the real V7/V8 SQL; the storefront and pricing integration suites were **also run against the Docker PostgreSQL 15** (scratch database). Docker Desktop was restarted and the stack deployed.

## Implemented
- **Schema** V8: `storefronts`, `storefront_documents`, `storefront_versions` (immutable once not DRAFT), `storefront_approval_history`, `storefront_media`, `storefront_banners`, `storefront_domains`, `storefront_theme_presets`, `storefront_audit_logs`.
- **Backend**: storefront CRUD; homepage and landing pages as validated typed sections (13 section types, desktop/mobile layout + visibility, reordering by content order); theme/branding with 10 supported fonts, presets (3 built-in + tenant); navigation (3 levels, safe URLs); product/category display and restaurant settings; SEO (title, description, canonical, robots, Open Graph, sitemap, robots.txt, JSON-LD); media upload with magic-byte sniffing, full decode, per-kind dimension/size limits, pluggable storage; banners (desktop/mobile, dates, priority, campaign link, active); domains with real DNS TXT verification and real TLS handshake check; workflow DRAFT→IN_REVIEW→APPROVED→PUBLISHED→ARCHIVED with no self-review, scheduling, rollback-as-new-version, compare, unpublish, full audit; anonymous published-only public API.
- **RBAC**: 8 permissions + 3 roles (user-service V10), gateway coarse gate, per-endpoint checks; tenant from JWT only.
- **UI**: all 17 menu screens under *Storefront Management*; real API data only.
- **Docs**: this folder. Compose: media volume + multipart limits for product-service.

## Tested — passed
| Suite | Result |
|---|---|
| product-service (all) | **75 run, 0 failures** — new: `ContentValidatorTest` 6, `StorefrontApiIntegrationTest` 14 |
| api-gateway (all) | 28 run, 0 failures (2 new storefront route tests) |
| user-service (all) | 12 run, 0 failures |
| order-service (all) | 21 run, 0 failures |
| mfe-product logic | 17 `node --test` tests (8 storefront) pass; `vite build` and `tsc` clean for new files |
| Real PostgreSQL | V7 + V8 apply cleanly; user-service V1–V10 apply cleanly; `PricingApiIntegrationTest` + `StorefrontApiIntegrationTest` (33 tests, incl. 25-way concurrent coupon redemption) pass against Postgres |
| Deployed stack | product-service migrated to V8 and user-service to V10 on the live databases; with `ddl-auto: validate` the app starts. Via the live gateway: unknown/unpublished storefront, sitemap, media and domain-resolve → **404**; admin without/with bogus token → **401**; existing `/api/products` → 200 |

Storefront cases covered: permissions, slug validation/uniqueness, tenant isolation (B cannot read, edit, publish or preview A's storefront, media, banners, domains, audit; A's publish leaves B's live content unchanged), review rules (no self-review, reason on send-back), locked IN_REVIEW, immutable published snapshot (hash compared), drafts invisible publicly, preview needs auth, rollback to a new version, compare, scheduling (future not live; due → live; cancel), unpublish, content validation (script/markup, `javascript:`/`data:`/`//`/`http:` links, unknown sections, duplicate ids, foreign media/products, section/size limits, bad colours/fonts/enums, opening hours), landing slug uniqueness per storefront, noindex excluded from sitemap, retail resolution at read time (inactive hidden, stock live), restaurant hours + JSON-LD, media (traversal filename, wrong size/format/SVG/fake GIF/text/empty/oversize 413, cross-tenant, in-use delete, public headers), banners (kinds, unsafe URL, foreign campaign, windows, ordering, public visibility), domains (invalid hosts, never verified without a matching record, SSL only after verify, primary only when verified, global uniqueness).

## Bugs found by testing and fixed
- Approval-history `action` column too short for `PUBLISH_SCHEDULED` (scheduled publishes would silently fail) → widened in V8.
- Anonymous 4xx on public product-service endpoints were reported as **403** behind the real stack (error dispatch to `/error` required auth) → `/error` permitted. MockMvc cannot show this; it was found by probing the deployed service.
- Deployment problems unrelated to this feature, fixed to deploy: delivery-service used `.env` Postgres credentials that did not match the Postgres container; `delivery_db` missing from an older volume; `.env` lacked `DELIVERY_OTP_PEPPER`.

## Blocked / not verified
- **No authenticated end-to-end on the live stack**: the only active login is `admin@ecommerce.com` (password not known to me; `manager@ecommerce.com` is deactivated in the database), and seeding a tenant/application grant was out of scope. The authenticated flows are verified by integration tests (H2 and Postgres) but not through the running gateway/UI.
- **UI never opened in a browser**; no React component-render tests (no vitest/jsdom/testing-library installed offline). Frontend logic is unit-tested; screens are type-checked and bundled only.
- **DNS and TLS probes are mocked in tests**; the real JNDI DNS lookup and TLS handshake code paths were not exercised against real domains.
- **Browser end-to-end publishing test** not run; the equivalent API-level publish→public-read flow is tested.

## Pending / not implemented
- S3/object-storage adapter (a local-directory/volume adapter ships behind `MediaStorage`).
- **Restaurant dishes/menu** (no restaurant catalogue service exists in the platform), dish images, per-dish veg and prep time.
- **Per-language content / localization editing**; locales are recorded at creation only.
- Best-sellers/trending automatic ranking (no sales feed); they are hand-picked.
- Drag-and-drop reordering (Up/Down buttons instead); WebP uploads (JDK cannot decode them for validation).
- Custom-domain edge routing and certificate issuance are operator infrastructure; `storefront.edge-host` must be configured.
- Public CDN/cache invalidation beyond short `Cache-Control`; rate limiting on public endpoints.
- Not touched (another session's uncommitted work): the shared-library application registry; `ECOMMERCE_ADMIN` remains PLANNED.
