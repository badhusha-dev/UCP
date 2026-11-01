# Delivery Management System: Repository Assessment

Assessment date: 2026-10-28.

## Existing capabilities

- The API Gateway validates signed UCP tokens, strips caller-supplied identity headers, applies permission gates for several applications, and routes `/api/orders/**`. There is no `/api/delivery/**` route or Delivery service route today.
- `order-service` owns order totals and order status. It stores `carrier`, `trackingNumber`, `estimatedDelivery`, and a loosely typed `shippingStatus`; its tracking endpoint accepts arbitrary status strings for admins. There is no delivery assignment, tenant scope, partner ownership, or transition graph.
- `product-service`, `inventory-service`, and `stock-service` own catalog and stock responsibilities. No restaurant or vendor service/domain was found. Delivery must reference an order by external ID and must not copy order items, product records, or payment totals.
- `notification-service` consumes `order-events` with retry/DLQ and idempotency handling. It has no delivery event subscription.
- User service V6 defines `DELIVERY_OPS` as `PLANNED` with no delivery permission codes. Tenant membership, entitlement, application grant, role assignment, and current-grant validation already exist and can be extended without creating a second identity system.
- The frontend workspace has a shell and separate MFEs, including an independent Super Admin and Inventory portal. No Delivery app, partner workflow, or Delivery navigation was found.
- Docker Compose runs a PostgreSQL database per service, and `devops/init-dbs.sql` provisions the databases on first initialization. Existing Java services use Spring Boot, Flyway, Eureka, Config Server, PostgreSQL, and Kafka.
- GitHub Actions has backend and frontend workflows. Kubernetes contains selected service manifests and a generic frontend manifest; there are no Delivery manifests or Helm charts.

## Baseline checks

Baseline builds and test commands are recorded in the implementation plan and will be executed during the Delivery change. The baseline is not evidence that absent Delivery functionality is available.

## Boundary for the implementation

Create a `delivery-service` because the lifecycle, partner assignment, location evidence, SLA/audit data, and tenant access rules form a distinct domain from orders. Create a separately deployable `mfe-delivery` application using shared UCP identity and the existing Gateway. Keep Order Service authoritative for order state and money; Delivery stores only a tenant-scoped external order reference and operational data.

The attachment ends mid-list in the partner mobile requirements. This assessment therefore treats the complete requirements present in the attachment as scope and does not invent requirements after the truncation.
