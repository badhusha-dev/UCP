# API Gateway Architecture & Routing Specification

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Overview & Gateway Responsibilities

The **Spring Cloud API Gateway** (Port 8080) acts as the unified reverse proxy and security perimeter for all inbound HTTP client traffic. Built on Spring WebFlux and Netty, it handles:
- **Dynamic Service Discovery Routing**: Routes requests to microservice instances registered in Netflix Eureka (`lb://SERVICE-NAME`).
- **Edge Authentication**: Validates JWT Bearer tokens before forwarding requests to backend domain services.
- **Header Enrichment**: Decodes token claims and injects downstream headers:
  - `X-User-Id`: Numerical ID of the authenticated user.
  - `X-User-Email`: Email address of the user.
  - `X-User-Roles`: Comma-separated list of granted authorities (`ROLE_USER,ROLE_ADMIN`).
- **Fault Tolerance**: Circuit breaker protection via Resilience4j Reactive Gateway filters.
- **CORS Handling**: Permissive browser cross-origin headers for frontend remotes on ports 3000-3003.

---

## 2. Route Configuration Matrix

| Route ID | Path Predicate | Target URI | Filters Applied |
| :--- | :--- | :--- | :--- |
| `user-service-route` | `/api/users/**` | `lb://user-service` | `StripPrefix=0`, `JwtAuthenticationFilter` (secured endpoints) |
| `product-service-route` | `/api/products/**` | `lb://product-service` | `StripPrefix=0`, CircuitBreaker (`name: productCircuitBreaker`) |
| `order-service-route` | `/api/orders/**` | `lb://order-service` | `StripPrefix=0`, `JwtAuthenticationFilter`, CircuitBreaker (`name: orderCircuitBreaker`) |
| `notification-service-route`| `/api/notifications/**`| `lb://notification-service`| `StripPrefix=0`, `JwtAuthenticationFilter` |
| `swagger-ui-route` | `/v3/api-docs/**` | Aggregated per service | OpenAPI 3.0 specs grouped under `/swagger-ui.html` |

---

## 3. Resilience4j Reactive Gateway Circuit Breakers

When downstream services experience latency spikes or high error rates, the Gateway trips the circuit:
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: order-service-route
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
          filters:
            - name: CircuitBreaker
              args:
                name: orderCircuitBreaker
                fallbackUri: forward:/fallback/orders
```

### Fallback Controller
When `forward:/fallback/orders` is invoked, the gateway returns:
- HTTP 503 Service Unavailable with a structured JSON payload:
```json
{
  "timestamp": "2026-10-22T16:00:00Z",
  "status": 503,
  "error": "Service Unavailable",
  "message": "Order Service is temporarily degraded. Please retry your request shortly.",
  "path": "/api/orders/checkout"
}
```
