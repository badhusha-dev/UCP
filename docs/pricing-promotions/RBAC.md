# RBAC configuration

The repo's convention is `module:action` (e.g. `catalog:manage`), so the requested names map to:

| Requested | Key | Requested | Key |
|---|---|---|---|
| PRICING_VIEW | `pricing:read` | PROMOTION_VIEW | `promotion:read` |
| PRICING_CREATE | `pricing:create` | PROMOTION_CREATE | `promotion:create` |
| PRICING_UPDATE | `pricing:update` | PROMOTION_UPDATE | `promotion:update` |
| PRICING_APPROVE | `pricing:approve` | PROMOTION_APPROVE | `promotion:approve` |
| PRICING_PUBLISH | `pricing:publish` | COUPON_MANAGE | `coupon:manage` |
| PRICING_EXPORT | `pricing:export` | PRICING_AUDIT_VIEW | `pricing:audit` |
| (cost price, restricted) | `pricing:cost:read` | | |

Seeded roles (user-service V9): `PRICING_MANAGER` (pricing read/create/update/cost/export, promotion read/create/update), `PRICING_APPROVER` (pricing read/approve/publish/audit, promotion read/approve), `MARKETING_MANAGER` (promotion read/create/update, `coupon:manage`, `pricing:read`) and `PRICING_ANALYST` (`pricing:read`, `promotion:read`, `pricing:audit`). `ADMIN` gets all of them. Maker-checker: nobody can approve their own campaign or scheduled price.

Enforcement order: gateway (JWT, coarse `pricing:read`) → product-service filter (signature, issuer, audience, `application_code=ECOMMERCE_ADMIN`, live tenant membership and application grant checked against user-service) → per-endpoint permission against the token's `permissions` claim → tenant-filtered SQL.

`ECOMMERCE_ADMIN` is still `PLANNED` in the application registry and its `required_permissions` (`catalog:manage`) were left unchanged: tenant users need the application grant plus one of the roles above.
