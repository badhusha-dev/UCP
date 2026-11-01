# Implemented Feature Technical Details

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Feature: Microfrontend Module Federation (`FT-01` to `FT-04`)
- **Technology**: React 18.3.1, TypeScript, `@originjs/vite-plugin-federation`.
- **Implementation Detail**:
  - `shell-app` consumes `http://localhost:3001/assets/remoteEntry.js`, `http://localhost:3002/assets/remoteEntry.js`, and `http://localhost:3003/assets/remoteEntry.js`.
  - Global navigation and routing are orchestrated in `shell-app`, while components are rendered lazily with `React.Suspense` and fallback spinners.
  - Custom DOM events (`ucp:cart:add`) coordinate cart additions seamlessly across decoupled bundles.

---

## 2. Feature: Atomic Stock Reservation (`FT-12`)
- **Technology**: Spring Data JPA, PostgreSQL Row-Level Lock, Spring `@Transactional`.
- **Implementation Detail**:
  - `order-service` calls `product-service` via internal HTTP RestTemplate.
  - `product-service` executes:
    ```sql
    UPDATE products SET stock = stock - :quantity WHERE id = :productId AND stock >= :quantity
    ```
  - If 0 rows are affected, an `InsufficientStockException` is thrown, returning HTTP 409 and aborting order persistence.

---

## 3. Feature: Kafka DLQ Inspection & Replay (`FT-17`, `FT-18`)
- **Technology**: Spring Kafka, `@RetryableTopic`, Flyway `V2__add_idempotency_and_dlq.sql`.
- **Implementation Detail**:
  - `notification-service` attempts message processing up to 3 times with 1000ms exponential backoff.
  - Poison pills are caught by `@DltHandler` and written to `dead_letter_events`.
  - Operations staff can query `GET /api/notifications/dlq` and trigger re-execution via `POST /api/notifications/dlq/{id}/replay`.
