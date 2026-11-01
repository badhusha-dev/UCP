# Atomic Inventory Reservation & Concurrency

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Concurrency Challenges in E-Commerce Checkout

During peak promotions or flash sales, multiple concurrent transactions may attempt to purchase the final remaining units of an item. Naive "read-then-write" patterns create race conditions that result in overselling.

The Universal Commerce Platform solves this with **database-level atomic row updates** combined with transactional Spring service boundaries.

---

## 2. Atomic Decrement Query Mechanics

Inside `product-service`, stock reduction is executed via an atomic SQL statement:

```sql
UPDATE products 
SET stock = stock - :quantity 
WHERE id = :productId AND stock >= :quantity;
```

### Operational Invariants
1. **Single Database Roundtrip**: The check (`stock >= :quantity`) and update (`stock = stock - :quantity`) occur atomically in a single statement.
2. **Row-Level Lock**: PostgreSQL acquires an exclusive row lock on the matching `product` record, preventing interleaved writes.
3. **Zero Negative Stock**: Enforced by the table-level database constraint `CHECK (stock >= 0)`.
4. **Row Count Validation**: If the update returns `0` affected rows, the service concludes that either the product ID does not exist or stock was insufficient, throwing `InsufficientStockException` (HTTP 409 Conflict).

---

## 3. Order Checkout Flow Sequence

```
[ Client ] ──> [ API Gateway ] ──> [ Order Service ] ──> [ Product Service ] ──> ( PostgreSQL )
                                           │                       │
                                           │ 1. POST /decrement    │ 2. Atomic SQL UPDATE
                                           │──────────────────────>│ (WHERE stock >= qty)
                                           │                       │
                                           │<──────────────────────│ 3. 200 OK (Stock deducted)
                                           │
                                           │ 4. Persist Order (status: PLACED)
                                           │ 5. Publish to Kafka (order-events)
```

---

## 4. Compensating Restocking Operations

If order placement fails midway (e.g. database timeout writing the order entity) or if a customer cancels an unfulfilled order:
- `order-service` executes a compensating REST call: `POST /api/products/{id}/increment-stock`.
- `product-service` runs an atomic increment: `UPDATE products SET stock = stock + :quantity WHERE id = :productId;`.
- The Redis catalog cache entry for the item is evicted immediately.
