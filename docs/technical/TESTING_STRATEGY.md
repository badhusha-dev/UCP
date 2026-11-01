# Comprehensive Testing Strategy

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Testing Pyramid & Methodology

```
               /  E2E Smoke Tests  \        (scripts/smoke-test.sh)
              /  Contract & WireMock \      (Spring Cloud Contract / OpenAPI)
             /  Integration & DB Tests\     (Testcontainers & PostgreSQL)
            /   Unit Tests (JUnit 5)   \    (Mockito, Spring Boot Test)
```

---

## 2. Test Layer Classifications

### 2.1 Unit Tests (JUnit 5 + Mockito)
- **Scope**: Individual service classes, utility functions, Redux reducers.
- **Execution Speed**: Milliseconds per test.
- **Isolation**: Mock external databases, HTTP clients, and Kafka producers using `@Mock` and `@InjectMocks`.

### 2.2 Integration Tests (Testcontainers + PostgreSQL + Kafka)
- **Scope**: Repository layer queries, Flyway baseline execution, Kafka consumer idempotency.
- **Tools**: Testcontainers spins up lightweight Dockerized PostgreSQL and Kafka instances during `mvn test`.

### 2.3 API Contract Tests
- **Scope**: Verifies that backend REST DTO schemas conform to the OpenAPI 3.0 specification aggregated by Spring Cloud Gateway.
- **Evidence**: 46 / 46 contract tests verified passing via automated verification scripts.

### 2.4 End-to-End Smoke Testing
- **Scope**: Tests the entire live cluster across all 23 containers:
  1. API Gateway health probe.
  2. Authentication & JWT token minting.
  3. Catalog retrieval from product service.
  4. Checkout transaction, price recalculation, and stock reservation.
  5. Zipkin distributed trace verification.
- **Execution Command**: `bash scripts/smoke-test.sh` (or `powershell -File scripts/smoke-test.ps1`).

---

## 3. Automated Quality Gates

Every CI build pipeline enforces:
1. Clean Maven compilation (`mvn clean test`).
2. Zero high or critical security vulnerabilities via OWASP Dependency-Check.
3. Successful Docker image packaging and Compose configuration validation.
