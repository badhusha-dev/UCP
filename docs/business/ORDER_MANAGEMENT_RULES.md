# Order Management & Status Governance

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Order Status Governance

| Status | Definition | Allowed Next Status | Permitted Roles |
| :--- | :--- | :--- | :--- |
| **`PLACED`** | Order created by customer; inventory atomically reserved; awaiting payment/confirmation. | `CONFIRMED`, `CANCELLED` | Customer, Manager, Admin |
| **`CONFIRMED`**| Payment authorized and captured; warehouse notified for picking. | `SHIPPED`, `CANCELLED` | Manager, Admin |
| **`SHIPPED`** | Goods handed over to logistics carrier with tracking number assigned. | `DELIVERED` | Manager, Admin |
| **`DELIVERED`**| Customer or carrier confirmed successful physical delivery. (Terminal State) | None | Admin / Webhook |
| **`CANCELLED`**| Order voided; inventory released and restocked. (Terminal State) | None | Customer (if PLACED), Admin |

---

## 2. Customer Cancellation Window

- Customers are permitted to self-cancel an order through their order history portal **only** while the status remains `PLACED`.
- Once an order transitions to `CONFIRMED` or `SHIPPED`, the cancellation button is disabled, requiring customer support intervention to prevent shipment collisions.

---

## 3. Order Isolation & Data Privacy

- The endpoint `GET /api/orders/my-orders` strictly filters results by the authenticated `userId` extracted from the Gateway's `X-User-Id` header.
- Customers have zero visibility into other users' order histories, shipping destinations, or payment amounts.
