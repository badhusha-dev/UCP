# Pricing & Promotions API

Base: `/api/products/admin/pricing` (via the gateway on `:8080`). Auth: `Authorization: Bearer <tenant-scoped E-commerce Admin JWT>`.
Errors: `400` validation, `401` bad token or lost grant, `403` missing permission or self-approval, `404` not found **or belongs to another tenant**, `409` state conflict / stale version / duplicate.

| Method & path | Permission | Notes |
|---|---|---|
| GET `/dashboard` | pricing:read or promotion:read | real counts only |
| GET `/settings?storeId=` · PUT `/settings?storeId=` | read: pricing:read/promotion:read; PUT: pricing:publish | validated policy values |
| GET `/prices?productId=&storeId=` | pricing:read | cost fields removed without pricing:cost:read |
| POST `/prices` | pricing:create | 409 if the (store, product, variant, currency) price exists |
| PUT `/prices` | pricing:update | optional `expectedVersion` (optimistic lock); base change above the approval threshold needs pricing:publish; setting `costPrice` needs pricing:cost:read |
| POST `/prices/bulk` | pricing:update | `{productIds, mode: PERCENT or AMOUNT, value, currency, dryRun}`; all-or-nothing |
| GET `/prices/history?productId=` | pricing:read | append-only |
| POST `/prices/history/{id}/rollback` | pricing:publish | writes a new ROLLBACK history row |
| GET `/prices/export` | pricing:export | CSV; formula cells neutralised; cost column needs pricing:cost:read |
| POST `/prices/import` (text/csv) | pricing:create and pricing:update | columns `productId,currency,basePrice[,variantId,salePrice,costPrice,storeId]`; all-or-nothing, max 2000 rows |
| GET `/scheduled` · POST `/scheduled` | pricing:read · pricing:create | future `effectiveFrom`; creates a pending approval |
| POST `/scheduled/{id}/decision` `{approve,note}` | pricing:approve | requester cannot approve their own |
| POST `/scheduled/{id}/cancel` | pricing:update | |
| GET `/campaigns?type=&status=&automatic=&segmentOnly=` · GET `/campaigns/{id}` | promotion:read | |
| POST `/campaigns` · PUT `/campaigns/{id}` | promotion:create · promotion:update | PUT only in DRAFT/REJECTED |
| POST `/campaigns/{id}/submit` | promotion:update | within threshold → APPROVED, otherwise PENDING_APPROVAL |
| POST `/campaigns/{id}/decision` | promotion:approve | reject needs a note; no self-approval |
| POST `/campaigns/{id}/publish` | pricing:publish | 409 on ambiguous overlap |
| POST `/campaigns/{id}/pause`, `/resume`, `/cancel` | promotion:update | cancel revokes the campaign's coupons |
| GET `/campaigns/{id}/conflicts` · GET `/calendar?from=&to=` | promotion:read | calendar window max 400 days |
| GET `/performance` · GET `/approvals?status=` | promotion:read | |
| GET `/coupons?campaignId=&status=&q=` · GET `/redemptions` | coupon:manage or promotion:read | |
| POST `/coupons` | coupon:manage | `{campaignId, code}` or `{campaignId, count, prefix}`; limits validated against settings |
| POST `/coupons/{id}/pause`, `/activate`, `/revoke` | coupon:manage | revoke is final |
| POST `/quote-preview` | promotion:read | same calculation as checkout, consumes nothing |
| GET `/audit?entityType=&entityId=` | pricing:audit | |

## Checkout (service-to-service)

Headers `X-Service-Token` (= `SERVICE_TO_SERVICE_TOKEN`; fails closed if unset) and `X-Tenant-Id`.

`POST /internal/pricing/quote` and `POST /internal/pricing/redeem` body:

```json
{"currency":"USD","orderRef":"chk-123 (redeem only)","customer":{"id":"42","segments":["GOLD"],"firstOrder":false},
 "lines":[{"productId":7,"variantId":0,"quantity":2}],"couponCode":"SPRING10"}
```

Response: `lines[]` (server unit price), `applied[]`, `subtotal`, `discount`, `total`, `warnings`, and `couponValid`/`couponMessage` when a coupon was sent.
`redeem` is idempotent per `(tenant, orderRef)` (a replay returns the stored result with `idempotentReplay:true`); a refused coupon or exhausted limit answers `409`.
`POST /internal/pricing/reverse/{orderRef}` releases coupon and campaign usage exactly once.
