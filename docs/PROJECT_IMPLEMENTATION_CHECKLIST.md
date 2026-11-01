# Universal Commerce Platform — Master Project Checklist

**Document Version:** 2.0.0  
**Status Date:** 2026-10-23  
**Verification Method:** Automated test execution across all 7 backend services, frontend unit test runners, source code inspection, and runtime synthetic probing.

---

## 1. Microservices & Backend Capabilities

| Item | Component | Capability / Requirement | Status | Verification Evidence |
| :--- | :--- | :--- | :--- | :--- |
| **B-01** | `discovery-server` | Service registration and heartbeat monitoring | Verified | Port 8761 UP; Eureka dashboard shows all microservices registered |
| **B-02** | `config-server` | Centralized Git-backed configuration server | Verified | Port 8888 UP; serves config for `docker` profile |
| **B-03** | `api-gateway` | Spring Cloud Gateway routing & JWT token validation | Verified | Port 8080 UP; routes `/api/users/**`, `/api/products/**`, `/api/orders/**` |
| **B-04** | `api-gateway` | Resilience4j reactive circuit breaker with fallback | Verified | Gateway fallback controller returns 503 / graceful degradation |
| **B-05** | `user-service` | User registration with BCrypt password hashing | Verified | `POST /api/users/register` returns 201; passwords hashed |
| **B-06** | `user-service` | JWT token minting and validation | Verified | `POST /api/users/login` returns signed Bearer token |
| **B-07** | `user-service` | Role-based authorization (`ROLE_USER`, `ROLE_ADMIN`) | Verified | `@PreAuthorize` tested; admin routes protected |
| **B-08** | `user-service` | Customer address management | Verified | `GET/POST /api/users/addresses` persisted in PostgreSQL |
| **B-09** | `product-service` | Product catalog querying with category & price filters | Verified | `GET /api/products` returns paginated JSON |
| **B-10** | `product-service` | Redis L2 caching with cache eviction | Verified | `@Cacheable("products")` verified with Redis keys in cache |
| **B-11** | `product-service` | Product stock tracking and decrement API | Verified | Stock levels decrement atomically during checkout |
| **B-12** | `order-service` | Cart checkout & server-side price calculation | Verified | `POST /api/orders/checkout` calculates exact total |
| **B-13** | `order-service` | Atomic inventory reservation call to product-service | Verified | Decrements product inventory inside transaction |
| **B-14** | `order-service` | Order lifecycle state transitions (`PLACED` -> `CONFIRMED`) | Verified | Order entity state machine verified via database inspect |
| **B-15** | `order-service` | Idempotent Kafka event publishing | Verified | Events published to `order-events` with orderId key |
| **B-16** | `notification-service` | Idempotent Kafka event consumption | Verified | `ProcessedEvent` table prevents duplicate handling |
| **B-17** | `notification-service` | Non-blocking retry & Dead Letter Queue (DLQ) | Verified | `@RetryableTopic` + `DeadLetterEvent` persisted on poison pill |
| **B-18** | `notification-service` | DLQ inspection and replay REST endpoints | Verified | `GET /api/notifications/dlq` and `POST /replay` verified |

---

## 2. Frontend Microfrontends (Vite Module Federation)

| Item | Component | Capability / Requirement | Status | Verification Evidence |
| :--- | :--- | :--- | :--- | :--- |
| **F-01** | `shell-app` | Host container orchestrating federated remotes | Verified | Port 3000 UP; loads navigation, header, and router |
| **F-02** | `shell-app` | Redux store & global authentication state | Verified | JWT token preserved and distributed to remote MFEs |
| **F-03** | `mfe-product` | Remote catalog component exposed via federation | Verified | Port 3001 UP; `ProductCatalog` renders with filters & search |
| **F-04** | `mfe-product` | Product detail view with real-time stock indicator | Verified | Product detail displays In Stock / Low Stock badges |
| **F-05** | `mfe-order` | Remote shopping cart drawer and checkout modal | Verified | Port 3002 UP; item additions update count & total |
| **F-06** | `mfe-order` | Order history and real-time status tracker | Verified | Displays list of placed orders with status badges |
| **F-07** | `mfe-user` | Remote authentication modal (Login / Register) | Verified | Port 3003 UP; modal submits to `/api/users/login` |
| **F-08** | `mfe-user` | User profile and shipping address management | Verified | Displays user info and address creation form |
| **F-09** | All MFEs | React 18.3.1 singleton sharing across federation | Verified | Clean console, no minified React #31 collision |

