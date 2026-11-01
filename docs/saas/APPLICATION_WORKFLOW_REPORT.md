# Application Workflow Report

Snapshot: 2026-11-02. This report describes the implemented workflow for registering applications, creating client tenants, granting application access, signing in to a tenant-scoped application, and revoking access.

## Workflow at a glance

```text
Create user identity
        |
        v
Create client tenant
        |
        v
Enable application entitlement for the client
        |
        v
Add existing user as an active client member
        |
        v
Grant application + tenant role to that member
        |
        v
Application login(email, password, applicationCode, tenantSlug)
        |
        v
Short-lived application token
        |
        v
Gateway and service validate tenant, application, grant, role, and permission
        |
        v
Revoke grant when access should end
```

The browser does not create authorization claims. The user-service checks membership, entitlement, grant, role assignment, active account state, and active application state before issuing the scoped token.

## Implemented workflow steps

| Step | Actor | Endpoint or component | Result | Status |
|---|---|---|---|---|
| 1. Create identity | User or onboarding flow | `POST /api/users/register` | Creates an active user with the default `USER` role | Implemented |
| 2. Platform login | Super Admin | `POST /api/users/login` | Issues an unscoped platform token | Implemented |
| 3. Create client | Super Admin | `POST /api/platform/tenants` | Creates an active tenant and writes `TENANT_CREATE` audit data | Implemented |
| 4. List clients | Super Admin | `GET /api/platform/tenants` | Lists tenants and lifecycle status | Implemented |
| 5. Inspect applications | Super Admin | `GET /api/platform/tenants/{tenantId}/applications` | Returns registry metadata and the tenant's entitlement state | Implemented |
| 6. Enable application | Super Admin | `PUT /api/platform/tenants/{tenantId}/applications/{applicationCode}` | Creates or updates an entitlement and audits enable/disable | Implemented |
| 7. Add member | Super Admin | `POST /api/platform/tenants/{tenantId}/memberships` | Adds an existing active identity to the tenant | Implemented |
| 8. Inspect members | Super Admin | `GET /api/platform/tenants/{tenantId}/memberships?applicationCode=...` | Shows membership, grant, and assigned role state | Implemented |
| 9. Grant application role | Super Admin | `PUT /api/platform/tenants/{tenantId}/memberships/{email}/applications/{applicationCode}` | Creates/updates grant and tenant role assignment | Implemented |
| 10. Application login | Employee/member | `POST /api/users/login/application` | Validates all access prerequisites and issues a scoped token | Implemented |
| 11. Use application | Employee/member | Gateway plus downstream service authorization | Enforces application code, tenant, role, and permissions | Implemented, coverage varies by service |
| 12. Revoke application | Super Admin | `DELETE /api/platform/tenants/{tenantId}/memberships/{email}/applications/{applicationCode}` | Marks grant inactive, records revoke time, and audits | Implemented |
| 13. Review access history | Super Admin | `GET /api/platform/tenants/{tenantId}/audit` | Returns bounded tenant access audit history | Implemented |

## Application login requirements

`POST /api/users/login/application` requires:

```json
{
  "email": "employee@example.com",
  "password": "the-password",
  "applicationCode": "INVENTORY",
  "tenantSlug": "client-slug"
}
```

The login succeeds only when all of these are true:

1. The user credentials are valid and the account is active.
2. The tenant exists and is active.
3. The user has an active membership in that tenant.
4. The application exists and is active in `application_registry`.
5. The tenant has an enabled entitlement for that application.
6. The user has an active application grant.
7. The user has a tenant role assignment for that application.

The response contains a short-lived token with the application code, tenant ID, tenant slug, tenant name, tenant role, and permissions. The shared frontend session helper rejects a token whose application or tenant claims do not match the requested session.

## Application registry state

The local PostgreSQL registry currently contains ten application records:

| Application | Database status | Required permission | Current workflow surface |
|---|---|---|---|
| `SUPER_ADMIN` | ACTIVE | `application:admin` | Platform tenant/application administration |
| `ECOMMERCE_ADMIN` | ACTIVE | `catalog:manage` | Product/order administration; tenant-scoped APIs |
| `STOREFRONT` | PLANNED | None | Storefront and checkout components exist, registry access is not active |
| `INVENTORY` | ACTIVE | `inventory:read` | Inventory and stock application |
| `ACCOUNTING` | ACTIVE | `accounting:read` | Accounting application and tenant-scoped API |
| `DELIVERY_OPS` | ACTIVE | `delivery:read` | Independent delivery application |
| `VENDOR_PORTAL` | PLANNED | None | No complete vendor/restaurant workflow |
| `CRM` | PLANNED | `crm:read` | CRM service exists, registry workflow is not active |
| `QUALITY_CENTER` | ACTIVE | `qa:read` | Quality Center / QA workflow |
| `CUSTOMER_SUPPORT` | PLANNED | None | No complete support application |

