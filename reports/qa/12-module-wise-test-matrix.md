# QA Report 12 — Module-wise Test Matrix

**Branch:** `feature/ucp-completion-modernization`  
**Recorded:** 2026-11-01 / 2026-11-02  
**Phase:** Phase 0 — Initial Assessment  
**Status:** COMPLETE

Statuses: **PASS** = all applicable tests pass; **FAIL** = one or more tests fail;
**PARTIAL** = some tests pass, others not yet run or blocked; **NOT TESTED** = no test exists or
none was run; **IN PROGRESS** = tests executing at time of writing; **BLOCKED** = cannot run due
to a known infrastructure or configuration issue.

---

## Backend modules

### `ucp-outbox`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `OutboxRepositoryTest` | 10 | **PASS** | H2; publishes/reads outbox entries |

**Module status: PASS (10/10)**

---

### `config-server`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `ConfigServerApplicationTest` | 1 | **PASS** | Smoke; context loads |

**Module status: PASS (1/1)**

> Note: Module count 11 in the partial result (11 summary lines) likely includes one of config-server or discovery-server; exact assignment confirmed by the final run.

---

### `discovery-server`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `DiscoveryServerApplicationTest` | 1 | **PASS** | Smoke; context loads |

**Module status: PASS (1/1)**

---

### `api-gateway`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `DeliveryGatewayPolicyTest` | 8 | **PASS** | — |
| `GatewayHeaderSanitizationTest` | 2 | **PASS** | — |
| `GatewaySecurityAndRoutingTest` | 10 | **PASS** | — |
| `GatewayTenantBoundaryTest` | 6 | **PASS** | — |
| `LoginRateLimitTest` | 3 | **PASS** | — |
| `PricingRoutePermissionTest` | 7 | **PASS** | — |
| `QualityCenterRbacTest` | 5 | **PASS** | — |
| **Total** | **41** | **PASS** | — |

**Module status: PASS (41/41)**

---

### `user-service`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `UserRepositoryTest` | 2 | **PASS** | H2 unit |
| `AuthAndUserIntegrationTest` | 10 | **PASS** | H2 integration |
| `LoyaltyAndAddressIntegrationTest` | 5 | **PASS** | H2 integration |
| `TenantAccessPostgresIntegrationTest` | 2 | **PASS** | Postgres; verifies B-38 fix (widen + negative test) |

**Module status: PASS (19/19)**  
*Key test:* `TenantAccessPostgresIntegrationTest` — verifies that warehouse operators (with `goods-receipt:manage` or `purchase-order:view`) can receive INVENTORY access, and that roles outside the inventory rule are refused.

---

### `product-service`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `ProductRepositoryTest` | 2 | **PASS** | Unit |
| `DiscountEngineTest` | 13 | **PASS** | Unit |
| `ContentValidatorTest` | 6 | **PASS** | Unit |
| `StorefrontCatalogTest` | 4 | **PASS** | Unit; SF-01 catalog isolation |
| `ProductResilienceWireMockTest` | 2 | **PASS** | WireMock |
| `CustomerExperienceIntegrationTest` | 7 | **PASS** | H2 integration |
| `ProductControllerIntegrationTest` | 2 | **PASS** | H2 integration |
| `PricingApiIntegrationTest` | 19 | **PASS** | H2 integration |
| `AdvancedInventoryAndAiIntegrationTest` | 4 | **PASS** | H2 integration |
| `CatalogAdminTenantIsolationTest` | 8 | **PASS** | H2 integration |
| `InternalStorefrontControllerTest` | 2 | **PASS** | H2 integration |
| `StorefrontApiIntegrationTest` | 14 | **PASS** | H2 integration |

**Module status: PASS (83/83)**

---

### `order-service`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `OrderRepositoryTest` | 1 | **PASS** | Unit |
| `OrderStateMachineTest` | 9 | **PASS** | Unit |
| `RmaDomainTest` | 16 | **PASS** | Unit |
| `OrderProducerServiceTest` | 2 | **PASS** | Unit |
| `OrderResilienceWireMockTest` | 2 | **PASS** | WireMock |
| `OrderCheckoutIntegrationTest` | 4 | **PASS** | H2 integration |
| `OrderPricingCheckoutTest` | 3 | **PASS** | H2 integration |
| `StorefrontCheckoutTest` | 6 | **PASS** | SF-01 order path |
| `AdvancedSalesFulfillmentIntegrationTest` | 8 | **PASS** | H2 integration |
| `DeliveryStatusSyncTest` | 3 | **PASS** | H2 integration |
| `OrderAdminApiIntegrationTest` | 23 | **PASS** | H2 integration |
| `ReturnsApiIntegrationTest` | 24 | **PASS** | H2 integration |

