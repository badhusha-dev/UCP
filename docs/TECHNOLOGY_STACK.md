# Technology Stack & Tooling Inventory

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Programming Languages & Runtimes

| Layer | Language / Runtime | Version | Justification |
| :--- | :--- | :--- | :--- |
| **Backend Services** | Java (OpenJDK / Eclipse Temurin) | 17 LTS / 21 LTS | High throughput, modern virtual threads compatibility, strong typing, mature ecosystem. |
| **Frontend MFEs** | TypeScript / ECMAScript Modules | TypeScript ~6.0.2 / ES2022 | Strict type safety across remote component boundaries and API contracts. |
| **Frontend Runtime**| Node.js / V8 Engine | Node 20.x / 22.x LTS | High-speed Vite compilation and build toolchain. |
| **Scripting / Automation**| POSIX Bash / PowerShell Core | Bash 5+ / PowerShell 7+ | Universal automation compatibility across Linux servers and Windows development nodes. |

---

## 2. Backend Frameworks & Libraries

- **Spring Boot 3.2.4**: Standalone microservice framework with native Observability integration.
- **Spring Cloud 2023.0.1 (Leyton)**:
  - Spring Cloud Gateway (Reactive, Netty-based edge proxy)
  - Spring Cloud Netflix Eureka (Decentralized service discovery)
  - Spring Cloud Config (Externalized centralized Git configuration)
- **Resilience4j 2.2.0**: Fault tolerance suite providing CircuitBreakers, Retries, Bulkheads, and RateLimiters.
- **Spring Data JPA & Hibernate 6**: Object-relational mapping and database access layer.
- **Flyway 10.10.0**: Database schema migration and version control.
- **Spring Kafka**: Native reactive and event-driven Apache Kafka consumer/producer abstraction.
- **Springdoc OpenAPI 2.3.0**: Automated OpenAPI 3.0 specification generation and Swagger UI aggregation.
- **JJWT 0.11.5**: JSON Web Token encoding, signing, and verification.

---

## 3. Frontend Architecture & Tooling

- **React 18.3.1**: Modern concurrent rendering UI library (pinned via npm overrides to prevent version collisions).
- **Vite 8.1.0**: Next-generation frontend build tool with near-instant hot module replacement (HMR).
- **@originjs/vite-plugin-federation 1.4.1**: Module Federation implementation for Vite, facilitating decoupled remote deployment.
- **Redux Toolkit 2.12.0**: Predictable global state management across federated modules.
- **React Router DOM 7.18.0**: Client-side single-page application routing.
- **Material UI (MUI) v6**: Accessible enterprise UI component system.

---

## 4. Persistence & Message Streaming

- **PostgreSQL 15 (Alpine)**: Primary ACID-compliant relational database.
- **Redis 7 (Alpine)**: Ultra-fast in-memory cache for catalog data and token invalidation.
- **Apache Kafka 7.4.0 (Confluent)**: Distributed event streaming platform for asynchronous order state dispatch.
- **ZooKeeper 7.4.0**: Distributed consensus and metadata management for Kafka broker.

---

## 5. Observability & SRE Stack

- **Prometheus 2.45.0**: Time-series metrics scraping engine monitoring Spring Boot `/actuator/prometheus`.
- **Grafana 10.0.3**: Rich metrics dashboard visualization.
- **Alertmanager 0.25.0**: Alert routing and notification management.
- **OpenZipkin 2.24.2**: Distributed tracing backend supporting OpenTelemetry (OTel) spans.
- **Elasticsearch 8.11.1**: Centralized log search and indexing.
- **Logstash 8.11.1**: TCP log receiver parsing Logback JSON events.
- **Kibana 8.11.1**: Web interface for log exploration and trace-to-log correlation.