## Frontend workflow

The Super Admin frontend currently supports:

1. Platform sign-in.
2. Client selection and client creation.
3. Application selection for Inventory and Delivery Operations.
4. Entitlement enable/disable.
5. Adding an existing identity to a client.
6. Selecting a role for the next application grant.
7. Granting and revoking application access.
8. Refreshing client/member state and signing out.

The shared application-session helper supports application-scoped sign-in, session storage per application, tenant/application claim validation, safe redirect resolution, and expiry cleanup.

## Authorization boundaries

| Boundary | Enforced by | Behavior |
|---|---|---|
| Platform administration | `PlatformTenantController` | Requires a valid platform token and a current active `SUPER_ADMIN` database user |
| Application availability | `TenantAccessService.requireActiveApplication` | Planned or suspended applications cannot receive access or issue application tokens |
| Tenant membership | `tenant_memberships` | User must be an active member before a grant can be issued |
| Entitlement | `tenant_application_entitlements` | Application must be enabled for the client before a member can receive access |
| User grant | `user_application_grants` | Grant must be active and not revoked |
| Tenant role | `tenant_role_assignments` | Role is selected per tenant and application, not globally for application access |
| Permission set | `access_role_permissions` / `RbacService` | Token permissions come from the assigned role; Super Admin uses service-level wildcard access |
| Delivery role safety | `TenantAccessService` | Delivery grants must contain Delivery permissions, except the recipient-only role |
| Inventory role safety | `TenantAccessService` | Inventory grants must contain Inventory/warehouse permissions plus the approved receiving and PO-view permissions |
| Current grant validity | Internal authorization endpoint | Services can re-check that the current grant and role still match the token |
| Audit | `tenant_access_audit_log` | Tenant creation, entitlement changes, membership creation, grants, and revocations are recorded |

## Current local verification state

The local database currently contains:

| Record type | Count | Meaning |
|---|---:|---|
| Tenants | 1 | Legacy tenant |
| Tenant memberships | 34 | Seeded RBAC test users are members of the legacy tenant |
| Application entitlements | 0 | No tenant application has been enabled in the current local database |
| Application grants | 0 | No user currently has an application grant |
| Tenant role assignments | 0 | No tenant application role has been assigned |

The RBAC user seed verifies platform login, but application login requires a separate tenant/application fixture or manual Super Admin workflow because the seed intentionally does not grant every application to every role.

## Completed

- Application registry tables and seed metadata exist.
- Platform Super Admin authentication and authorization exist.
- Tenant creation and tenant listing exist.
- Application entitlement enable/disable exists.
- Membership creation exists for existing active identities.
- Application grant and revoke flows exist with audit records.
- Application-scoped login and JWT claims exist.
- Frontend session isolation by application and tenant exists.
- Delivery and Inventory grant-role validation exists.
- RBAC test users and role access documentation exist.

## Incomplete or not verified

- The frontend registry is out of sync with the database: `ECOMMERCE_ADMIN` and `ACCOUNTING` are ACTIVE in PostgreSQL but still marked PLANNED in `frontend/shared-library/src/config/applicationRegistry.ts`.
- The Super Admin UI currently exposes only Inventory and Delivery Operations in its application selector; it does not expose every ACTIVE database application.
- No automatic seed currently creates a complete tenant entitlement/grant matrix for all applications and roles.
- Full end-to-end verification of every application login and every grant/revoke path is not complete.
- Tenant lifecycle operations such as suspend, archive, delete, plan limits, and billing are not complete.
- Application registry versioning, feature entitlements, dependencies, and approval workflows are not modelled.
- Application origin configuration and production HTTPS origin verification still require deployment-specific configuration.
- Service-level tenant isolation is not complete across every backend service.
- Frontend route guards remain partly legacy-role based; backend authorization remains the source of truth.
- Planned applications (`STOREFRONT`, `VENDOR_PORTAL`, `CRM`, and `CUSTOMER_SUPPORT`) need complete registry activation criteria and workflow implementation before being marked ACTIVE.

