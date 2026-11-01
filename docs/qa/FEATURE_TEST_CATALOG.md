# Feature and Test Catalog

## Discovery method

Inventory sources inspected: `backend/pom.xml`, each backend service's `src/main/java` controllers and Flyway migrations, `backend/config-repo/api-gateway.yml`, `backend/user-service/.../V4__hierarchical_rbac.sql`, shell routes in `frontend/shell-app/src/App.tsx`, frontend workspace packages, test source files, and the current Node/Maven test scripts.

Counts below are repository test annotations registered at implementation time (including 16 QA-service unit tests and five new gateway QA authorization tests). The catalog maps **158 registered test cases** across 16 features: 144 backend and 14 shared frontend. Test results and statuses shown in the app come from execution records, not these registration counts. A class can contain multiple behavior paths; counts are not code coverage.

## Feature inventory

| Feature | Frontend | Backend | API route families | Database / dependency | Permission policy | Registered tests | Status |
|---|---|---|---|---|---|---:|---|
| Authentication & users | `mfe-user`, shell login | `user-service` | `/api/users/**`, `/api/access/**` | `user_db` | `users:read`, `users:manage`, `roles:manage`; own-account checks in gateway | 11 | Partial |
| Products & catalog | `mfe-product` | `product-service` | `/api/products/**` | `product_db`, `inventory_db` | `catalog:manage` on admin product APIs | 20 | Partial |
| Cart & basket | `mfe-cart` | `cart-service` | `/api/cart/**` | `cart_db`, `product_db` | `orders:manage` on admin cart APIs | 17 | Partial |
| Checkout, orders & fulfillment | `mfe-order` | `order-service` | `/api/orders/**`, `/api/orders/admin/analytics/**` | `order_db`, `cart_db`, `inventory_db` | `orders:manage` on admin order APIs; customer ownership needs wider testing | 13 | Partial |
| Inventory & reservations | `mfe-inventory` | `inventory-service` | `/api/inventory/**` | `inventory_db`, `order_db` | `inventory:read`, `inventory:manage` | 7 | Partial |
| Stock management | `mfe-stock` | `stock-service` | `/api/stock/**` | `stock_db`, `inventory_db` | `stock:read`, `stock:manage` | 7 | Partial |
| Loyalty & rewards | `mfe-loyalty`, user profile | `loyalty-service`, user workflows | `/api/loyalty/**`, `/api/users/{id}/loyalty/**` | `loyalty_db`, `user_db` | `loyalty:manage` for staff operations | 13 | Partial |
| Accounting & governance | `mfe-accounting` | `accounting-service` | `/api/accounting/**` | `accounting_db`, `order_db` | read/write/approve/reconcile/import/export permissions | 18 | Partial |
| CRM & support | `mfe-crm` | `crm-service` | `/api/crm/**` | `crm_db`, `user_db`, `order_db` | `crm:read`, `crm:manage`; staff filter exists | 2 | Partial |
| Procurement & suppliers | `mfe-procurement` | `procurement-service` | `/api/procurement/**` | `procurement_db`, `inventory_db` | `procurement:read`, `procurement:manage` | 0 | Not covered |
| Analytics & reporting | `mfe-analytics` | `analytics-service` | `/api/analytics/**`, `/api/orders/admin/analytics/**` | `analytics_db`, `order_db` | `analytics:read` | 8 | Partial |
| Notifications | shell `NotificationBell` | `notification-service` | `/api/notifications/**` | `notification_db`, Kafka | No feature permission in current V4 matrix | 6 | Partial |
| Error diagnostics | `mfe-error-stack` | `error-stack-service` | `/api/errors/**` | `error_stack_db`, Kafka | `errors:manage` on admin error APIs | 6 | Partial |
| RBAC & gateway security | shell navigation/guards | `api-gateway`, user RBAC policy | `/api/admin/**`, `/api/access/**` | `user_db` | 26 known permissions, gateway route policies | 18 | Partial |
| Discovery & configuration | shell service navigation | `discovery-server`, `config-server` | `/actuator/health`, aggregated OpenAPI | registry/config repo | Service-level access; route-level coverage unresolved | 2 | Partial |
| Quality Center | `/admin/quality-center` in shell | `qa-service` | `/api/admin/qa/**` | `qa_db`, isolated local workspace | `qa:read`, `qa:run`, `qa:cancel` | 10 | Partial |

The 12 MFEs are separate frontend packages; the existing `shared-library` owns Redux auth/cart slices and the only frontend test script. There is no separate payment, delivery-partner, support, or notification MFE/service package in the workspace: payment/fulfillment behavior is part of orders, support behavior is part of CRM, and notification display is part of the shell. Their boundaries are therefore not reported as independent services.

## Registered test source inventory

| Module | Test classes | Cases |
|---|---|---:|
| `accounting-service` | `AccountingSecurityAndControllerTest`, `AccountingServiceTest` | 18 |
| `analytics-service` | `AdminAnalyticsIntegrationTest` | 8 |
| `api-gateway` | `GatewaySecurityAndRoutingTest`, `QualityCenterRbacTest` | 12 |
| `cart-service` | `CartServiceIntegrationTest` | 7 |
| `config-server` | `ConfigServerApplicationTest` | 1 |
| `crm-service` | `StaffAccessFilterTest` | 2 |
| `discovery-server` | `DiscoveryServerApplicationTest` | 1 |
| `error-stack-service` | `ErrorStackIntegrationTest` | 6 |
| `inventory-service` | `InventoryServiceIntegrationTest` | 7 |
| `loyalty-service` | `LoyaltyServiceIntegrationTest` | 8 |
| `notification-service` | `DeadLetterQueueIntegrationTest`, `NotificationConsumerIdempotencyTest`, `NotificationRepositoryTest` | 6 |
| `order-service` | `AdvancedSalesFulfillmentIntegrationTest`, `OrderCheckoutIntegrationTest`, `OrderRepositoryTest`, `OrderResilienceWireMockTest` | 13 |
| `product-service` | `AdvancedInventoryAndAiIntegrationTest`, `CustomerExperienceIntegrationTest`, `ProductControllerIntegrationTest`, `ProductRepositoryTest`, `ProductResilienceWireMockTest` | 20 |
| `qa-service` | `QaCatalogRegistryTest`, `QaRunSelectionTest`, `QaPermissionServiceTest` | 16 |
| `stock-service` | `StockServiceIntegrationTest` | 7 |
| `user-service` | `AuthAndUserIntegrationTest`, `LoyaltyAndAddressIntegrationTest`, `UserRepositoryTest` | 12 |
| `frontend/shared-library` | `authSlice.test.ts`, `cartSlice.test.ts` | 14 |

The count is based on `@Test` and `@ParameterizedTest` annotations in the current source inventory. It is not a claim that every test runs in every environment or that every listed endpoint has a specific test assertion.

## Routes and coverage caveats

- The shell's protected business routes are defined in `frontend/shell-app/src/App.tsx`; module federation remotes are configured in the shell Vite config.
- Gateway route families are defined in `backend/config-repo/api-gateway.yml`; method/permission rules are handwritten in `JwtAuthenticationFilter.routePermission`.
- Non-admin paths that do not match a gateway policy rely on the downstream service. The repository does not yet have an automatically verified endpoint-to-policy map. These cases are flagged unresolved in the RBAC matrix.
- The QA catalog reports feature status as `PARTIAL` when tests are registered and `NOT_COVERED` when none are registered. No feature is marked fully covered from a test count alone.
