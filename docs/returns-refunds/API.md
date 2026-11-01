# Returns & Refunds API

Base path: `/api/orders/admin/rma` (through the gateway: `http://localhost:8080/api/orders/admin/rma`). Requires a verified E-commerce Admin JWT (signature, issuer, audience, `application_code=ECOMMERCE_ADMIN`, live tenant grant). The **tenant comes only from the token**; a spoofed `X-Tenant-ID` is ignored. Store scope: `order:view-all-stores`, or the token's `store_ids` (none ⇒ nothing visible). Other tenants' or stores' records are always **404**. Errors: 400 invalid input, 401, 403 missing permission, 404, 409 not possible in the current state, 422 not eligible under policy.

Mutating money/stock actions accept an optional `Idempotency-Key` (8–80 chars); the same key replays the first result, the same key with a different request is 422.

## Permissions

`return:view`, `return:view-customer` (otherwise name/email are masked and phone/address omitted), `return:create`, `return:review`, `return:approve`, `return:reject`, `return:pickup`, `return:receive`, `return:inspect`, `return:refund-process`, `return:refund-approve` (refunds above the tenant threshold; never your own calculation), `return:exchange`, `return:policy-manage`, `return:policy-approve` (never your own submission), `return:config`, `return:exception-manage`, `return:audit-view`, `return:analytics-view`, `return:export`.

## Reading

| Method & path | Permission | Notes |
|---|---|---|
| `GET /meta` | view | views, status flows, manual targets, your return permissions |
| `GET /dashboard?from&to&storeId&vendorId&kind&status` | view | totals, pending, approved, rejected, awaiting pickup, in transit, awaiting inspection, refunds pending/completed, total refunded, return rate, avg processing/refund hours, top reasons, high-return products |
| `GET /analytics?…` | analytics-view | by status/reason/store/resolution/day, refunds by method, inspection outcomes, exchanges |
| `GET /audit?returnId&entityType&action&actorId&page&size` | audit-view | |
| `GET /returns?view&q&status&kind&resolution&reasonCode&storeId&vendorId&from&to&flagged&page&size` | view | `view` = NEW, PENDING_APPROVAL, APPROVED, REJECTED, PICKUP_SCHEDULED, IN_TRANSIT, RECEIVED, INSPECTION_PENDING, INSPECTION_COMPLETED, REFUND_PENDING, REFUND_PROCESSING, REFUND_COMPLETED, EXCHANGE. `q` matches RMA number, order number, customer name/email (wildcards are data). |
| `GET /returns/export?…` | export | CSV, formula-injection safe, audited |
| `GET /returns/{id}` | view | order, customer, items, history, notes, attachments, inspections, refunds + attempts, exchanges, pickups, exceptions, allowed transitions |
| `GET /refunds`, `GET /refunds/{id}`, `GET /exchanges`, `GET /exceptions` | view | |
| `GET /returns/{id}/attachments/{aid}` | view | private download (`nosniff`, `no-store`) |
| `GET /returns/{id}/inspections` | view | |
| `GET /policies`, `/policies/defaults?kind`, `/policies/{id}`, `/policies/{id}/versions`, `GET /settings` | view | |

## Acting

| Method & path | Permission | Body / rules |
|---|---|---|
| `POST /returns` | create | `{orderId, reasonCode, details, condition?, resolution? REFUND\|EXCHANGE\|STORE_CREDIT, lines:[{orderItemId, quantity}]}`; `Idempotency-Key` ⇒ one request. Restaurant claims may omit `lines` (covers unclaimed lines). 422 with the eligibility blockers when not eligible. |
| `POST /returns/{id}/approve` | approve | `{quantities?: {orderItemId: n}, note?}` — fewer than requested = partial approval; refused (409) while policy-required evidence is missing |
| `POST /returns/{id}/reject` | reject | `{reason}` mandatory |
| `POST /returns/{id}/request-info` | review | `{note}` |
| `POST /returns/{id}/notes`, `/assign` | review | internal note (≤2000 chars); `{operatorId}` |
| `POST /returns/{id}/transition` | by target | `{to, reason?}`; `to` ∈ UNDER_REVIEW, CUSTOMER_CANCELLED, CLOSED (review) · RECEIVED, INSPECTION_PENDING (receive) · REFUND_PENDING (refund-process). Anything else is 409/400. |
| `POST /returns/{id}/attachments` | review (inspect for `INSPECTION`) | multipart `file`, `kind`, `note`; PNG/JPEG/GIF/PDF verified by content, ≤5 MB, ≤25 MP, ≤20 per return |
| `POST /returns/{id}/pickup`, `/pickup/cancel` | pickup | `{address?, phone?, from, to, destination?}` / `{reason}`; retail only |
| `POST /returns/{id}/inspection` | inspect | `{packagingCondition, accessoriesReceived, missingItems?, damageDetails?, notes?, outcome, items:[{returnItemId, receivedQuantity, acceptedQuantity, condition, restockable, note?}]}` — one result per item; received ≤ approved; accepted ≤ received; restock only accepted NEW/LIKE_NEW |
| `POST /returns/{id}/refund` | refund-process | `{method?: ORIGINAL\|STORE_CREDIT}` — calculates (never accepts an amount) |
| `POST /refunds/{id}/approve` | refund-process (+refund-approve above threshold) | second person above threshold |
| `POST /refunds/{id}/process` | refund-process | sends to Payment; completes only on callback; also retries a `FAILED` refund with a new attempt |
| `POST /returns/{id}/exchanges` | exchange | `{returnItemId, newProductId, variant?, quantity, note?}` — price + stock checked |
| `POST /exchanges/{id}/decision` | exchange | `{approve, note?}` (reason required to reject) |
| `POST /exchanges/{id}/fulfill` | exchange | needs approval, accepted goods, fresh stock check; creates the replacement order |
| `POST /exceptions/{id}/action` | exception-manage | `{action: ASSIGN\|ESCALATE\|RESOLVE\|DISMISS, assigneeId?, note?}` (note required to resolve/dismiss) |
| `POST /returns/{id}/scan` | exception-manage | re-evaluate thresholds (idempotent) |
| `POST /policies`, `PUT /policies/{id}/draft`, `POST …/versions/{v}/submit`, `…/withdraw` | policy-manage | |
| `POST …/versions/{v}/decision`, `…/publish`, `…/rollback` | policy-approve | no self-review |
| `PUT /settings` | config | validated thresholds |

## Customer endpoint (unchanged path)

`POST /api/orders/{id}/returns` with `X-User-Id` (set by the gateway) and body `{reason, reasonCode?, resolution?, condition?, items:[{orderItemId, quantity}]}` — uses the same engine; `Idempotency-Key` supported. `GET /api/orders/returns/user/{userId}` is unchanged. `GET /api/orders/returns` and `POST /api/orders/returns/{id}/process` now return **410 Gone**.

## Service callbacks (service token, not routed by the gateway)

`POST /internal/return-events/refund` and `POST /internal/return-events/pickup` — see README §4. Headers: `X-Service-Token`, `X-Tenant-Id`. 401 without a valid token, 404 for another tenant's reference.

## Events published on `order-events`

`RETURN_REQUESTED`, `RETURN_<STATUS>` for every status change, `RETURN_INFO_REQUESTED`, `RETURN_REFUND_REQUESTED`, `RETURN_PICKUP_REQUESTED`, `RETURN_REFUND_COMPLETED`, `RETURN_REFUND_FAILED`. `referenceId` is unique per change, so the notification consumer is idempotent. Customers are notified only of: requested, info requested, approved, rejected, pickup scheduled/failed, received, inspection completed, refunded, cancelled, exchanged.
