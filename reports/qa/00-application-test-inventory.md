# QA Report 00 — Application Test Inventory

**Branch:** `feature/ucp-completion-modernization`  
**Recorded:** 2026-11-01  
**Phase:** Phase 0 — Initial Assessment  
**Status:** COMPLETE (inventory; test execution in progress for backend)

---

## 1. Backend — Maven modules (19 modules total)

All modules are under `backend/`. The build tool is Maven 3.6.3 (`mvnw.cmd`) targeting Java 17.
Spring Boot version: 3.2.4. Test database strategy: H2 by default; Postgres integration tests
guarded by environment variables (`UCP_TEST_*_POSTGRES_URL`).

| Module | Test classes | Unit | Integration | Notes |
|---|---|---|---|---|
| `ucp-outbox` | 1 | 1 | 0 | `OutboxRepositoryTest` — H2, in-memory only |
| `config-server` | 1 | 1 | 0 | `ConfigServerApplicationTest` — smoke |
| `discovery-server` | 1 | 1 | 0 | `DiscoveryServerApplicationTest` — smoke |
| `api-gateway` | 7 | 7 | 0 | Security, routing, RBAC, rate-limit, header-sanitisation, tenant boundary |
| `user-service` | 4 | 1 | 3 | Postgres integration guarded by `UCP_TEST_POSTGRES_URL`; 3 IT classes |
| `product-service` | 12 | 7 | 5 | Includes StorefrontCatalogTest (4 tests, SF-01) |
| `order-service` | 12 | 4 | 8 | Widest single-module suite; includes RMA, state-machine, storefront checkout |
| `accounting-service` | 3 | 2 | 1 | Postgres IT guarded by `UCP_TEST_ACCOUNTING_POSTGRES_URL` |
| `analytics-service` | 1 | 0 | 1 | H2 integration; no Postgres guard |
| `cart-service` | 2 | 1 | 1 | `CartTransactionContractTest` verifies `@Transactional` contract (B-39) |
| `crm-service` | 1 | 1 | 0 | `StaffAccessFilterTest` — unit |
| `delivery-service` | 9 | 8 | 1 | Postgres IT guarded by `UCP_TEST_DELIVERY_POSTGRES_URL` |
| `error-stack-service` | 1 | 0 | 1 | H2 integration |
| `inventory-service` | 4 | 2 | 2 | Postgres IT guarded by `UCP_TEST_INVENTORY_POSTGRES_URL` |
| `loyalty-service` | 1 | 0 | 1 | H2 integration |
| `notification-service` | 4 | 3 | 1 | Includes DLQ and idempotency; Kafka mocked |
| `procurement-service` | 1 | 1 | 0 | Unit only |
| `qa-service` | 3 | 3 | 0 | Unit only; domain logic |
| `stock-service` | 2 | 1 | 1 | H2 integration |

**Total test classes: 70  |  Approximate unit methods: ~180  |  Approximate integration methods: ~250**
(Exact counts come from surefire output; see Report 01.)

### Backend test class index

