# Project Overview

**Universal Commerce Platform**  
*Document Version:* 2.0.0  
*Classification:* Technical & Architectural Overview  
*Last Updated:* 2026-10-22

---

## 1. Vision & Purpose

The **Universal Commerce Platform (UCP)** is designed to deliver a resilient, cloud-native digital storefront and fulfillment engine. Modern e-commerce platforms struggle with monolithic bottlenecks: deploy risks, single points of failure, lack of tracing across order checkout steps, and tight coupling between frontend UI components and backend business logic.

UCP addresses these core challenges through:
1. **Microfrontend Architecture**: Decoupled, independently deployable React modules loaded at runtime using Vite Module Federation.
2. **Domain-Driven Microservices**: Isolated Spring Boot microservices partitioned around natural business bounded contexts (`User`, `Product`, `Order`, `Notification`).
3. **Event-Driven Resilience**: Asynchronous Kafka event processing with consumer idempotency and non-blocking dead letter queue (DLQ) replay capabilities.
4. **End-to-End Observability**: Unified distributed tracing (Zipkin/OTel), metrics scraping (Prometheus), dashboard visualization (Grafana), and centralized log indexing (ELK Stack).

---

## 2. Core Architectural Pillars

- **Zero-Downtime Independently Deployable Remotes**: The customer storefront shell orchestrates separate remote bundles (`mfe-product`, `mfe-order`, `mfe-user`) without requiring full platform rebuilds.
- **Fail-Safe Ingress & Traffic Management**: The Spring Cloud API Gateway acts as the secure reverse proxy, handling path-based routing, JWT bearer authentication, and Resilience4j reactive circuit breaking.
- **Transactional Consistency & Compensating Operations**: Order checkout enforces strict database transactions, server-side price recalculation, and stock reservation.
- **Operational Portability**: Zero hardcoded localhost assumptions in production container topologies. Full support for Docker Compose, Helm 3, Kubernetes, and automated Terraform infrastructure provisioning.

---

## 3. High-Level System Scope

```
[Storefront Shell] ───> [Microfrontends: Product, Order, User]
        │
        ▼ (HTTPS / REST)
[API Gateway :8080]
   ├──> [User Service :8081] ───────> (PostgreSQL: user_db)
   ├──> [Product Service :8082] ────> (PostgreSQL: product_db + Redis Cache)
   ├──> [Order Service :8083] ──────> (PostgreSQL: order_db) ──> [Kafka :9092]
   └──> [Notification Service :8084] ───────────────────────────> (DLQ & Postgres)
```
