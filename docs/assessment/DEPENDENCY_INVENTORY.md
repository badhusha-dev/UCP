# Dependency Inventory & Version BOM

**Universal Commerce Platform**  
*Document Version:* 2.0.0  
*Assessment Date:* 2026-10-22  
*Author:* Lead DevOps & Backend Engineer  
*Status:* COMPLETE & VERIFIED

---

## 1. Backend Bill of Materials (BOM)

### 1.1 Core Platform & Frameworks
| Dependency | Group ID / Artifact ID | Version | Scope / Notes |
| :--- | :--- | :--- | :--- |
| **Java JDK** | OpenJDK / Temurin | 17 LTS / 21 LTS | Runtime & Compiler target |
| **Spring Boot Parent** | `org.springframework.boot:spring-boot-starter-parent` | 3.2.4 | Base framework |
| **Spring Cloud BOM** | `org.springframework.cloud:spring-cloud-dependencies` | 2023.0.1 | Cloud coordination |
| **Resilience4j BOM** | `io.github.resilience4j:resilience4j-bom` | 2.2.0 | Fault tolerance |

### 1.2 Common Starters & Libraries
| Component | Group ID / Artifact ID | Version | Purpose |
| :--- | :--- | :--- | :--- |
| Spring Web | `org.springframework.boot:spring-boot-starter-web` | 3.2.4 | MVC REST APIs |
| Spring WebFlux | `org.springframework.boot:spring-boot-starter-webflux` | 3.2.4 | Gateway reactive routing |
| Spring Data JPA | `org.springframework.boot:spring-boot-starter-data-jpa`| 3.2.4 | ORM & Relational DB |
| Spring Data Redis| `org.springframework.boot:spring-boot-starter-data-redis`| 3.2.4 | Cache & Key-value store |
| Spring Kafka | `org.springframework.kafka:spring-kafka` | 3.2.4 | Event streaming |
| Spring Security | `org.springframework.boot:spring-boot-starter-security`| 3.2.4 | Auth & RBAC filters |
| JWT JJWT | `io.jsonwebtoken:jjwt-api` / `jjwt-impl` / `jjwt-jackson`| 0.11.5 | Token signing/validation |
| PostgreSQL Driver| `org.postgresql:postgresql` | 42.6.2 | Relational DB Driver |
| Flyway Core | `org.flywaydb:flyway-core` | 10.10.0 | DB schema migrations |
| Springdoc OpenAPI| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 2.3.0 | Swagger UI & OpenAPI 3 |
| Eureka Client | `org.springframework.cloud:spring-cloud-starter-netflix-eureka-client`| 4.1.0 | Discovery registration |
| Config Client | `org.springframework.cloud:spring-cloud-starter-config`| 4.1.0 | Externalized configuration |
| Micrometer Prometheus| `io.micrometer:micrometer-registry-prometheus` | 1.12.4 | Metrics exporter |
| Micrometer Tracing| `io.micrometer:micrometer-tracing-bridge-otel` | 1.2.4 | OpenTelemetry tracing bridge |
| OTel Zipkin Exporter| `io.opentelemetry:opentelemetry-exporter-zipkin` | 1.34.1 | Distributed trace sender |
| Logstash Encoder | `net.logstash.logback:logstash-logback-encoder` | 7.4 | JSON log formatting |
| Lombok | `org.projectlombok:lombok` | 1.18.30 | Boilerplate reduction |

---

## 2. Frontend Microfrontends BOM

### 2.1 Workspace & Overrides (`frontend/package.json`)
| Package | Version | Purpose |
| :--- | :--- | :--- |
| **React** | `18.3.1` (Pinned via override) | Base UI rendering runtime |
| **React DOM** | `18.3.1` (Pinned via override) | DOM reconciler |
| **@types/react** | `^18.3.12` | TypeScript definitions |
| **@types/react-dom**| `^18.3.1` | TypeScript definitions |

### 2.2 Shell & Remote Applications
| Package | Version | Application / Purpose |
| :--- | :--- | :--- |
| **Vite** | `^8.1.0` | Development server & build tool |
| **@originjs/vite-plugin-federation**| `^1.4.1` | Microfrontend Module Federation plugin |
| **@reduxjs/toolkit** | `^2.12.0` | Global state management |
| **react-redux** | `^9.3.0` | React bindings for Redux |
| **react-router-dom** | `^7.18.0` | Client-side routing across MFEs |
| **@mui/material** | `^6.0.0` / `^6.5.0` | Component styling and design system |
| **@mui/icons-material**| `^6.5.0` | Design system vector icons |
| **@emotion/react** | `^11.13.0` | CSS-in-JS engine for MUI |
| **@emotion/styled** | `^11.13.0` | Styled components engine for MUI |
| **axios** | `^1.18.1` | HTTP client for REST Gateway calls |
| **typescript** | `~6.0.2` | Static type checking |
| **oxlint** | `^1.69.0` | Ultra-fast linter |

---

## 3. Infrastructure & Pinned Container Images

| Container Service | Base Image | Pinned Digest / Tag | Architecture |
| :--- | :--- | :--- | :--- |
| **PostgreSQL** | `postgres:15-alpine` | `15-alpine` | linux/amd64, arm64 |
| **Redis** | `redis:7-alpine` | `7-alpine` | linux/amd64, arm64 |
| **Apache Kafka** | `confluentinc/cp-kafka:7.4.0` | `7.4.0` | linux/amd64 |
| **ZooKeeper** | `confluentinc/cp-zookeeper:7.4.0` | `7.4.0` | linux/amd64 |
| **Kafka UI** | `provectuslabs/kafka-ui:latest` | `latest` | linux/amd64 |
| **Prometheus** | `prom/prometheus:v2.45.0` | `v2.45.0` | linux/amd64 |
| **Grafana** | `grafana/grafana:10.0.3` | `10.0.3` | linux/amd64 |
| **Alertmanager** | `prom/alertmanager:v0.25.0` | `v0.25.0` | linux/amd64 |
| **Zipkin** | `openzipkin/zipkin:latest` | `2.24.2` | linux/amd64 |
| **Elasticsearch** | `docker.elastic.co/elasticsearch/elasticsearch:8.11.1`| `8.11.1` | linux/amd64 |
| **Logstash** | `docker.elastic.co/logstash/logstash:8.11.1` | `8.11.1` | linux/amd64 |
| **Kibana** | `docker.elastic.co/kibana/kibana:8.11.1` | `8.11.1` | linux/amd64 |
| **Microservices Base**| `eclipse-temurin:17-jre-alpine` | `17-jre-alpine` | linux/amd64, arm64 |
| **Frontend Base** | `nginx:alpine` | `alpine` | linux/amd64, arm64 |