## Recommended next verification

1. Enable one ACTIVE application for a test tenant.
2. Add two seeded users as tenant members.
3. Grant one valid role and one invalid role; verify the invalid role is rejected.
4. Sign in through `/api/users/login/application` and inspect the scoped claims.
5. Call an allowed endpoint and a denied endpoint.
6. Revoke the grant and verify the same token is rejected by the current-access check.
7. Confirm the audit rows for entitlement, membership, grant, and revoke actions.

## Workflow by role

Every role follows the same access sequence: create or select the identity, add the user to a tenant, enable the target application, grant the role for that application, sign in with `POST /api/users/login/application`, use only the listed operations, then revoke the grant when access ends. The application role is tenant-scoped; the global `users.role` value is not a substitute for an application grant.

| Role | Recommended application/workspace | Workflow and primary operations | Expected boundary |
|---|---|---|---|
| `SUPER_ADMIN` | Super Admin Portal | Platform login; create clients; enable applications; add members; grant/revoke roles; inspect audit; manage platform access | Platform-only workflow; protected role; should not be used as a tenant application role |
| `ADMIN` | E-commerce Admin / platform operations | Platform login or tenant grant; manage catalog, orders, pricing, returns, inventory, procurement, users, QA, storefront, accounting, CRM, analytics, and diagnostics according to permissions | Broad global role; tenant application access still requires entitlement and grant |
| `MANAGER` | E-commerce Admin | Sign in to enabled client; manage users/roles below hierarchy; operate accounting, CRM, inventory, stock, orders, procurement, loyalty, analytics, and errors | Cannot manage equal/higher roles; cannot grant permissions it does not hold |
| `ACCOUNTANT` | Accounting | Application login; view/write accounting records; reconcile; import/export | No approval permission; no non-accounting application access unless separately granted |
| `AUDITOR` | Accounting | Application login; read accounting records; export records; view analytics | Read/export boundary; no accounting writes or approvals |
| `DELIVERY_ADMIN` | Delivery Operations | Application login; manage deliveries; dispatch; manage partners; tracking; proof; incidents; configuration; reports; audit | Delivery-only role; must pass Delivery permission validation |
| `DISPATCHER` | Delivery Operations | Application login; view/manage deliveries; dispatch/reassign partners; tracking; incidents; reports | Cannot manage partner eligibility or delivery configuration |
| `DELIVERY_PARTNER` | Delivery Operations | Application login; manage own availability/work; view assigned delivery; submit tracking; capture proof; report incidents | Own/assigned operational scope; cannot dispatch or manage other partners |
| `DELIVERY_ANALYST` | Delivery Operations | Application login; view delivery operations; view tracking, reports, and audit history | Read/report/audit scope; cannot update deliveries or dispatch |
| `DELIVERY_RECIPIENT` | Delivery recipient flow | Application or recipient login; track own delivery; obtain one-time handover code | Own-delivery only; `delivery:track:own` is intentionally accepted as recipient-only access |
| `ORDER_MANAGER` | E-commerce Admin / Order Control Tower | Application login; view all stores; confirm/process/cancel orders; manage holds, exceptions, notes, fulfillment, exports, reports, audit, and settings | Order scope; no automatic Delivery or Inventory grant |
| `ORDER_OPERATOR` | E-commerce Admin / Order Control Tower | Application login; view/process orders; confirm; hold/release; manage exceptions; add notes; view fulfillment | Cannot cancel, export, configure, or approve all order actions |
| `ORDER_SUPPORT` | E-commerce Admin / Order Support | Application login; view orders/all stores; view customer and fulfillment data; add notes | Support/read scope; no order processing or cancellation |
| `ORDER_ANALYST` | E-commerce Admin / Order Analytics | Application login; view orders/all stores; export; reports; audit | Read/report/export scope; no order mutation |
| `RETURNS_MANAGER` | E-commerce Admin / Returns | Application login; manage returns, refunds, exchanges, policies, exceptions, analytics, exports, and audit; view orders | Full seeded returns workflow; tenant/store rules still apply |
| `RETURNS_AGENT` | E-commerce Admin / Returns | Application login; create/review/approve/reject return requests; schedule pickup; receive; exchange; view customer data | Cannot configure thresholds, approve high-value refunds, or manage policies |
| `WAREHOUSE_INSPECTOR` | E-commerce Admin / Returns | Application login; view returns/orders; receive returned goods; record inspection results | Receiving/inspection only; no return approval or refund processing |
| `REFUND_APPROVER` | E-commerce Admin / Refunds | Application login; view orders/returns; calculate/process refunds; approve above-threshold refunds; view return analytics/audit | Refund boundary; no return intake, policy, or inventory operations |
| `RETURNS_ANALYST` | E-commerce Admin / Returns Analytics | Application login; view returns/orders; view analytics/audit; export return lists | Read/report/export scope; no return mutation |
| `INVENTORY_MANAGER` | Inventory | Enable Inventory; grant role; application login; manage inventory adjustments, approvals, transfers, configuration, exports, audit, warehouses, goods receipt, suppliers, and PO view | Valid Inventory role; contains approved Inventory/procurement permissions |
| `WAREHOUSE_OPERATOR` | Inventory | Application login; view/manage inventory; request adjustments/transfers; view warehouse and purchase orders; record goods receipts | Valid Inventory role with the approved goods-receipt and purchase-order-view exceptions |
| `INVENTORY_ANALYST` | E-commerce Admin / Inventory reporting | Application login; view/export inventory; view warehouses, suppliers, and purchase orders | Read/report scope; no stock mutation or approval; current supplier permission prevents use as an Inventory application role |
| `PROCUREMENT_MANAGER` | E-commerce Admin / Procurement | Application login; manage suppliers, purchase orders, goods receipts, supplier returns; approve purchase orders; view/export inventory | Procurement role; cannot be used for the Inventory application because of non-Inventory permissions |
| `PROCUREMENT_OFFICER` | E-commerce Admin / Procurement | Application login; view suppliers and inventory; create/edit/manage purchase orders | Cannot approve purchase orders, receive goods, or manage suppliers |
| `PRICING_MANAGER` | E-commerce Admin / Pricing | Application login; view/create/update pricing; view cost; export; create/update promotions | Cannot approve or publish pricing/promotions |
| `PRICING_APPROVER` | E-commerce Admin / Pricing | Application login; read/audit pricing; approve/publish prices; read/approve promotions | Approval/publishing scope; no price creation or general campaign editing |
| `PRICING_ANALYST` | E-commerce Admin / Pricing Analytics | Application login; view/audit pricing; view promotions | Read/audit scope; no price or promotion mutation |
| `MARKETING_MANAGER` | E-commerce Admin / Promotions | Application login; create/update promotions; manage coupons; view pricing/promotions | Cannot approve promotions or publish prices |
| `STOREFRONT_MANAGER` | Storefront / Content Studio | Application login when Storefront is active; create/rename storefronts; edit content; manage media/domains | Storefront management/editing; no approval or publishing |
| `CONTENT_EDITOR` | Storefront / Content Studio | Application login when Storefront is active; edit drafts/pages/banners/navigation; manage media | Editing/media scope; no storefront creation, approval, or publishing |
| `STOREFRONT_PUBLISHER` | Storefront / Content Studio | Application login when Storefront is active; approve; publish/schedule/unpublish/rollback; view audit | Approval/publishing scope; no draft editing or media management |
| `USER` | Customer-facing pages | Platform login; use identity-owned profile, addresses, loyalty, and customer operations | No explicit RBAC permission grant; endpoint ownership and customer rules apply |

### Role workflow test template

For each role above, the repeatable test is:

1. Use the seeded account from [RBAC_TEST_USERS.md](RBAC_TEST_USERS.md).
2. Create or select a test tenant.
3. Enable the recommended application entitlement.
4. Add the user as an active tenant member.
5. Grant the role for that application.
6. Sign in with the application code and tenant slug.
7. Verify the JWT contains the expected `application_code`, `tenant_id`, `tenant_role`, and permissions.
8. Verify one allowed operation returns success.
9. Verify one operation outside the role boundary returns `403`.
10. Revoke the grant and verify a new application login is refused.

For `INVENTORY_MANAGER`, `WAREHOUSE_OPERATOR`, `DELIVERY_ADMIN`, `DISPATCHER`, `DELIVERY_PARTNER`, `DELIVERY_ANALYST`, and `DELIVERY_RECIPIENT`, also verify the application-specific role validation rules before checking endpoint permissions.