| Class | Module | Kind | Notes |
|---|---|---|---|
| `OutboxRepositoryTest` | ucp-outbox | Unit/H2 | — |
| `ConfigServerApplicationTest` | config-server | Smoke | — |
| `DiscoveryServerApplicationTest` | discovery-server | Smoke | — |
| `DeliveryGatewayPolicyTest` | api-gateway | Unit | — |
| `GatewayHeaderSanitizationTest` | api-gateway | Unit | — |
| `GatewaySecurityAndRoutingTest` | api-gateway | Unit | — |
| `GatewayTenantBoundaryTest` | api-gateway | Unit | — |
| `LoginRateLimitTest` | api-gateway | Unit | — |
| `PricingRoutePermissionTest` | api-gateway | Unit | — |
| `QualityCenterRbacTest` | api-gateway | Unit | — |
| `AuthAndUserIntegrationTest` | user-service | Integration/H2 | — |
| `LoyaltyAndAddressIntegrationTest` | user-service | Integration/H2 | — |
| `TenantAccessPostgresIntegrationTest` | user-service | Integration/Postgres | Guarded; B-38 fix verified here |
| `UserRepositoryTest` | user-service | Unit | — |
| `CatalogAdminTenantIsolationTest` | product-service | Integration/H2 | — |
| `CustomerExperienceIntegrationTest` | product-service | Integration/H2 | — |
| `ProductControllerIntegrationTest` | product-service | Integration/H2 | — |
| `ProductRepositoryTest` | product-service | Unit | — |
| `ProductResilienceWireMockTest` | product-service | Integration/WireMock | — |
| `AdvancedInventoryAndAiIntegrationTest` | product-service | Integration/H2 | — |
| `DiscountEngineTest` | product-service | Unit | — |
| `PricingApiIntegrationTest` | product-service | Integration/H2 | — |
| `ContentValidatorTest` | product-service | Unit | — |
| `InternalStorefrontControllerTest` | product-service | Integration/H2 | — |
| `StorefrontApiIntegrationTest` | product-service | Integration/H2 | — |
| `StorefrontCatalogTest` | product-service | Unit | SF-01: 4 tests |
| `AdvancedSalesFulfillmentIntegrationTest` | order-service | Integration/H2 | — |
| `DeliveryStatusSyncTest` | order-service | Integration/H2 | — |
| `OrderCheckoutIntegrationTest` | order-service | Integration/H2 | — |
| `OrderPricingCheckoutTest` | order-service | Integration/H2 | — |
| `OrderRepositoryTest` | order-service | Unit | — |
| `OrderResilienceWireMockTest` | order-service | Integration/WireMock | — |
| `StorefrontCheckoutTest` | order-service | Integration/H2 | SF-01 order path |
| `OrderAdminApiIntegrationTest` | order-service | Integration/H2 | — |
| `OrderStateMachineTest` | order-service | Unit | — |
| `ReturnsApiIntegrationTest` | order-service | Integration/H2 | — |
| `RmaDomainTest` | order-service | Unit | — |
| `OrderProducerServiceTest` | order-service | Unit | — |
| `AccountingSecurityAndControllerTest` | accounting-service | Integration/H2 | — |
| `AccountingServiceTest` | accounting-service | Unit | — |
| `AccountingTenantIsolationPostgresTest` | accounting-service | Integration/Postgres | Guarded |
| `AdminAnalyticsIntegrationTest` | analytics-service | Integration/H2 | — |
| `CartServiceIntegrationTest` | cart-service | Integration/H2 | 8 tests |
| `CartTransactionContractTest` | cart-service | Unit | B-39 contract check; 1 test |
| `StaffAccessFilterTest` | crm-service | Unit | — |
| `DeliveryApiTestSupport` | delivery-service | Support class | Not a test class; no @Test |
| `DeliverySecurityAndProofTest` | delivery-service | Integration/H2 | — |
| `DeliveryVerticalSliceTest` | delivery-service | Integration/H2 | — |
| `DeliveryRulesTest` | delivery-service | Unit | — |
| `DeliveryJwtVerifierTest` | delivery-service | Unit | — |
| `DeliveryLifecycleMatrixTest` | delivery-service | Integration/H2 | — |
| `DeliveryLifecycleTest` | delivery-service | Integration/H2 | — |
| `DeliveryProofCodesTest` | delivery-service | Unit | — |
| `DeliveryTenantIsolationPostgresIntegrationTest` | delivery-service | Integration/Postgres | Guarded |
| `ErrorStackIntegrationTest` | error-stack-service | Integration/H2 | — |
| `InventoryServiceIntegrationTest` | inventory-service | Integration/Postgres | Guarded |
| `DomainRulesTest` | inventory-service | Unit | — |
| `OpsIntegrationTest` | inventory-service | Integration/Postgres | Guarded |
| `InventoryProducerServiceTest` | inventory-service | Unit | — |
| `LoyaltyServiceIntegrationTest` | loyalty-service | Integration/H2 | — |
| `DeadLetterQueueIntegrationTest` | notification-service | Integration/Kafka-mock | — |
| `NotificationConsumerIdempotencyTest` | notification-service | Integration/H2 | — |
| `NotificationRepositoryTest` | notification-service | Unit | — |
| `OrderEventCompatibilityTest` | notification-service | Unit | — |
| `UcpProcurementTest` | procurement-service | Unit | — |
| `QaCatalogRegistryTest` | qa-service | Unit | — |
| `QaRunSelectionTest` | qa-service | Unit | — |
| `QaPermissionServiceTest` | qa-service | Unit | — |
| `StockServiceIntegrationTest` | stock-service | Integration/H2 | — |
| `StockProducerServiceTest` | stock-service | Unit | — |

