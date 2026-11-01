# Tenant Inventory Slice — API Contracts

All routes are served through the API Gateway unless marked **internal**. JSON errors use an `error` message. Tenant scope comes from a signed token; caller-provided tenant headers and query parameters are not authorization inputs.

## Super Admin control plane

Every `/api/platform/**` request requires a current, active `SUPER_ADMIN` user and a signed token with the configured issuer and audience. The user service reads the role and active state from its database on each control-plane request.

An Inventory grant can use only a role with `inventory:read` and Inventory-only permissions. Global `SUPER_ADMIN`/`ADMIN` roles cannot be assigned as tenant application roles.

| Method and route | Request | Result |
| --- | --- | --- |
| `GET /api/platform/tenants` | — | `200` list of clients |
| `POST /api/platform/tenants` | `{ "displayName": "Client A", "slug": "client-a" }` | `201` created client |
| `GET /api/platform/tenants/{tenantId}/applications` | — | `200` registered applications and entitlement state |
| `PUT /api/platform/tenants/{tenantId}/applications/{applicationCode}` | `{ "enabled": true }` | `204` updated entitlement |
| `GET /api/platform/tenants/{tenantId}/memberships?applicationCode=INVENTORY` | — | `200` client members and Inventory grant state |
| `POST /api/platform/tenants/{tenantId}/memberships` | `{ "email": "employee@example.com" }` | `201` added existing identity |
| `PUT /api/platform/tenants/{tenantId}/memberships/{email}/applications/{applicationCode}` | `{ "roleKey": "WAREHOUSE_OPERATOR" }` | `204` access granted |
| `DELETE /api/platform/tenants/{tenantId}/memberships/{email}/applications/{applicationCode}` | — | `204` access revoked |
| `GET /api/platform/tenants/{tenantId}/audit?limit=50` | — | `200` access audit events |

The `INVENTORY` application and `WAREHOUSE_OPERATOR` role are enabled for the first slice. Other registered application codes remain `PLANNED` until their vertical slices are implemented.

## Application-scoped sign-in

`POST /api/users/login/application`

```json
{
  "email": "employee@example.com",
  "password": "user supplied password",
  "applicationCode": "INVENTORY",
  "tenantSlug": "client-a"
}
```

Success returns `200` with `token`, `applicationCode`, `tenantId`, `tenantSlug`, `tenantName`, and `role`. The token lasts 15 minutes and carries the identity, tenant, application, role and permissions. Invalid credentials return `401`; a missing membership, entitlement or application grant returns `403`; invalid fields return `400`.

The legacy `/api/users/login` route remains available for the existing application transition. It does not grant tenant Inventory access.

## Inventory API

Every `/api/inventory/**` route requires an `INVENTORY` application token and a current grant. Inventory validates the signature, issuer, audience and app code, then calls the user service's internal authorization check before serving the request. `inventory:read` is required for reads; `inventory:manage` is required for writes.

| Method and route | Permission | Behavior |
| --- | --- | --- |
| `GET /api/inventory/{productId}` | `inventory:read` | Read this tenant's stock for a product |
| `POST /api/inventory/batch` | `inventory:read` | Read this tenant's stock for product IDs |
| `GET /api/inventory/admin/items` | `inventory:read` | List this tenant's inventory metadata |
| `POST /api/inventory/admin/items` | `inventory:manage` | Create metadata without changing stock |
| `PUT /api/inventory/admin/items/{productId}` | `inventory:manage` | Update metadata |
| `DELETE /api/inventory/admin/items/{productId}` | `inventory:manage` | Archive zero-stock metadata |
| `PATCH /api/inventory/admin/items/{productId}/restore` | `inventory:manage` | Restore archived metadata |
| `POST /api/inventory/admin/adjust` | `inventory:manage` | Adjust stock and write a movement record |
| `GET /api/inventory/admin/analytics` | `inventory:read` | Read tenant-level stock summary |
| `GET /api/inventory/admin/movements` | `inventory:read` | Read this tenant's movement ledger |
| `GET /api/inventory/admin/reservations` | `inventory:read` | Read this tenant's active reservations |
| `POST /api/inventory/reservations` | `inventory:manage` | Reserve available stock |
| `POST /api/inventory/reservations/{reservationId}/confirm` | `inventory:manage` | Confirm a reservation |
| `POST /api/inventory/reservations/{reservationId}/release` | `inventory:manage` | Release a reservation |

For every route, the service derives `tenantId` from the authenticated principal. Supplying `tenantId` in a query, body, or `X-Tenant-ID` header cannot switch the tenant. A missing or invalid token returns `401`; missing permission or revoked access returns `403`; if the identity authorization service is unavailable, Inventory fails closed with `503`.

## Internal current-grant check

`POST /api/users/internal/authorization/check` is **service-to-service only** and is blocked at the public API Gateway. Inventory calls the user service directly with both:

- `Authorization: Bearer <application-scoped token>`
- `X-Service-Token: <shared service credential>`

The user service verifies the service credential in constant time, validates the signed token, and rechecks account activity, membership, application entitlement, grant and tenant role. It returns `204` when current access is valid, `401` for invalid service/token credentials, and `403` for revoked or disabled access.
