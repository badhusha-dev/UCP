# API Verification Report

All verification in this session is through automated tests (MockMvc/WebTestClient on H2, and gateway WebTestClient
against unused upstream ports). **No request was sent to a running stack** (Docker daemon off). Earlier live probes are
listed in the module reports (`docs/storefront-studio`, `docs/returns-refunds`).

## Gateway (verified this session)

| Behaviour | Test | Result |
|---|---|---|
| `/…/swagger`, `/…/api-docs`, `/…/actuator`, `/…/fallback` sub-paths require auth | `GatewayTenantBoundaryTest.substringOfOpenEndpointDoesNotBypassAuthentication` | see TEST_EXECUTION_REPORT |
| Login / application login / register stay anonymous | `openEndpointsStayOpen` | " |
| Tenant token → legacy orders, user directory, notifications, QA, product & order analytics, role change: 403 | `tenantTokenCannotReachPlatformWideServices` | " |
| Tenant token → order admin, pricing admin, own account, public catalog: forwarded | `tenantTokenReachesTenantAwareRoutes` | " |
| Platform token unchanged | `platformTokenUnchanged` | " |
| Spoofed identity/tenant/service headers replaced or removed | `GatewayHeaderSanitizationTest` (2) | " |
| Existing route-permission suites (delivery, pricing, storefront, order admin, returns, QA, routing) | `DeliveryGatewayPolicyTest`, `PricingRoutePermissionTest`, `QualityCenterRbacTest`, `GatewaySecurityAndRoutingTest` | " |

## Service APIs
Re-verified by re-running each module's suite (counts in TEST_EXECUTION_REPORT). Endpoint-level coverage for pricing,
storefront, order control tower, returns and delivery is described in the respective VERIFICATION_REPORTs and was not
duplicated here.

## Not verified
- Any API through the live gateway with a real login (no tenant-grant credential available).
- Postman collections (`E-Commerce-Platform.postman_collection.json`, `ecommerce_postman_collection.json`) were not run.