**Module status: PASS (101/101)**

---

### `accounting-service`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `AccountingServiceTest` | 12 | **PASS** | Unit |
| `AccountingSecurityAndControllerTest` | 10 | **PASS** | H2 integration |
| `AccountingTenantIsolationPostgresTest` | 5 | **PASS** | Postgres; Flyway V19 applied |

**Module status: PASS (27/27)**

---

### `analytics-service`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `AdminAnalyticsIntegrationTest` | 8 | **PASS** | H2 integration; class uses `com.ecommerce.order` package but is in `analytics-service` Maven module |

**Module status: PASS (8/8)**

---

### `cart-service`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `CartServiceIntegrationTest` | 8 | **PASS** | H2 integration |
| `CartTransactionContractTest` | 1 | **PASS** | Reflection: verifies `getCart`/`validateCart` are `@Transactional` and not `readOnly` (B-39) |

**Module status: PASS (9/9)**

---

### `crm-service`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `StaffAccessFilterTest` | 2 | **PASS** | Unit |

**Module status: PASS (2/2)**  
*Note:* The `StaffAccessFilter` unit tests pass, but **B-36 is still open**: the filter refuses SUPER_ADMIN role. The unit test may not cover the SUPER_ADMIN case.

---

### `delivery-service`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `DeliveryApiTestSupport` | — | N/A | Support class only; no @Test methods |
| `DeliveryRulesTest` | 3 | **PASS** | Unit |
| `DeliveryJwtVerifierTest` | 1 | **PASS** | Unit |
| `DeliveryProofCodesTest` | 3 | **PASS** | Unit |
| `DeliverySecurityAndProofTest` | 14 | **PASS** | H2 integration |
| `DeliveryVerticalSliceTest` | 4 | **PASS** | H2 integration |
| `DeliveryLifecycleMatrixTest` | 3 | **PASS** | H2 integration |
| `DeliveryLifecycleTest` | 3 | **PASS** | H2 integration |
| `DeliveryTenantIsolationPostgresIntegrationTest` | 6 | **PASS** | Postgres; Flyway V1–V2 applied |

**Module status: PASS (37/37)**

---

### `error-stack-service`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `ErrorStackIntegrationTest` | 6 | **PASS** | H2 integration |

**Module status: PASS (6/6)**

---

### `inventory-service`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `DomainRulesTest` | 7 | **PASS** | Unit |
| `InventoryProducerServiceTest` | 2 | **PASS** | Unit |
| `InventoryServiceIntegrationTest` | 9 | **PASS** | Postgres; Flyway V1–V5 applied |
| `OpsIntegrationTest` | 11 | **PASS** | Postgres |

**Module status: PASS (29/29)**

---

### `loyalty-service`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `LoyaltyServiceIntegrationTest` | 9 | **PASS** | H2 integration |

**Module status: PASS (9/9)**

---

### `notification-service`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `NotificationRepositoryTest` | 2 | **PASS** | Unit |
| `OrderEventCompatibilityTest` | 2 | **PASS** | Unit |
| `NotificationConsumerIdempotencyTest` | 5 | **PASS** | H2 / Kafka-mock |
| `DeadLetterQueueIntegrationTest` | 2 | **PASS** | H2 / Kafka-mock |

**Module status: PASS (11/11)**

---

### `procurement-service`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `UcpProcurementTest` | 8 | **PASS** | Unit |

**Module status: PASS (8/8)**

---

### `qa-service`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `QaCatalogRegistryTest` | 5 | **PASS** | Unit |
| `QaRunSelectionTest` | 5 | **PASS** | Unit |
| `QaPermissionServiceTest` | 6 | **PASS** | Unit |

**Module status: PASS (16/16)**

---

### `stock-service`

| Class | Tests | Result | Notes |
|---|---|---|---|
| `StockProducerServiceTest` | 2 | **PASS** | Unit |
| `StockServiceIntegrationTest` | 9 | **PASS** | H2 integration |

