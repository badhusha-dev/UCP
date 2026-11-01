# Microservices Catalog & Service Inventory

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Catalog Summary

| Service Name | Port | Database | Primary Responsibility | Discovery App ID |
| :--- | :--- | :--- | :--- | :--- |
| **`api-gateway`** | 8080 | N/A | Edge reverse proxy, JWT validation, circuit breaker routing | `API-GATEWAY` |
| **`user-service`** | 8081 | `user_db` | Authentication, user profiles, address books, RBAC | `USER-SERVICE` |
| **`product-service`**| 8082 | `product_db` | Product catalog, search, Redis caching, stock decrement | `PRODUCT-SERVICE` |
| **`order-service`** | 8083 | `order_db` | Cart checkout, state machine, Kafka event publishing | `ORDER-SERVICE` |
| **`notification-service`**| 8084 | `notification_db`| Kafka event consumer, idempotency engine, DLQ & replay | `NOTIFICATION-SERVICE` |
| **`discovery-server`**| 8761 | In-memory | Netflix Eureka dynamic service registration & heartbeat | `DISCOVERY-SERVER` |
| **`config-server`** | 8888 | Git repo | Centralized application configuration profiles | `CONFIG-SERVER` |

---

## 2. Microservice Detailed Specifications

### 2.1 User Service (`user-service`)
- **Port**: `8081`
- **Database**: PostgreSQL `user_db` (Flyway baseline `V1__init_user_schema.sql`)
- **Key Endpoints**:
  - `POST /api/users/register`: Registers user with BCrypt password hash.
  - `POST /api/users/login`: Authenticates credentials; returns signed JWT Bearer token.
  - `GET /api/users/profile`: Retrieves authenticated user details.
  - `GET /api/users/addresses`: Retrieves user address list.
  - `POST /api/users/addresses`: Appends new shipping address.
- **Actuator Health**: `http://localhost:8081/actuator/health`

### 2.2 Product Service (`product-service`)
- **Port**: `8082`
- **Database**: PostgreSQL `product_db` + Redis 7 cache
- **Key Endpoints**:
  - `GET /api/products`: Lists products with pagination and category/price filtering.
  - `GET /api/products/{id}`: Detailed product info cached in Redis `@Cacheable("products")`.
  - `GET /api/products/search?q=`: Full-text title/description search.
  - `POST /api/products/{id}/decrement-stock`: Atomic stock decrement called by `order-service`.
- **Actuator Health**: `http://localhost:8082/actuator/health`

### 2.3 Order Service (`order-service`)
- **Port**: `8083`
- **Database**: PostgreSQL `order_db` (Flyway baseline `V1__init_order_schema.sql`)
- **Key Endpoints**:
  - `POST /api/orders/checkout`: Validates items, calculates total price, decrements stock via `product-service`, persists order with status `PLACED`, and emits Kafka event.
  - `GET /api/orders/my-orders`: Returns customer order history.
  - `GET /api/orders/{id}`: Returns order details with items and tracking state.
  - `PUT /api/orders/{id}/status`: Transitions order state (`PLACED` -> `CONFIRMED` -> `SHIPPED` -> `DELIVERED`).
- **Kafka Producer**: Publishes to `order-events` with transactional callback.
- **Actuator Health**: `http://localhost:8083/actuator/health`

### 2.4 Notification Service (`notification-service`)
- **Port**: `8084`
- **Database**: PostgreSQL `notification_db` (Flyway baseline `V1__init_notification_schema.sql` and `V2__add_idempotency_and_dlq.sql`)
- **Key Capabilities**:
  - Kafka `@KafkaListener(topics = "order-events", groupId = "notification-group")`.
  - Idempotent deduplication against `processed_events` table using key `ORDER-{orderId}-{eventType}`.
  - Non-blocking `@RetryableTopic` with exponential backoff (3 attempts).
  - Dead Letter persistence into `DeadLetterEvent` repository.
  - Replay endpoints: `GET /api/notifications/dlq`, `POST /api/notifications/dlq/{id}/replay`.
- **Actuator Health**: `http://localhost:8084/actuator/health`
