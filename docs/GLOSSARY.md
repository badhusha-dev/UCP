# Platform Glossary & Definitions

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

| Term / Acronym | Full Form | Definition & Context in Platform |
| :--- | :--- | :--- |
| **API Gateway** | Application Programming Interface Gateway | Single entry point (port 8080) for all client requests; handles routing, auth, and rate limiting. |
| **BFF** | Backend For Frontend | Pattern where an intermediary API layer adapts backend services for specific frontend interfaces. |
| **Circuit Breaker**| Resilience4j Fault Tolerance Pattern | Prevents catastrophic cascade failures by failing fast when downstream services become unresponsive. |
| **DLQ** | Dead Letter Queue | Dedicated storage and topic where poison-pill or unparseable messages are moved after max retries fail. |
| **Eureka** | Netflix Eureka Service Registry | High-availability registration server (port 8761) tracking live microservice instances and health. |
| **Flyway** | Open-source Database Migration Tool | Version-controlled database schema management running SQL migrations (`V1__init.sql`) on boot. |
| **HPA** | Horizontal Pod Autoscaler | Kubernetes controller dynamically scaling pod replicas based on observed CPU and memory metrics. |
| **Idempotency** | Exactly-Once Processing Semantic | Ensuring an operation (like payment or event consumption) produces identical outcome if retried. |
| **JWT** | JSON Web Token | Cryptographically signed token encoding user identity, email, and granted authority roles (`ROLE_USER`). |
| **MFE** | Microfrontend | Architectural style where independent frontend components are developed and served at runtime. |
| **Module Federation**| Webpack / Vite Federation Protocol | Runtime sharing and dynamic loading of JavaScript code and React components across origins. |
| **OTel** | OpenTelemetry | Vendor-neutral telemetry framework used for generating distributed traces across HTTP and Kafka. |
| **RBAC** | Role-Based Access Control | Authorization mechanism restricting endpoint execution based on user permissions (`USER`, `ADMIN`). |
| **Zipkin** | Distributed Tracing System | Visualizer for timing and network hops across microservices via trace and span IDs. |