---

## 3. Infrastructure & Observability

| Item | Service | Role / Purpose | Status | Verification Evidence |
| :--- | :--- | :--- | :--- | :--- |
| **I-01** | `postgres` | Primary relational database for all microservices | Verified | Port 5432 UP; 4 distinct databases initialized |
| **I-02** | `flyway` | Automated schema versioning and migrations | Verified | V1/V2 baseline migration tables present across services |
| **I-03** | `redis` | In-memory L2 cache store | Verified | Port 6379 UP; `PONG` response on health probe |
| **I-04** | `kafka` | Distributed streaming event bus | Verified | Port 9092 UP; `order-events` topic active |
| **I-05** | `zookeeper` | Kafka cluster coordination | Verified | Port 2181 UP; active connection to Kafka broker |
| **I-06** | `kafka-ui` | Web administration UI for Kafka | Verified | Port 8090 UP; displays topics, partitions, and consumers |
| **I-07** | `prometheus` | Time-series metrics collection | Verified | Port 9090 UP; scraping all Spring Boot actuators |
| **I-08** | `grafana` | Visualization dashboards | Verified | Port 3030 UP; provisioned with Platform & JVM dashboards |
| **I-09** | `alertmanager` | Central alert dispatch and routing | Verified | Port 9093 UP; routes alerts to slack/email configs |
| **I-10** | `zipkin` | Distributed trace visualizer | Verified | Port 9411 UP; traces recorded across HTTP and Kafka |
| **I-11** | `elasticsearch` | Log search and indexing | Verified | Port 9200 UP; cluster health green |
| **I-12** | `logstash` | TCP log collector with JSON parser | Verified | Port 5000/9600 UP; ingests Logback appender output |
| **I-13** | `kibana` | Web UI for log analysis | Verified | Port 5601 UP; status green |

---

## 4. Production Readiness & Installation Suite

| Item | Requirement | Status | Verification Evidence |
| :--- | :--- | :--- | :--- |
| **P-01** | Automated environment validation (`validate-environment`) | Verified | Script checks OS, RAM (8GB+), CPU, Disk, Docker, Ports |
| **P-02** | One-command installation script (`install.sh` / `install.ps1`) | Verified | Fully automated end-to-end setup script |
| **P-03** | Synthetic health check probe (`health-check.sh` / `.ps1`) | Verified | 15 / 15 endpoints probed with HTTP 200 response |
| **P-04** | End-to-end golden flow smoke test (`smoke-test.sh` / `.ps1`) | Verified | Tests full user order cycle via REST API Gateway |
| **P-05** | Automated database backup & restore (`backup.sh` / `restore.sh`)| Verified | Backs up all 4 databases with SHA-256 checksums |
| **P-06** | Safe uninstallation workflow (`uninstall.sh`) | Verified | Protects persistent volumes unless explicitly forced |
| **P-07** | Comprehensive Technical, Business & Operational Documentation | Verified | Organized in dedicated directories with cross-links |


---

## 5. Accounting Subledger & Financial Governance

