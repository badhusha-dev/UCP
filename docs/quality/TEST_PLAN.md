# Quality Assurance & Test Plan

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Test Strategy & Scope

This quality plan defines the testing framework, test types, tools, and execution cadences for the Universal Commerce Platform:

```
[ Unit Tests ] ──> [ Contract Tests ] ──> [ Integration Tests ] ──> [ E2E Smoke Tests ]
(JUnit 5/Mock)     (WireMock/OpenAPI)     (Testcontainers)          (Synthetic & Shell)
```

---

## 2. Test Execution Levels

1. **Unit Testing**:
   - Executes during `mvn test`. Validates isolated business logic, DTO mapping, and edge case calculations.
2. **Contract Testing**:
   - Validates that REST controllers adhere to Springdoc OpenAPI 3.0 contracts.
3. **Database Integration Testing**:
   - Validates Flyway migration execution, repository queries, and atomic concurrency limits against PostgreSQL instances.
4. **End-to-End System Smoke Testing**:
   - Automated script (`scripts/smoke-test.sh` / `smoke-test.ps1`) executing the 5-step golden user flow against live Docker containers.
