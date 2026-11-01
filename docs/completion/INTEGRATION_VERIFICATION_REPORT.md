# Integration Verification Report

| Integration | Mechanism | Verified how | Status |
|---|---|---|---|
| order → product pricing (quote/redeem/reverse) | HTTP + `X-Service-Token` | `OrderPricingCheckoutTest` (mocked server) | Test-verified, not live |
| delivery → order delivery-status | HTTP callback, monotonic/idempotent | `DeliveryStatusSyncTest` | Test-verified, not live |
| Payment provider → order (payment/refund confirmation) | `/internal/order-events/*` callback | order admin/returns tests | Contract only — **no Payment service exists** |
| order → stock-service deduction | HTTP `/api/stock/adjustments` | existing order tests | Test-verified; uses legacy stock, not inventory reservations |
| order → notification | Kafka `order-events`, idempotent consumer | `NotificationConsumerIdempotencyTest` | Test-verified (no broker) |
| procurement → stock / accounting | HTTP + service token | none | Unverified (no procurement tests) |
| inventory internal API (`/internal/inventory/**`) | HTTP + service token + tenant | sibling session | In progress (sibling) |
| gateway → services | Spring Cloud Gateway routes | gateway tests to unused ports | Routing rules verified, upstreams not |

Live end-to-end (Kafka broker, Postgres, gateway) was not exercised in this session: the Docker daemon was not running.
