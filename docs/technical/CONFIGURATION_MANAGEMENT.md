# Configuration Management Specification

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Centralized Configuration Architecture

The platform uses **Spring Cloud Config Server** (Port 8888) backed by a local or remote Git repository (`backend/config-repo/`). This centralizes properties across all microservices, eliminating configuration drift.

```
[ Git Config Repository ] ──> [ Config Server :8888 ]
                                       ▲
                   ┌───────────────────┼───────────────────┐
                   │                   │                   │
            [ User Service ]    [ Product Service ]   [ Order Service ]
```

---

## 2. Profile Hierarchy & Inheritance

Spring Cloud Config resolves configuration using a hierarchical inheritance model:

1. **`application.yml`**: Shared baseline inherited by all services:
   - Actuator management endpoints
   - Logging formats and Logstash destinations
   - Resilience4j default circuit breaker timeouts
   - Micrometer Prometheus metrics and Zipkin tracing endpoints
2. **`application-docker.yml`**: Overrides hostnames for container networking (`postgres`, `redis`, `kafka`, `discovery-server`).
3. **`<service-name>.yml`** (e.g. `order-service.yml`): Service-specific properties:
   - Database connection strings (`jdbc:postgresql://postgres:5432/order_db`)
   - Kafka topic names and consumer group IDs
   - Dedicated circuit breaker thresholds

---

## 3. Microservice Bootstrapping

Microservices configure their initial contact with Config Server via `src/main/resources/bootstrap.yml`:
```yaml
spring:
  application:
    name: order-service
  cloud:
    config:
      uri: http://config-server:8888
      fail-fast: true
      retry:
        max-attempts: 6
        initial-interval: 2000ms
```
- Setting `fail-fast: true` ensures that if the service cannot fetch its required configuration, it terminates safely instead of starting with dangerous unconfigured defaults.
