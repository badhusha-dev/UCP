# End-to-End User Journeys

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## Journey 1: Customer Product Discovery to Order Placement (Golden Flow)

1. **Discovery & Browsing**: Customer lands on the Storefront Shell (`http://localhost:3000`). The Product MFE loads the catalog, displaying real-time stock badges (`In Stock`, `Low Stock`).
2. **Search & Filter**: Customer applies category filter "Electronics" and sorts by price. Results update instantaneously via Redis L2 cache.
3. **Cart Assembly**: Customer clicks "Add to Cart" on Noise Cancelling Headphones. The Product MFE fires a `ucp:cart:add` event, prompting the Order MFE Cart Drawer to increment its badge count.
4. **Authentication**: Customer initiates checkout. If not logged in, the User MFE Auth Modal displays. Customer enters `admin@ecommerce.com` / `password123`. A signed JWT is acquired and stored in Redux.
5. **Checkout & Reservation**: Customer clicks "Confirm Order". The Order Service validates unit pricing, executes an atomic reservation in Product Service, writes the order as `PLACED`, and emits a Kafka event.
6. **Confirmation & Tracing**: Customer receives order confirmation with ID #79. Downstream, Notification Service consumes the event idempotently, and a 13-span trace is registered in Zipkin.

---

## Journey 2: Warehouse Order Processing & Fulfillment

1. **Order Identification**: Warehouse manager logs in and queries orders awaiting fulfillment (`GET /api/orders` with status `PLACED` or `CONFIRMED`).
2. **Item Picking**: Warehouse staff picks physical items from warehouse bins matching order line items.
3. **Dispatch & Transition**: Manager enters courier tracking code and calls `PUT /api/orders/{id}/status` with target status `SHIPPED`.
4. **Customer Notification**: Kafka dispatches `ORDER_SHIPPED` event, updating customer tracking and sending shipping confirmation.

---

## Journey 3: Administrator DLQ Inspection & Replay

1. **Incident Notification**: Admin observes an alert in Alertmanager indicating consumer notification retry failure.
2. **Queue Inspection**: Admin calls `GET /api/notifications/dlq` to review failed payloads and error messages.
3. **Root Cause Remediation**: Admin verifies downstream SMTP network restoration.
4. **One-Click Replay**: Admin triggers `POST /api/notifications/dlq/{id}/replay`. The event re-executes idempotently without duplicating prior customer communications.
