# Security Threat Model (STRIDE Methodology)

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. STRIDE Threat Analysis

| Threat Category | Potential Attack Vector | Platform Defensive Countermeasure |
| :--- | :--- | :--- |
| **Spoofing Identity** | Attacker crafts illegitimate JWT tokens to impersonate administrators. | HMAC-SHA256 signature verified on API Gateway using high-entropy `JWT_SECRET`; exp timestamp enforced. |
| **Tampering with Data** | Attacker modifies line item pricing in checkout JSON payload to pay $0.01. | Order Service ignores client price claims and recalculates total using verified database catalog prices. |
| **Repudiation** | Customer denies placing an order. | Orders immutable in `order_db` with timestamps, customer ID, IP, and event logs in Elasticsearch. |
| **Information Disclosure**| Plaintext database passwords or customer emails leaking in log files. | Secrets externalized; Logback filters exclude password fields; database connections isolated on internal bridge. |
| **Denial of Service (DoS)**| Flash sale checkout bot swarms overwhelming microservices. | Gateway rate limiting, Resilience4j bulkheads and circuit breakers protect backend thread pools. |
| **Elevation of Privilege**| Standard customer executes admin-only DLQ replay API. | Method-level `@PreAuthorize("hasRole('ADMIN')")` enforced at Spring Security controller boundary. |
