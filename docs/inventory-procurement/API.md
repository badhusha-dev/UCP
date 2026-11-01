# API reference

All `/api/...` routes need `Authorization: Bearer <INVENTORY application token>`. Tenant and permissions come only from the verified token; `X-Tenant-Id` headers on these routes are ignored. Errors: 400 validation, 401 token, 403 permission/four-eyes, 404 not found or other tenant, 409 state/stock conflict.

## inventory-service `/api/inventory/ops`

| Method / path | Permission | Notes |
|---|---|---|
| GET `/dashboard?warehouseId=` | inventory:view | counts, valuation, pending adjustments, open transfers |
| GET `/items?q&status&category&sort&page&size` | inventory:view | sort: productId, sku, available, onHand; status IN_STOCK/LOW_STOCK/OUT_OF_STOCK |
| GET `/export` | inventory:export | CSV, formula-injection safe |
| GET `/valuation` | inventory:view | FIFO total, weighted-average total, costed/uncosted units |
| GET `/forecast?windowDays=30` | inventory:view | velocity, coverage, class, stockout risk, suggested qty |
| GET `/reservations/reconciliation` | inventory:view | drift between item reserved and holds + transfer reservations |
| GET/POST `/warehouses`, GET/POST `/warehouses/{id}/locations`, GET `/warehouse-stock` | warehouse:view / warehouse:manage | |
| GET/POST `/transfers`, POST `/transfers/{id}/{request,approve,reject,pick,dispatch,receive,cancel,lost,damaged}` | inventory:transfer, approve/reject need inventory:transfer-approve | `receive` body `{"receive":[{"productId","received","damaged"}]}` |
| GET/POST `/adjustments`, POST `/adjustments/{id}/{approve,reject}` | inventory:adjust / inventory:adjust-approve | body `{warehouseId, productId, newQuantity, reason, evidence, idempotencyKey}` |
| GET/PUT `/config` | inventory:view / inventory:config | |
| GET `/audit` | inventory:audit | |

Legacy `/api/inventory/reservations`, `/movements`, `/adjust` etc. still exist. Reservation request now accepts `idempotencyKey` and `allowPartial`.

## inventory-service internal `/internal/inventory` (X-Service-Token + X-Tenant-Id, fail closed)

POST `/reservations`, `/reservations/{id}/confirm`, `/reservations/{id}/release`, `/receipts`, `/incoming`, `/supplier-returns`; GET `/forecast`.

## procurement-service `/api/procurement/ucp`

| Method / path | Permission |
|---|---|
| GET/POST `/suppliers`, POST `/suppliers/{id}/{approve,suspend,archive}` | supplier:view / supplier:manage |
| GET/POST `/suppliers/{id}/items`, GET `/suppliers/{id}/performance` | supplier:view / supplier:manage |
| GET/POST `/purchase-orders`, GET `/purchase-orders/{id}`, GET `/purchase-orders/{id}/document` | purchase-order:view / purchase-order:manage |
| POST `/purchase-orders/{id}/{submit,reopen,issue,cancel}` | purchase-order:manage |
| POST `/purchase-orders/{id}/{approve,reject}` | purchase-order:approve (creator cannot approve; reject needs `reason`) |
| POST `/purchase-orders/{id}/receipts` (header `Idempotency-Key` required) | goods-receipt:manage |
| GET `/receipts` | purchase-order:view or goods-receipt:manage |
| GET/POST `/supplier-returns`, POST `/supplier-returns/{id}/{ship,credit}` | supplier-return:manage |
| GET `/replenishment`, POST `/replenishment/draft-po` | purchase-order:view / purchase-order:manage |
| GET `/analytics`, GET `/audit` | purchase-order:view / inventory:audit |

## accounting-service

POST `/internal/accounting/supplier-credit-notes` (X-Service-Token): idempotent on `idempotencyKey`; debits Accounts Payable, credits Inventory Asset (and recoverable tax).

OpenAPI for inventory-service and procurement-service is served through the gateway docs routes.
