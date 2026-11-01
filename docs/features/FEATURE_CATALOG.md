# Implemented Feature Catalog

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

| ID | Feature Name | Category | Service Owner | Primary Endpoint / Hook | Verification Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **FT-01** | Microfrontend Shell Orchestration | Frontend | `shell-app` | `http://localhost:3000` | Verified (Port 3000 UP) |
| **FT-02** | Product Catalog Remotes | Frontend | `mfe-product` | `http://localhost:3001` | Verified (Port 3001 UP) |
| **FT-03** | Order & Cart Drawer Remotes | Frontend | `mfe-order` | `http://localhost:3002` | Verified (Port 3002 UP) |
| **FT-04** | User Profile & Auth Modal Remotes | Frontend | `mfe-user` | `http://localhost:3003` | Verified (Port 3003 UP) |
| **FT-05** | API Gateway Dynamic Routing | Ingress | `api-gateway` | `http://localhost:8080` | Verified (Port 8080 UP) |
| **FT-06** | JWT Token Minting & Authentication| Auth | `user-service` | `POST /api/users/login` | Verified (Token generated) |
| **FT-07** | BCrypt Password Encryption | Security | `user-service` | `POST /api/users/register` | Verified (Hash cost 10) |
| **FT-08** | Customer Address Management | Profile | `user-service` | `GET/POST /api/users/addresses`| Verified (Postgres persisted) |
| **FT-09** | Category & Price Filtered Browsing | Catalog | `product-service` | `GET /api/products` | Verified (Filtered JSON) |
| **FT-10** | Full-Text Product Search | Catalog | `product-service` | `GET /api/products/search?q=` | Verified (Fuzzy match) |
| **FT-11** | Redis L2 Catalog Caching | Performance | `product-service` | `@Cacheable("products")` | Verified (Cache hit) |
| **FT-12** | Atomic Stock Decrement | Inventory | `product-service` | `POST /decrement-stock` | Verified (Row lock update) |
| **FT-13** | Server-Side Price Verification | Checkout | `order-service` | `POST /api/orders/checkout` | Verified (Calculates total) |
| **FT-14** | Order State Machine | Lifecycle | `order-service` | `PUT /api/orders/{id}/status` | Verified (PLACED to CONFIRMED)|
| **FT-15** | Idempotent Kafka Event Publishing | Messaging | `order-service` | Topic `order-events` | Verified (acks=all) |
| **FT-16** | Notification Event Deduplication | Messaging | `notification-service` | Table `processed_events` | Verified (Key checked) |
| **FT-17** | Non-Blocking Retry & DLQ | Resilience | `notification-service` | `@RetryableTopic` | Verified (DLQ stored) |
| **FT-18** | DLQ Inspection & Replay API | Operations | `notification-service` | `GET/POST /api/notifications/dlq`| Verified (Replay HTTP 200) |
| **FT-19** | Eureka Dynamic Service Discovery | Platform | `discovery-server` | `http://localhost:8761` | Verified (Heartbeats green) |
| **FT-20** | Git-Backed Config Server | Config | `config-server` | `http://localhost:8888` | Verified (Serves profiles) |
| **FT-21** | Distributed Tracing & Zipkin | Observability | `zipkin` | `http://localhost:9411` | Verified (13 spans captured) |
| **FT-22** | Prometheus Metrics & Grafana | Observability | `prometheus` / `grafana`| `http://localhost:3030` | Verified (Dashboards live) |
| **FT-23** | Centralized Logging (ELK) | Observability | `logstash` / `kibana` | `http://localhost:5601` | Verified (Status green) |
| **FT-24** | Customer Account Statement & Balance | Accounting | `order-service` | `GET /api/accounting/balance` | Verified (Net balance logic) |
| **FT-25** | Immutable Subledger & Non-Destructive Reversals | Accounting | `order-service` | `POST /api/accounting/admin/reversals` | Verified (Compensating entries) |
| **FT-26** | Administrative Audit Trail & Adjustments | Accounting | `order-service` | `GET/POST /api/accounting/admin/*` | Verified (Actor & state logged) |
