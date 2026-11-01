# Business rules

**Tenancy.** Every query filters by the JWT `tenant_id`; foreign ids answer 404. Products referenced by prices, schedules and campaign rules must belong to the tenant.

**Prices.** Base > 0, 0 < sale ≤ base, cost ≥ 0, at most 2 decimals; the currency must be ISO 4217 *and* enabled for the tenant. There is one current price per (store, product, variant, currency); every change appends to `price_history` (old/new values, source MANUAL/BULK/SCHEDULED/IMPORT/ROLLBACK, actor). A price whose margin over cost (price-row cost, else catalog cost) is below `min_margin_percent` is rejected, so at the default 0% selling below cost is refused. Direct base-price moves above `approval_threshold_percent` need `pricing:publish`; others go through scheduling and approval. A scheduled price applies once, when approved and due. `effective_to` on schedules is stored but **not** auto-reverted.

**Campaigns.** Types PERCENTAGE, FIXED_AMOUNT, BUY_X_GET_Y, BUNDLE and QUANTITY_TIER, scoped to products, categories or the whole store (with EXCLUDE rules). The reward must match the type and percent ≤ the tenant's `max_discount_percent`. `endsAt` defaults to `startsAt + default_campaign_days`. Lifecycle: DRAFT → PENDING_APPROVAL or APPROVED → PUBLISHED ⇄ PAUSED, plus CANCELLED and REJECTED. Campaigns whose rewards are all percentages within the threshold auto-approve; amount-based, bundle and free-item rewards always need a second person. Published campaigns are immutable (pause or cancel instead). A campaign is live only when PUBLISHED and `starts_at ≤ now < ends_at`, so future and expired campaigns never discount. Publishing is refused when another APPROVED/PUBLISHED campaign has the **same priority, overlapping dates and store, overlapping targets, and neither is stackable**.

**Calculation (server side, `DiscountEngine`).** Campaigns are sorted by priority (higher first).
- `NO_STACKING`: only the first campaign that discounts applies.
- `FLAGGED_ONLY`: a later campaign applies only if it and every earlier applied one are `stackable`.
- `STACK_ALL`: all eligible campaigns apply.

Stacked discounts apply to the *remaining* line amount. Each campaign is capped by its `max_discount_amount`. Each line is capped at `max_discount_percent` of the line and never goes below cost ÷ (1 − min margin). Eligibility covers minimum order value, minimum quantity, customer segment and first-order flag; the caller supplies segment and first-order (the platform does not derive them). Loyalty-point rules are **not** implemented. Unit prices come from `product_prices` (a store row overrides `*`, in the order's currency, inside its effective window) or from the catalog price in the default currency; client prices are ignored.

**Coupons.** Codes are unique per tenant (case-insensitive, stored upper-case). Bulk generation is limited by `coupon_bulk_max` and uses SecureRandom over an unambiguous alphabet. A coupon is valid only if it is ACTIVE, not expired, matches any customer restriction, its campaign is live, and total/per-customer limits (coupon and campaign level) are not reached. Redemption uses a guarded `UPDATE … WHERE redeemed_count < usage_limit` (the database serialises racers), then checks per-customer counts under that row lock; a primary key on `(tenant, order_ref)` makes retries idempotent. Per-customer limits apply to coupon campaigns only. Cancelling a campaign revokes its coupons; REVOKED is final.

**Orders are immutable with respect to promotions.** The order stores the computed unit prices, discount and total at checkout; later campaign or price edits never rewrite it. `reverse` releases usage if checkout fails after redemption. `OrderService.cancelOrder` calls `reverse` (best-effort).

**Configuration.** `pricing_settings` holds plain percent/integer/enum/CSV-of-known-values columns, validated on write and by CHECK constraints. No expressions, scripts or SQL are stored or evaluated.

**Audit.** Every admin mutation writes `promotion_audit_logs` (actor, entity, action, detail) in the same transaction; checkout redemptions log as actor 0 `checkout@system`.
