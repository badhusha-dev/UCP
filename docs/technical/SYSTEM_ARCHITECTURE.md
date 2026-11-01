# System Architecture Specification

**Universal Commerce Platform**  
*Document Version:* 2.0.0  
*Classification:* Core Technical Specification

---

## 1. System Topology & Logical Architecture

The Universal Commerce Platform is structured across four primary logical planes:
1. **Presentation Plane**: Microfrontends implemented in React 18 and TypeScript, orchestrated via Vite Module Federation. The Host Shell (`shell-app`, port 3000) dynamically renders Remote Modules (`mfe-product`, `mfe-order`, `mfe-user`).
2. **Control & Routing Plane**: Spring Cloud API Gateway (port 8080) coupled with Netflix Eureka Discovery Server (port 8761) and Spring Cloud Config Server (port 8888).
3. **Application & Domain Services Plane**: Four independent Spring Boot microservices:
   - `user-service` (Port 8081): Identity, Authentication, RBAC, Profiles, Addresses.
   - `product-service` (Port 8082): Catalog, Categories, Search, Redis Caching, Inventory Levels.
   - `order-service` (Port 8083): Cart Checkout, Server-side Pricing, Order State Transitions, Inventory Reservation.
   - `notification-service` (Port 8084): Asynchronous Kafka Consumer, Idempotency Tracker, Dead Letter Queue (DLQ).
4. **Data & Storage Plane**:
   - PostgreSQL 15 Relational Databases (`user_db`, `product_db`, `order_db`, `notification_db`).
   - Redis 7 In-Memory Key-Value & Cache Store.
   - Apache Kafka 7.4.0 Event Broker with ZooKeeper coordination.

---

## 2. Ingress & Routing Mechanics

All external client traffic targets the Spring Cloud API Gateway on port 8080. The Gateway handles:
- **Path Stripping & Discovery Routing**: Requests matching `/api/users/**` route to `lb://user-service`, `/api/products/**` to `lb://product-service`, and `/api/orders/**` to `lb://order-service`.
- **JWT Authentication Filter**: Decodes the `Authorization: Bearer <token>` header, validates HMAC-SHA256 signatures, extracts claims (`userId`, `sub`, `roles`), and injects custom downstream headers (`X-User-Id`, `X-User-Roles`).
- **Resilience4j Reactive Circuit Breakers**: Protects against downstream microservice outages. If `order-service` or `product-service` latency exceeds 3000ms or fails repeatedly, the gateway trips to a fallback controller returning HTTP 503 or graceful cached degradation.

---

## 3. Communication Topologies

### 3.1 Synchronous In-Flight Requests
- Frontend MFEs interact with the API Gateway via HTTP/1.1 and JSON payloads.
- Inter-service REST calls (e.g. `order-service` invoking `product-service` to decrement stock) utilize Spring's `RestTemplate` instrumented with Micrometer Observation Registry for distributed trace header propagation (`traceparent`).

### 3.2 Asynchronous Event Streams
- When an order transition occurs, `order-service` publishes an `OrderEvent` to Kafka topic `order-events` using partition key `orderId`.
- `notification-service` consumes events with group `notification-group`, verifies duplicate event protection against `processed_events`, and triggers downstream notifications.
