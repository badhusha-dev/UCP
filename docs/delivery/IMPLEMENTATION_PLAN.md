# Delivery Management System: Implementation Plan

## First implementation boundary

Deliver one independently deployable, tenant-scoped DMS application and service. The service owns Delivery lifecycle and assignments, while the existing Order Service remains the order and financial system of record. Delivery app login uses `DELIVERY_OPS` scoped login, current UCP membership/entitlement/grant checks, and permission-scoped roles.

## Work sequence

1. Extend shared RBAC and application registry with Delivery-specific permissions and roles; keep `SUPER_ADMIN` platform access separate from tenant Delivery access.
2. Add a PostgreSQL/Flyway-backed `delivery-service` with tenant-scoped deliveries, partner profiles, zones, assignments/history, lifecycle audit, incidents, proof-of-delivery metadata, latest location, policy settings, and a transactional event outbox.
3. Enforce signed `DELIVERY_OPS` app tokens and current entitlement/grant on every protected service request. Derive tenant and actor from token claims, not request headers. Enforce permissions, location zones, and partner resource ownership in the service.
4. Implement transition guards, optimistic version checks, idempotency keys, required failure/reassignment reasons, deterministic assignment scoring, capacity and zone eligibility, partner accept/reject/availability, location retention cleanup, and auditable actions.
5. Add a standalone `mfe-delivery` with scoped login, operations console, dispatch/partner/configuration workflows, and a mobile-first partner view. Never rely on hidden UI controls for authorization.
6. Wire Gateway, Compose, initial database provisioning, Config Server, CI, and Kubernetes manifests. Keep deployment hosts configurable; do not add a production domain.
7. Add focused tests for transition and tenant/role boundaries, build affected services and app, validate Compose, deploy locally, and document what is and is not integrated.

## External integration limits

- Existing orders contain no delivery address, pickup location, business type, tenant, or readiness event contract. The initial DMS accepts an authorized external order reference and operational pickup/drop-off details; it does not invent or modify order data.
- Existing notification handling is tied to legacy `order-events`. Delivery emits versioned outbox events to its own Kafka topic. Notification delivery can consume these events after the recipient identity/tenant mapping is explicit; no e-mail, SMS, navigation, geocoding, or mapping provider is configured in this repository.
- Earnings are calculated from recorded configured rate inputs for operational review only. No payout or accounting integration is implied.
- Partner identity can only be granted through UCP's existing tenant membership and Delivery entitlement flow. Document status is administrative metadata, not proof of legal verification.

## Validation commands

Baseline: `docker compose config --quiet`; backend `./mvnw -B -pl order-service,api-gateway,user-service -am test`; frontend `npm run build --workspace=@ecommerce/shared-library`.

Delivery implementation: backend focused tests and package build; `npm run build --workspace=mfe-delivery`; `docker compose config --quiet`; local portal/API smoke checks. Record unavailable checks and dependencies in [DELIVERY_STATUS.md](DELIVERY_STATUS.md).
