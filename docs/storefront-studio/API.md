# Storefront API

Admin base: `/api/products/admin/storefront` (gateway `:8080`), `Authorization: Bearer <tenant-scoped E-commerce Admin JWT>`.
Errors: `400` validation (message says what to fix) · `401` token/grant · `403` missing permission or reviewing your own submission · `404` not found **or another tenant's** · `409` state conflict / duplicate · `413` image too large · `415` unsupported image type.

| Method & path | Permission | Notes |
|---|---|---|
| GET `` · POST `` | read · manage | create `{name, slug, storeId, storefrontType RETAIL/RESTAURANT, defaultLocale, supportedLocales}`; also creates HOMEPAGE, THEME, NAVIGATION, SETTINGS drafts |
| GET `/{id}` · PUT `/{id}` `{name}` | read · manage | |
| GET `/{id}/dashboard` | read | real counts |
| GET `/{id}/audit` | audit | |
| GET `/{id}/documents` | read | each with latest version and live version number |
| POST `/{id}/pages` `{slug,title,content?}` | edit | landing page; 409 duplicate slug in this storefront; reserved slugs refused |
| PUT `/documents/{doc}` `{title}` | edit | |
| GET `/documents/{doc}/versions` · `/versions/{v}` | read | version includes content and review trail |
| PUT `/documents/{doc}/draft` `{content,note}` | edit | validated; edits the open DRAFT or starts a new draft; 409 while IN_REVIEW/APPROVED |
| POST `…/versions/{v}/submit` | edit | DRAFT → IN_REVIEW |
| POST `…/versions/{v}/decision` `{approve,note}` | approve | reviewer ≠ submitter; reject needs a note (back to DRAFT) |
| POST `…/versions/{v}/withdraw` | edit | IN_REVIEW/APPROVED → DRAFT |
| POST `…/versions/{v}/publish` `{at?,note?}` | publish | APPROVED only; future `at` schedules |
| POST `…/versions/{v}/cancel-schedule` | publish | |
| POST `…/versions/{v}/rollback` `{note?}` | publish | target must be ARCHIVED; creates a new live version |
| POST `/documents/{doc}/unpublish` | publish | landing pages only |
| GET `/documents/{doc}/compare?from=&to=` | read | path-level changes |
| GET `/documents/{doc}/preview?versionId=` | read | renders any version incl. drafts (authenticated only) |
| GET `/{id}/media?kind=` · POST `/{id}/media` (multipart `file,kind,altText`) · DELETE `/media/{m}` | read · media · media | PNG/JPEG/GIF, ≤5 MB, per-kind dimensions; delete blocked while referenced |
| GET/POST `/{id}/banners` · PUT/DELETE `/banners/{b}` · POST `/{id}/banners/reorder` `{ids}` | read/edit | |
| GET/POST `/{id}/domains` · POST `/domains/{d}/verify` · `/check-ssl` · `/primary` · DELETE `/domains/{d}` | read · domain | |
| GET `/theme-presets` · POST `/{id}/theme-presets` · DELETE `/theme-presets/{p}` | read · edit | built-in + tenant presets |

## Content shapes (all validated; unknown fields are dropped)
- **HOMEPAGE/LANDING** `{template?, sections:[{id,type,visible,layout:{desktop:{columns,visible},mobile:{…}},props}], seo}`. Section types: HERO_BANNER, PROMO_BANNER, FEATURED_PRODUCTS, FEATURED_CATEGORIES, PRODUCT_GRID, BEST_SELLERS, NEW_ARRIVALS, TRENDING, TESTIMONIALS, BRAND_HIGHLIGHTS, COUNTDOWN, NEWSLETTER, CUSTOM_CONTENT (blocks: HEADING, PARAGRAPH, LIST, IMAGE, BUTTON). Best sellers/trending are hand-picked (no sales feed exists). Max 50 sections, 200 KB.
- **THEME** `{logoMediaId, faviconMediaId, colors{primary,secondary,accent,background,surface,text #RRGGBB}, headingFont/bodyFont (10 supported), headerLayout, footerLayout, buttonStyle, productCardStyle, navigationStyle, mobileMenu, colorMode}`.
- **NAVIGATION** `{header[], footer[], mobile[]}`, item `{label,url|categoryId,promotional,children[]}`, ≤3 levels, ≤100 items.
- **SETTINGS** `{display{showAvailability,showSearch,showFilters,defaultSort,productsPerPage,badges[]}, restaurant{orderingAvailable,showVegIndicator,showPreparationTime,openingHours{MON..SUN:{open,close}|{closed}}}, contact{name,phone,email}}`.
- **seo** `{title≤70, description≤160, canonicalUrl(https), robots, ogTitle, ogDescription, ogImageMediaId}`.
- Text is plain text only: `<`, `>`, `javascript:`, `data:text`, event-handler patterns and HTML entities are **rejected**. Links: in-site path (`/x`) or `https://`.

## Public API (anonymous, GET, published content only; unknown/unpublished/suspended → 404)
`/api/products/public/storefront/` + `resolve?host=` (verified domains only) · `{slug}` (name, locales, baseUrl, published theme/navigation/settings, JSON-LD) · `{slug}/home` · `{slug}/pages/{pageSlug}` · `{slug}/banners` (active, in window, by priority) · `{slug}/sitemap.xml` · `{slug}/robots.txt` · `media/{publicId}` (pinned content type, `nosniff`, `CSP: sandbox`).
Media ids in content become `{url, alt, width, height}` objects; product sections gain `products[]` (price, sale price, availability, live from the catalog).
