# Security Architecture Specification

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Zero-Trust Security Model

The Universal Commerce Platform adopts defense-in-depth principles:
1. **Perimeter Defense**: Only the API Gateway (port 8080) and Storefront Shell (port 3000) are exposed to external traffic. Backend databases, message brokers, and internal microservice ports are strictly firewalled or contained within private container networks.
2. **Stateless Identity Assertion**: Clients never communicate directly with domain microservices. All identity assertions are signed via HMAC-SHA256 JWT tokens.
3. **Database Segregation**: No single database credential grants access across service boundaries. `user-service` has zero visibility into `order_db`.

---

## 2. OWASP Top 10 Defensive Controls

| Vulnerability Domain | Threat Vector | Platform Defensive Control |
| :--- | :--- | :--- |
| **A01: Broken Access Control** | Unauthorized user querying another's orders | API Gateway validates JWT; `order-service` filters orders by `userId` in `X-User-Id` header. |
| **A02: Cryptographic Failures** | Plaintext password exposure in database | Passwords hashed using Spring Security `BCryptPasswordEncoder` (cost 10). Sensitive connection strings externalized. |
| **A03: Injection (SQL / Command)**| SQL injection via catalog search | Hibernate parameterized prepared statements used exclusively; zero string concatenation queries. |
| **A04: Insecure Design** | Flash sale inventory overselling | Atomic SQL update queries (`UPDATE ... WHERE stock >= qty`) with DB check constraints. |
| **A05: Security Misconfiguration**| Default passwords in production | Pre-flight validation script verifies non-default `.env` secrets; actuator endpoints secured behind Gateway. |
| **A06: Vulnerable Components** | Outdated jar dependencies with CVEs | Parent POM uses pinned BOMs; dependency-check plugins integrated into CI pipeline. |
| **A07: Identification Failures** | Brute force authentication attacks | Rate limiting filters on API Gateway; BCrypt computational cost delays rapid guessing. |
| **A08: Software & Data Integrity**| Poison-pill Kafka message corrupting worker| Non-blocking `@RetryableTopic` isolates corrupted events into a quarantine DLQ without crashing the listener. |
| **A09: Logging & Monitoring Failures**| Attacks go unnoticed | Real-time security and auth failure metrics scraped by Prometheus; structured JSON audit logs in Elasticsearch. |
| **A10: Server-Side Request Forgery**| Tampered image URLs in product records | Image URLs validated against whitelist; no arbitrary outbound HTTP requests permitted by domain services. |