---

## 2. Frontend — npm workspaces (16 workspaces)

All workspaces are under `frontend/`. Toolchain: Node 24.5 (local), npm workspaces, Vite, React 18,
TypeScript, `@originjs/vite-plugin-federation`.

| Workspace | TypeScript check | Build script | Lint script | Test script | Test files | Notes |
|---|---|---|---|---|---|---|
| `shared-library` | tsconfig.app.json | vite build | — | `node --experimental-strip-types --test` | 0 (`.ts` in `tests/`) | 48 test methods across `tests/*.test.ts` |
| `shell-app` | tsconfig.app.json | vite build | eslint | — | 0 | No test script; lint configured |
| `mfe-accounting` | tsconfig.app.json | vite build | — | — | 0 | No tests |
| `mfe-analytics` | tsconfig.app.json | vite build | — | — | 0 | No tests |
| `mfe-cart` | tsconfig.app.json | vite build | — | — | 0 | No tests |
| `mfe-crm` | tsconfig.app.json | vite build | — | — | 0 | No tests |
| `mfe-delivery` | tsconfig.app.json | vite build | — | — | 0 | No tests |
| `mfe-error-stack` | tsconfig.app.json | vite build | — | — | 0 | No tests |
| `mfe-inventory` | tsconfig.app.json | vite build | — | — | 1 | `ops/model.test.ts` is an orphan: no test script; run manually with `node --experimental-strip-types --test` |
| `mfe-loyalty` | tsconfig.app.json | vite build | — | — | 0 | No tests |
| `mfe-order` | tsconfig.app.json | vite build | eslint | `node --experimental-strip-types --test` | 0 (`.ts` in `tests/`) | 20 test methods |
| `mfe-procurement` | tsconfig.app.json | vite build | — | — | 0 | No tests |
| `mfe-product` | tsconfig.app.json | vite build | eslint | `node --experimental-strip-types --test` | 0 (`.ts` in `tests/`) | 17 test methods |
| `mfe-stock` | tsconfig.app.json | vite build | — | — | 0 | No tests |
| `mfe-super-admin` | tsconfig.app.json | vite build | — | — | 0 | No tests |
| `mfe-user` | tsconfig.app.json | vite build | eslint | — | 0 | Lint configured; no test script |

**Test coverage: 4 workspaces have runnable tests (shared-library, mfe-order, mfe-product, mfe-inventory orphan).
12 of 16 workspaces have no tests at all (TD-04 in OPEN_ISSUES).**

---

## 3. Infrastructure checks

| Check | Tool | Scope |
|---|---|---|
| Docker Compose validity | `docker compose config` | `docker-compose.yml` (46 services) |
| Kubernetes manifests | `kubectl kustomize kubernetes/base kubernetes/overlays/production` | 34 Deployments, 36 Services, 18 NetworkPolicies |
| YAML parse | yamllint (PowerShell) | 193 YAML files |
| JSON parse | PowerShell ConvertFrom-Json | 36 JSON files |
| Secret scan (patterns) | PowerShell regex | All tracked files |
| Frontend TSC | `npx tsc --noEmit` | 16 workspaces |
| Frontend build | `npm run build` | 16 workspaces |
| Frontend lint | `npm run lint` | 4 workspaces with lint script |
| Frontend test | npm test / node --test | 3 workspaces + 1 orphan |
| Backend test (H2) | `mvnw -B -fae test` | 19 modules |
| Backend test (Postgres) | same + `UCP_TEST_*_POSTGRES_URL` env vars | 4 guarded modules |
| Runtime health | `docker exec <svc> wget /actuator/health` | 9 running services |
| Frontend HTTP | `Invoke-WebRequest localhost:<port>` | 6 running MFEs |

---

## 4. What is NOT covered in Phase 0

- End-to-end browser test (Playwright/Selenium)
- Load testing
- DAST / penetration testing
- Security scanning tools (SAST, dependency audit)
- Kubernetes apply against a real cluster
- Kafka broker consumer tests (Kafka mocked in all test classes)
- Notification delivery verification
- Payment integration