**Module status: PASS (11/11)**

---

## Backend summary

| Module | Tests | Status | Postgres-gated | Source |
|---|---|---|---|---|
| ucp-outbox | 10 | **PASS** | No | QA run |
| user-service | 19 | **PASS** | Yes (2 of 19) | QA run |
| product-service | 83 | **PASS** | No | QA run |
| inventory-service | 29 | **PASS** | Yes (20 of 29) | QA run |
| stock-service | 11 | **PASS** | No | QA run |
| cart-service | 9 | **PASS** | No | QA run |
| loyalty-service | 9 | **PASS** | No | QA run |
| accounting-service | 27 | **PASS** | Yes (5 of 27) | QA run |
| crm-service | 2 | **PASS** | No | QA run |
| procurement-service | 8 | **PASS** | No | QA run |
| analytics-service | 8 | **PASS** | No | QA run |
| order-service | 101 | **PASS** | No | QA run |
| notification-service | 11 | **PASS** | No (Kafka mocked) | QA run |
| api-gateway | 41 | **PASS** | No | QA run |
| discovery-server | 1 | **PASS** | No | QA run |
| config-server | 1 | **PASS** | No | QA run |
| error-stack-service | 6 | **PASS** | No | QA run |
| qa-service | 16 | **PASS** | No | QA run |
| delivery-service | 37 | **PASS** | Yes (6 of 37) | QA run |
| **TOTAL** | **429** | **PASS** | — | BUILD SUCCESS, 17:31 min |

**All 19 modules: PASS. 429 tests, 0 failures, 0 errors, 0 skipped.**

---

## Frontend workspaces

| Workspace | TypeScript | Build | Lint | Tests | Status |
|---|---|---|---|---|---|
| `shared-library` | PASS | PASS | — | PASS (48) | **PASS** |
| `shell-app` | PASS | PASS | PASS | — | **PASS** |
| `mfe-accounting` | PASS | PASS | — | — | **PASS** |
| `mfe-analytics` | PASS | PASS | — | — | **PASS** |
| `mfe-cart` | PASS | PASS | — | — | **PASS** |
| `mfe-crm` | PASS | PASS | — | — | **PASS** |
| `mfe-delivery` | PASS | PASS | — | — | **PASS** |
| `mfe-error-stack` | PASS | PASS | — | — | **PASS** |
| `mfe-inventory` | PASS | PASS | — | PASS (12, orphan) | **PASS** |
| `mfe-loyalty` | PASS | PASS | — | — | **PASS** |
| `mfe-order` | PASS | PASS | PASS | PASS (20) | **PASS** |
| `mfe-procurement` | PASS | PASS | — | — | **PASS** |
| `mfe-product` | PASS | PASS | PASS | PASS (17) | **PASS** |
| `mfe-stock` | PASS | PASS | — | — | **PASS** |
| `mfe-super-admin` | PASS | PASS | — | — | **PASS** |
| `mfe-user` | PASS | PASS | PASS | — | **PASS** |

**All 16 frontend workspaces: PASS on TypeScript and build. 4 lint scripts: PASS. 4 test suites (97 tests): PASS.**

*Note:* PASS on TypeScript/build does not imply PASS on runtime behaviour. 12 workspaces have no
runtime tests. B-40 (cart not shared between MFEs) is a runtime defect not caught by TypeScript or
build checks.

---

## Open gaps (Phase 0 conclusions)

| Gap | Impact | Recommendation |
|---|---|---|
| 12 of 16 frontend workspaces have no tests | Low unit coverage; B-40 and similar cart/UX bugs undetected | Add Vitest tests per workspace (TD-04) |
| `mfe-inventory` orphan test file | Tests never run in CI/npm pipeline | Add `"test"` script to `mfe-inventory/package.json` |
| No Postgres test path for cart, loyalty, order, stock, notification, qa-service, error-stack, crm | SQL bugs in those services only caught by H2 (dialect differences exist) | Add Postgres IT class per service |
| B-40 blocks UI checkout path | Checkout page unreachable from browser | Resolve cart federation strategy (see OPEN_ISSUES #26) |
| SEC-01 (`flagger-alert-provider.yml`) | Possible live Slack webhook in tracked file | Owner must verify and rotate |
