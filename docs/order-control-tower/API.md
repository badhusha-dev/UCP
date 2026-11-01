# Order Management API

Base `/api/orders/admin` (gateway `:8080`), `Authorization: Bearer <tenant-scoped E-commerce Admin JWT>`. The tenant and (optionally) `store_ids` come only from the verified token.
Errors: `400` validation · `401` token/grant · `403` missing permission · `404` not found **or another tenant's/store's** · `409` state conflict / stale version · `422` Idempotency-Key reused with a different request.
Sensitive POSTs require `Idempotency-Key` (8-80 chars `A-Za-z0-9_.:-`); optional `X-Correlation-Id` is stored with the audit row (otherwise generated).

| Method & path | Permission | Notes |
|---|---|---|
| GET `/meta` | order:view | statuses, flows, views, caller permissions |
| GET `/dashboard?from&to&storeId&vendorId&orderType&paymentStatus&fulfillmentStatus` | order:view | queue sizes (current), outcomes (range), averages, gross/pending value, definitions |
| GET `/orders` | order:view | `q, view, status[], orderType, storeId[], vendorId, paymentStatus, fulfillmentStatus, deliveryStatus, from, to, minAmount, maxAmount, operatorId, slaBreached, sort=field:dir (repeatable), page, size≤100`. Customer search only with order:customer:view |
| GET `/view-counts` | order:view | count per menu view |
| GET `/orders/export` | order:export | CSV, max 5000 rows, formula-safe; customer columns only with order:customer:view |
| GET `/orders/{id}` · `/timeline` · `/notes` | order:view | details include allowed transitions with blocked reasons |
| POST `/orders/{id}/transition` `{to, reason, expectedVersion?}` | per target (confirm/process/hold/cancel) | state-machine checked |
| POST `/orders/{id}/hold` · `/release` · `/request-cancellation` · `/cancel` `{reason}` | order:hold / release / cancel | release also withdraws a cancellation request |
| POST `/orders/{id}/restaurant/{accept,reject,start,delay,ready,picked-up,pickup-failed}` `{prepMinutes, reason}` | order:confirm / process | restaurant orders only |
| POST `/orders/{id}/notes` `{kind: INTERNAL/COMMUNICATION/ADMIN_COMMENT, body}` | order:note:create | |
| POST `/orders/{id}/assign` `{operatorId}` | order:process | |
| GET `/orders/{id}/fulfillment` | order:fulfillment:view | per-item ordered/allocated/fulfilled |
| POST `/orders/{id}/fulfillments` `{locationCode, locationName, method, lines:[{orderItemId, quantity}]}` | order:process | cannot over-allocate; one vendor per fulfillment |
| POST `/fulfillments/{id}/packing` · `/ship` `{carrier, trackingReference}` · `/cancel` | order:process | ship drives order to PARTIALLY_FULFILLED / SHIPPED (pickup: FULFILLED) |
| GET `/exceptions?status&category&priority&assigneeId&page&size` · GET `/exceptions/{id}` | order:view | |
| POST `/exceptions/scan` · `/exceptions/{id}/{assign,escalate,resolve,dismiss,notes}` | order:exception:manage | resolve/escalate need a note |
| POST `/bulk/preview` `{action, orderIds, params}` · POST `/bulk/{job}/execute` · GET `/bulk/{job}` | order:bulk + the action's own permission | actions: ASSIGN_OPERATOR, HOLD, RELEASE, ADD_NOTE, REQUEST_CANCELLATION, SET_CARRIER, EXPORT; executes once per job |
| GET/POST `/saved-views` · DELETE `/saved-views/{id}` | order:view | per user |
| GET/PUT `/settings?storeId` | order:view / order:settings | SLA minutes, payment gate, max bulk size |
| GET `/returns?status&page&size` | order:view | read-only window onto returns/refunds |
| GET `/reports/summary?groupBy=day|store|status|type|payment&from&to` | order:report:view | |
| GET `/audit?orderId&action&limit` | order:audit:view | previous/new status, actor, reason, correlation id |

## Domain callbacks (service token)
Headers `X-Service-Token`, `X-Tenant-Id`; each call has an `eventId` and is applied once.
- `POST /internal/order-events/payment` `{eventId, orderId, status: PENDING|PAID|FAILED, method, reference}`
- `POST /internal/order-events/refund` `{eventId, orderId, status: COMPLETED|FAILED, reference}` — the only path to `REFUNDED`
- `POST /internal/order-events/return-refund` `{eventId, returnId, status: COMPLETED|FAILED, reference}` — return `REFUND_PENDING` → `REFUNDED`

## Events published (Kafka `order-events`)
`ORDER_STATUS_UPDATED`, `ORDER_CANCELLED`, `ORDER_CANCELLATION_REQUESTED`, `ORDER_SLA_BREACHED` (internal, not sent to customers), `ORDER_REFUND_REQUESTED`, `ORDER_RETURN_REFUND` (after confirmation). `referenceId` carries the change's correlation id; the notification consumer is idempotent per (order, event, status, correlation id).