| Item | Component | Capability / Requirement | Status | Verification Evidence |
| :--- | :--- | :--- | :--- | :--- |
| **A-01** | `order-service` | Fixed-precision immutable transaction subledger (`NUMERIC(19,4)`) | Verified | Stored in PostgreSQL `ledger_transactions`; zero floating point |
| **A-02** | `order-service` | Dynamic Net Balance calculation with explanatory note | Verified | `GET /api/accounting/balance` calculates Credits minus Debits |
| **A-03** | `order-service` | Idempotent checkout debit & cancellation refund settlement | Verified | `ORDER_PAYMENT_<id>` and `ORDER_REFUND_<id>` idempotency keys |
| **A-04** | `order-service` | Non-destructive compensating transaction reversals | Verified | `POST /api/accounting/admin/reversals/{id}` creates linked offset entry |
| **A-05** | `order-service` | Comprehensive administrative audit trail logging | Verified | Stored in `accounting_audit_log` with before/after JSON states |
| **A-06** | `api-gateway` | Zero-Trust JWT claim injection & Anti-spoofing header stripping | Verified | Client `X-User-*` stripped; claims extracted; 403 on non-admin |
| **A-07** | `mfe-order` | Customer Statement & Transaction Ledger UI (`/orders/ledger`) | Verified | Net balance card, filter toolbar, paginated table, detail modal |
| **A-08** | `mfe-order` | Admin Accounting Console & Audit Trail UI (`/orders/ledger/admin`)| Verified | Manual adjustments modal, reversal modal, audit trail viewer |
| **A-09** | `order-service` | Customer Account Overview with Lifetime Metrics & Explanations | Verified | `GET /api/accounting/overview` with store credit, spend, refunds |
| **A-10** | `order-service` | Itemized Order Invoices & Receipts with Tokenized Reference | Verified | `GET /api/accounting/invoices/{orderId}` with printable modal & line items |
| **A-11** | `order-service` | Customer-Initiated Refund Workflow & Administrative Governance | Verified | `POST /refunds/request` and `POST /admin/refunds/{id}/process` verified live |
| **A-12** | `order-service` | Statement CSV & Master Ledger CSV Exports with Audit Logging | Verified | `/statements/export` and `/admin/export` verified with audit trail |
| **A-13** | `order-service` | Saved Tokenized Payment Method Metadata (PCI DSS Compliant) | Verified | `GET /api/accounting/payment-methods` returns tokenized card brand/last4 |
| **A-14** | `order-service` | Customer Accounting Communication Preferences | Verified | `GET/PUT /api/accounting/preferences` for email receipts & statements |
| **A-15** | `order-service` | Executive Accounting Dashboard with Subledger Disclaimer | Verified | `GET /api/accounting/admin/dashboard` verified with gross sales/refunds |
| **A-16** | `order-service` | Subledger Reconciliation & Discrepancy Flagging Management | Verified | `PUT /api/accounting/admin/reconciliation/{id}` with auditor notes |
| **A-17** | `api-gateway` | Fine-Grained Role Separation (`ROLE_MANAGER` vs `ROLE_ADMIN`) | Verified | Manager read-only access allowed; write actions return 403 Forbidden |

---

## 6. Quality Assurance, Automated Test Suites & Security Hardening

| Item | Layer / Area | Requirement / Capability | Status | Verification Evidence |
| :--- | :--- | :--- | :--- | :--- |
| **Q-01** | Backend | Full Microservices Automated Test Suite Execution | Verified | 53 / 53 backend automated tests passing across all 7 services |
| **Q-02** | `api-gateway` | Gateway Security, Routing & Header Sanitization Test Suite | Verified | 7 / 7 tests passed (`BUILD SUCCESS`) |
| **Q-03** | `user-service` | Authentication, Profile & JPA Integration Test Suite | Verified | 7 / 7 tests passed (`BUILD SUCCESS`) |
| **Q-04** | `product-service`| Controller, Catalog, Stock & WireMock Resilience Suite | Verified | 7 / 7 tests passed (`BUILD SUCCESS`) |
| **Q-05** | `order-service` | Order Flow, Payment, Accounting Security & Subledger Suite | Verified | 24 / 24 tests passed (`BUILD SUCCESS`) |
| **Q-06** | `notification-service` | Kafka Event Consumer Idempotency & DLQ Replay Suite | Verified | 6 / 6 tests passed (`BUILD SUCCESS`) |
| **Q-07** | Infrastructure | Discovery & Config Server Context Load Suites | Verified | 2 / 2 tests passed (`BUILD SUCCESS`) |
| **Q-08** | Frontend | Redux Cart & Auth Slices Unit Test Suite | Verified | 14 / 14 tests passed in 181ms (`node --test`) |
| **Q-09** | Frontend | Monorepo Production Compilation (5 MFEs) | Verified | Zero TypeScript or Vite bundler errors (`npm run build`) |
| **Q-10** | Quality | End-to-End Defect Remediation (`BUG-01` through `BUG-05`) | Verified | Documented in `BUG_FIX_REPORT.md` with regression evidence |
| **Q-11** | Security | Zero-Trust Threat Modeling & Anti-Spoofing Audit | Verified | Documented in `SECURITY_AUDIT.md`; 10 / 10 controls pass |
| **Q-12** | Release | Production Release Package & Checksum Integrity | Verified | `ecommerce-platform-v2.0.0-prod.zip` (SHA-256 verified) |

