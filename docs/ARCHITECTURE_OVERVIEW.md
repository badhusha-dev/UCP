# Architecture Overview

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Architectural Philosophy

The **Universal Commerce Platform (UCP)** adopts a decentralized, cloud-native architecture adhering to:
- **Database-Per-Service Pattern**: Each domain microservice owns its private database schema (`user_db`, `product_db`, `order_db`, `notification_db`). Cross-database queries are strictly forbidden.
- **Frontend Microfrontends (Module Federation)**: User interface features are segmented into autonomous deployables, loaded dynamically into the `shell-app` host.
- **Eventual Consistency via Kafka**: Order fulfillment and notification dispatch rely on asynchronous event streams (`order-events`), decoupling order placement latency from downstream systems.
- **Edge Routing & Zero-Trust Authentication**: Inbound external requests pass through Spring Cloud Gateway, which validates JWT Bearer tokens before forwarding headers (`X-User-Id`, `X-User-Roles`) to backend microservices.

---

## 2. Layered Architecture Representation

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PRESENTATION TIER (MFEs)                        │
│   Storefront Shell (:3000) ──> [ Product MFE ] [ Order MFE ] [ User MFE ]│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTP / REST / JSON
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                           EDGE INGRESS TIER                            │
│           Spring Cloud API Gateway (:8080)                             │
│   - JWT Bearer Authentication Filter                                   │
│   - Resilience4j Reactive Circuit Breaker                             │
│   - Dynamic Path Routing via Netflix Eureka Discovery                  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
       ┌────────────────────────────┼────────────────────────────┐
       ▼                            ▼                            ▼
┌──────────────┐             ┌──────────────┐             ┌──────────────┐
│ User Service │             │Product Service             │Order Service │
│   (:8081)    │             │   (:8082)    │             │   (:8083)    │
└──────┬───────┘             └──────┬───────┘             └──────┬───────┘
       │                            │                            │
       ▼                            ▼                            ▼
 (PostgreSQL)                 (PostgreSQL)                 (PostgreSQL)
   user_db                      product_db                   order_db
                                    │                            │
                               (Redis L2)                        │ Kafka Event
                                                                 ▼
                                                        ┌─────────────────┐
                                                        │ Apache Kafka    │
                                                        │ (:9092)         │
                                                        └────────┬────────┘
                                                                 │
                                                                 ▼
                                                        ┌─────────────────┐
                                                        │Notification Svc │
                                                        │ (:8084) + DLQ   │
                                                        └────────┬────────┘
                                                                 │
                                                                 ▼
                                                           (PostgreSQL)
                                                          notification_db
```

---

## 3. Communication Patterns

### 3.1 Synchronous Communication (HTTP / REST)
Used when immediate, strongly consistent feedback is required:
- Client authentication and profile retrieval.
- Product catalog search and browsing.
- Order checkout and stock reservation (Atomic REST call between `order-service` and `product-service` wrapped in a circuit breaker).

### 3.2 Asynchronous Communication (Apache Kafka)
Used for decoupled workflows where operations should not block the customer experience:
- Order status state transitions (`ORDER_CREATED`, `ORDER_CONFIRMED`, `ORDER_CANCELLED`).
- Consumer idempotency tracking in `notification-service` to ensure transactional emails and alerts are dispatched exactly once.
- Poison-pill message segregation into Dead Letter Queues (DLQ) with RESTful replay capabilities.

---

## 4. Observability Fabric

1. **Distributed Tracing**: OpenTelemetry (OTel) context injected into HTTP request headers (`traceparent`) and Kafka record headers, aggregated by Zipkin.
2. **Metrics Collection**: Microservices export Micrometer metrics scraped every 15 seconds by Prometheus.
3. **Log Aggregation**: Asynchronous Logback TCP appenders stream structured JSON logs to Logstash, indexed into Elasticsearch, and explored in Kibana.
