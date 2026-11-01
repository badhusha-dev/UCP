# Architectural Decision Records (ADRs)

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## ADR-01: Microfrontends via Vite Module Federation

- **Status**: ACCEPTED & IMPLEMENTED
- **Context**: The frontend was originally conceived as a monolith, making independent feature releases across checkout, catalog, and user profile teams risky and slow.
- **Decision**: Adopt `@originjs/vite-plugin-federation` to decouple the UI into a host shell (`shell-app` on port 3000) and remote microfrontends (`mfe-product` on 3001, `mfe-order` on 3002, `mfe-user` on 3003).
- **Consequences**: Enables zero-downtime independent deployment and distinct ownership. Requires shared dependency pinning (`react`, `react-dom`, `@reduxjs/toolkit`) to avoid runtime conflicts.

---

## ADR-02: Spring Cloud Gateway as Primary API Gateway

- **Status**: ACCEPTED & IMPLEMENTED
- **Context**: The platform needed edge ingress that integrates dynamically with Eureka discovery, evaluates JWT Bearer tokens natively, and applies reactive circuit breakers.
- **Decision**: Deploy Spring Cloud Gateway on port 8080 built on Spring WebFlux and Project Reactor.
- **Consequences**: Seamless integration with Spring Security and Eureka service registry. Non-blocking I/O ensures high connection concurrency.

---

## ADR-03: Database-Per-Service Pattern with Flyway Migrations

- **Status**: ACCEPTED & IMPLEMENTED
- **Context**: Monolithic shared databases create tight schema coupling and prevent independent microservice scaling.
- **Decision**: Provision isolated databases (`user_db`, `product_db`, `order_db`, `notification_db`) in PostgreSQL. Enforce schema migrations through Flyway versioned SQL scripts. Set Hibernate `ddl-auto` to `validate`.
- **Consequences**: Eliminates database-level coupling. Schema evolution is versioned in git and applied deterministically on startup.

---

## ADR-04: Multi-Layer Redis Caching for Product Catalog

- **Status**: ACCEPTED & IMPLEMENTED
- **Context**: Product catalog browsing represents over 85% of traffic volume in e-commerce workflows. Repeated database queries for popular products create unnecessary database load.
- **Decision**: Implement Redis 7 as an L2 distributed cache in `product-service` utilizing Spring Cache annotations (`@Cacheable`, `@CacheEvict`).
- **Consequences**: Sub-millisecond response times for read-heavy operations. Cache entries must be explicitly invalidated upon product inventory or price updates.

---

## ADR-05: Apache Kafka for Asynchronous Order Events & DLQ

- **Status**: ACCEPTED & IMPLEMENTED
- **Context**: High-throughput checkout should not block on notification dispatch or slow third-party email providers. Duplicate notifications must also be strictly prevented.
- **Decision**: Stream order lifecycle events to Kafka topic `order-events` keyed by `orderId`. In `notification-service`, implement consumer idempotency (`processed_events` table) and `@RetryableTopic` with a persistent Dead Letter Queue (`DeadLetterEvent`).
- **Consequences**: Guaranteed at-least-once delivery with exactly-once processing semantics. Poison-pill messages are isolated into the DLQ and can be replayed via REST API.

---

## ADR-06: Pinned React 18.3.1 Overrides

- **Status**: ACCEPTED & IMPLEMENTED
- **Context**: React 19 introduced internal reconciler changes (`Symbol(react.transitional.element)`) that triggered runtime minified error #31 when loaded across Vite Module Federation boundaries.
- **Decision**: Pin React and ReactDOM strictly to `18.3.1` across the root workspace `package.json` using npm `overrides`.
- **Consequences**: Guaranteed runtime stability across all remote microfrontends and shell host.
