# Delivery Management System — Status

**Checked:** 2026-10-31. Only results from commands run on that date are listed as verified.

## What exists

| Area | Location | State |
| --- | --- | --- |
| Permissions, roles, `DELIVERY_OPS` app registration | `user-service` Flyway `V7__delivery_application_permissions.sql` | Present. Roles: `DELIVERY_ADMIN`, `DISPATCHER`, `DELIVERY_PARTNER`, `DELIVERY_ANALYST`. |
| Service | `backend/delivery-service` (port 8091 inside its container) | Tenant-scoped JDBC service: deliveries, readiness, lifecycle transitions, dispatch and assignment offers, partner profiles and review, zones, pricing, location, proof, incidents, trips, earnings, reports, configuration, audit, transactional outbox. |
| Schema | `delivery-service` Flyway `V1__delivery_management_schema.sql` | Every table carries `tenant_id`; order references are unique per tenant. |
| Service security | `DeliveryTenantAuthenticationFilter` | Requires a signed `DELIVERY_OPS` token and rechecks the current grant with `user-service`; tenant and actor come from token claims only. |
| Gateway policy | `JwtAuthenticationFilter.deliveryRoutePermissions` | Requires a `DELIVERY_OPS` token and a per-route permission; unmapped delivery routes are denied. |
| Frontend | `frontend/mfe-delivery` (Compose host port 3014) | Standalone app with scoped login, operations console and partner view. |
| Wiring | `docker-compose.yml`, `devops/init-dbs.sql` (`delivery_db`), `backend/config-repo/delivery-service*.yml`, CI matrices | Present. |

## Verified on 2026-10-31

- `delivery-service`: 10/10 tests pass. That includes `DeliveryTenantIsolationPostgresIntegrationTest` (6 tests) against PostgreSQL 15 with the real Flyway schema. They prove cross-tenant reads, lists and transitions return 404, that order references are unique per tenant, idempotent replay, and that create requires `delivery:manage`.
- Without `UCP_TEST_DELIVERY_POSTGRES_URL`, the PostgreSQL test is skipped. It never reports a pass without a database.
- `api-gateway`: 21/21 tests pass, including `DeliveryGatewayPolicyTest` (6 tests). They cover wrong-application and legacy-token rejection, partner versus dispatcher and admin route separation, analyst read-only access and default deny.
- `mfe-delivery` production build passes; `docker compose config --quiet` passes.

## Fixed during this check

The unfiltered delivery list and the unfiltered incident list failed on PostgreSQL with `could not determine data type of parameter $2`, because of an untyped `? IS NULL`. Both queries now cast the parameter. This affected the default delivery list in the app.

## Not verified / known gaps

- No runtime stack check: the service, gateway and app were not started together, and no browser login was exercised.
- The dispatch, assignment-response, partner, proof, trip, pricing and earnings paths have no database-backed tests yet.
- Delivery events go to the service's own Kafka topic. No notification consumer, and no e-mail, SMS, maps or geocoding provider, is wired.
- Earnings are operational figures only; there is no payout or accounting integration.
- Locally, `delivery-service` and `error-stack-service` both default to port 8091. They do not clash in Compose, where each runs in its own container, but they cannot both run on one host with `spring-boot:run`.

## Running the tests

```powershell
cd D:\practical\ecommerce-platform\backend
# Point at an isolated, disposable PostgreSQL database
$env:UCP_TEST_DELIVERY_POSTGRES_URL = "jdbc:postgresql://127.0.0.1:55432/ucp_delivery_test"
$env:UCP_TEST_DELIVERY_POSTGRES_USERNAME = "postgres"
$env:UCP_TEST_DELIVERY_POSTGRES_PASSWORD = "<password>"
.\mvnw.cmd -B -pl delivery-service test
.\mvnw.cmd -B -pl api-gateway test
```
