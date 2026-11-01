# RBAC Role Access Report

Snapshot of the user-service RBAC model on 2026-11-02. The report covers the role, module, feature, and operation boundaries represented by `access_roles`, `access_permissions`, and `access_role_permissions`.

## Access rules

- `SUPER_ADMIN` is a protected platform role. The service returns the wildcard permission `*` for it, even though its database row contains the original platform permission set.
- All other roles receive only the permissions listed in `access_role_permissions`.
- Tenant application login requires a tenant membership, an enabled application entitlement, an active user application grant, and a tenant role assignment.
- A tenant role is still constrained by the application being accessed. For example, an Inventory grant cannot use an unrelated Order or Delivery role.
- Role hierarchy controls administration. A role can manage users or roles only below its own hierarchy level and only when it has `roles:manage`.
- Built-in roles cannot be deleted. The last active Super Admin cannot be demoted or deactivated.

## Role access matrix

The operation column lists the effective permission keys. `*` means all permissions.

| Role | Level | Module/page scope | Features and operations |
|---|---:|---|---|
| `SUPER_ADMIN` | 1000 | Platform / Super Admin | `*`; platform configuration, tenant/application administration, all registered modules, and RBAC governance |
| `ADMIN` | 800 | Platform admin, accounting, catalog, CRM, inventory, orders, pricing, procurement, QA, returns, storefront, users | Full permissions granted to `ADMIN` by the migrations, excluding `application:admin` and tenant-delivery role permissions; includes create/read/update/approve/publish/export/manage operations across the listed modules |
| `DELIVERY_ADMIN` | 700 | Delivery Operations | Read/manage deliveries; dispatch; read/manage partners; partner self-service; tracking; proof; incidents; configuration; reports; audit |
| `RETURNS_MANAGER` | 620 | Returns and Orders | Full returns workflow including review, approve/reject, pickup, receive, inspect, refunds, exchanges, policies, exceptions, analytics, export, audit; order read/all-stores |
| `INVENTORY_MANAGER` | 600 | Inventory and Procurement | Inventory view, adjustments and approvals, transfers and approvals, configuration, export, audit; warehouse management; supplier view; purchase-order view; goods receipt |
| `ORDER_MANAGER` | 600 | Order Management | Full order control tower: view/all-stores, customer data, confirm, process, hold/release, cancel, bulk, exceptions, notes, fulfillment, export, reports, audit, settings |
| `PROCUREMENT_MANAGER` | 580 | Procurement and Inventory | Supplier view/manage; purchase-order view/manage/approve; goods receipt; supplier returns; inventory view/export |
| `PRICING_APPROVER` | 550 | Pricing and Promotions | Pricing read/approve/publish/audit; promotion read/approve |
| `STOREFRONT_PUBLISHER` | 550 | Storefront / Content Studio | Storefront read, approve, publish, audit |
| `REFUND_APPROVER` | 540 | Returns and Orders | Return view; refund process/approve; return analytics/audit; order view/all-stores |
| `DISPATCHER` | 500 | Delivery Operations | Delivery read/manage/dispatch; partner read; tracking; incidents; reports |
| `MANAGER` | 500 | Business operations, accounting, CRM, inventory, orders, procurement, users | User read/manage; role management; accounting read/write/approve/reconcile/import/export; CRM read/manage; inventory/stock read/manage; analytics; loyalty; orders; procurement; errors |
| `PRICING_MANAGER` | 450 | Pricing and Promotions | Pricing read/create/update/cost-read/export; promotion read/create/update |
| `STOREFRONT_MANAGER` | 450 | Storefront / Content Studio | Storefront read/manage/edit/media/domain |
| `MARKETING_MANAGER` | 400 | Pricing and Promotions | Promotion read/create/update; coupon management; pricing read |
| `ACCOUNTANT` | 350 | Accounting | Accounting read/write/reconcile/import/export |
| `ORDER_OPERATOR` | 350 | Order Management | Order view/all-stores, confirm, process, hold/release, exceptions, notes, fulfillment |
| `RETURNS_AGENT` | 340 | Returns and Orders | Return view/customer, create, review, approve/reject, pickup, receive, exchange; order view/all-stores |
| `PROCUREMENT_OFFICER` | 330 | Procurement and Inventory | Supplier view; purchase-order view/manage; inventory view |
| `CONTENT_EDITOR` | 300 | Storefront / Content Studio | Storefront read/edit/media |
| `WAREHOUSE_INSPECTOR` | 260 | Returns and Orders | Return view/receive/inspect; order view/all-stores |
| `AUDITOR` | 250 | Accounting and Analytics | Accounting read/export; analytics read |
| `ORDER_SUPPORT` | 250 | Order Management | Order view/all-stores, customer view, fulfillment view, internal notes |
| `WAREHOUSE_OPERATOR` | 220 | Inventory and Procurement | Inventory read/view/manage, adjust, transfer; warehouse view; purchase-order view; goods receipt |
| `DELIVERY_PARTNER` | 200 | Delivery Operations | Delivery read; own availability/work; tracking; proof; incidents |
| `DELIVERY_ANALYST` | 150 | Delivery Operations | Delivery read/tracking/report/audit |
| `ORDER_ANALYST` | 150 | Order Management | Order view/all-stores, export, reports, audit |
| `PRICING_ANALYST` | 150 | Pricing and Promotions | Pricing read/audit; promotion read |
| `RETURNS_ANALYST` | 150 | Returns and Orders | Return view/analytics/audit/export; order view/all-stores |
| `QA_ANALYST` | 160 | Quality Center | `qa:read` — view QA catalog, runs, results, and reports |
| `INVENTORY_ANALYST` | 140 | Inventory and Procurement | Inventory view/export; warehouse view; supplier view; purchase-order view |
| `DELIVERY_RECIPIENT` | 100 | Delivery recipient page | `delivery:track:own`: track the recipient's own delivery and obtain the one-time handover code |
| `USER` | 100 | Customer pages | No explicit RBAC permission grant. Authenticated customer capabilities are enforced by user identity and endpoint ownership rules. |

## Module, feature, and operation catalog

### Platform and identity

| Page/module | Feature | Operation / permission |
|---|---|---|
| Super Admin / Platform | Platform configuration | `application:admin` — manage platform-wide configuration |
| Users | User directory | `users:read` — view users |
| Users | Account and role administration | `users:manage` — change user roles and account status |
| Roles | Delegated RBAC administration | `roles:manage` — create roles and grant permissions within authority |
| Roles | Full platform access | `*` — Super Admin service-level wildcard |

### Accounting and analytics

| Page/module | Feature | Operation / permission |
|---|---|---|
| Accounting | Records and reports | `accounting:read` — view |
| Accounting | Journal and record editing | `accounting:write` — create/update |
| Accounting | Entry decisions | `accounting:approve` — approve/reverse |
| Accounting | Reconciliation | `accounting:reconcile` — update reconciliation decisions |
| Accounting | Data movement | `accounting:import`, `accounting:export` — import/export |
| Analytics | Business analytics | `analytics:read` — view analytics |

### Delivery Operations

| Page/module | Feature | Operation / permission |
|---|---|---|
| Delivery | Delivery board | `delivery:read` — view assigned-client delivery operations |
| Delivery | Delivery lifecycle | `delivery:manage` — create/manage deliveries |
| Delivery | Dispatch | `delivery:dispatch` — dispatch/reassign delivery partners |
| Delivery | Partner directory | `delivery:partners:read`, `delivery:partners:manage` — view/review/manage partner eligibility |
| Delivery | Partner workspace | `delivery:partner:self` — manage own availability and assigned work |
| Delivery | Tracking | `delivery:tracking` — view/submit delivery locations |
| Delivery | Completion evidence | `delivery:proof` — capture/view delivery proof |
| Delivery | Incidents | `delivery:incident` — report/manage incidents |
| Delivery | Operations policy | `delivery:config` — manage zones and operating policy |
| Delivery | Reports and audit | `delivery:report`, `delivery:audit` — view reports/audit history |
| Delivery recipient | Own delivery tracking | `delivery:track:own` — track own delivery and obtain handover code |

### Catalog, CRM, loyalty, stock, and diagnostics

| Page/module | Feature | Operation / permission |
|---|---|---|
| Catalog | Product catalog and offers | `catalog:manage` — manage |
| CRM | CRM records | `crm:read`, `crm:manage` — view/manage |
| Loyalty | Loyalty operations | `loyalty:manage` — manage |
| Stock | Stock records | `stock:read`, `stock:manage` — view/manage |
| Error Stack | Diagnostics | `errors:manage` — view/manage error diagnostics |

### Order Management

| Page/module | Feature | Operation / permission |
|---|---|---|
| Orders | Order board and saved views | `order:view`, `order:view-all-stores` — view assigned/all stores |
| Orders | Customer information | `order:customer:view` — view customer contact/address data |
| Orders | Order decisioning | `order:confirm`, `order:cancel` — confirm/reject/cancel |
| Orders | Fulfillment | `order:process`, `order:fulfillment:view` — process and view fulfillment |
| Orders | Holds | `order:hold`, `order:release` — hold/release |
| Orders | Exceptions | `order:exception:manage` — scan, assign, escalate, resolve |
| Orders | Notes | `order:note:create` — create internal notes |
| Orders | Bulk actions | `order:bulk` — run bulk operations subject to action permission |
| Orders | Reporting and audit | `order:report:view`, `order:audit:view`, `order:export` — report/audit/export |
| Orders | Workflow settings | `order:settings` — change SLA/workflow settings |
| Orders | Legacy order administration | `orders:manage` — manage orders |

### Pricing and Promotions

| Page/module | Feature | Operation / permission |
|---|---|---|
| Pricing | Price workspace | `pricing:read` — view prices/history/dashboard |
| Pricing | Price creation and changes | `pricing:create`, `pricing:update` — create/update/schedule/bulk-update |
| Pricing | Cost and exports | `pricing:cost:read`, `pricing:export` — view/edit cost and export price lists |
| Pricing | Approval and publishing | `pricing:approve`, `pricing:publish` — approve/reject and publish/rollback |
| Pricing | Audit | `pricing:audit` — view pricing/promotion audit history |
| Promotions | Campaign workspace | `promotion:read` — view campaigns/calendar/approvals/performance |
| Promotions | Campaign editing | `promotion:create`, `promotion:update` — create/edit/submit/pause/cancel |
| Promotions | Campaign decisions | `promotion:approve` — approve/reject |
| Promotions | Coupons | `coupon:manage` — generate/pause/revoke and view redemptions |

### Inventory and Procurement

| Page/module | Feature | Operation / permission |
|---|---|---|
| Inventory | Inventory workspace | `inventory:view`, `inventory:read`, `inventory:manage` — view/manage |
| Inventory | Adjustments | `inventory:adjust`, `inventory:adjust-approve` — request and approve/reject |
| Inventory | Transfers | `inventory:transfer`, `inventory:transfer-approve` — create/process and approve/reject |
| Inventory | Configuration and reporting | `inventory:config`, `inventory:export`, `inventory:audit` — configure/export/audit |
| Warehouse | Warehouses and locations | `warehouse:view`, `warehouse:manage` — view/manage |
| Suppliers | Supplier directory | `supplier:view`, `supplier:manage` — view/manage/approve/archive |
| Purchase Orders | Purchase order workflow | `purchase-order:view`, `purchase-order:manage`, `purchase-order:approve` — view/create/edit/submit/cancel/approve/reject |
| Goods Receipt | Receiving | `goods-receipt:manage` — record receipts, inspection, accepted quantities |
| Supplier Returns | Supplier returns | `supplier-return:manage` — create/complete |
| Procurement | Legacy procurement administration | `procurement:read`, `procurement:manage` — view/manage |

### Returns and Refunds

| Page/module | Feature | Operation / permission |
|---|---|---|
| Returns | Return dashboard | `return:view`, `return:view-customer` — view returns and permitted customer data |
| Returns | Request lifecycle | `return:create`, `return:review`, `return:approve`, `return:reject` — create/review/approve/reject |
| Returns | Reverse logistics | `return:pickup`, `return:receive`, `return:inspect` — schedule, receive, inspect |
| Refunds | Refund processing | `return:refund-process`, `return:refund-approve` — calculate/send and approve above threshold |
| Exchanges | Exchange workflow | `return:exchange` — create/approve/fulfill |
| Policies | Policy workflow | `return:policy-manage`, `return:policy-approve` — draft/submit and approve/publish/rollback |
| Exceptions | Exception and fraud handling | `return:exception-manage` — assign/escalate/resolve/dismiss |
| Returns | Analytics and exports | `return:analytics-view`, `return:export` — view/export |
| Returns | Audit and configuration | `return:audit-view`, `return:config` — audit and thresholds |

### Storefront and Content Studio

| Page/module | Feature | Operation / permission |
|---|---|---|
| Storefront | Storefront workspace | `storefront:read`, `storefront:manage` — view/create/rename |
| Storefront | Content editing | `storefront:edit` — edit drafts/pages/banners/navigation and submit for review |
| Storefront | Media | `storefront:media` — upload/delete images |
| Storefront | Review and publishing | `storefront:approve`, `storefront:publish` — approve/send back and publish/schedule/unpublish/rollback |
| Storefront | Domains | `storefront:domain` — manage domains, verification, SSL checks |
| Storefront | Audit | `storefront:audit` — view audit history |

### Quality Center

| Page/module | Feature | Operation / permission |
|---|---|---|
| Quality Center | QA catalog and results | `qa:read` — view catalog, runs, results, reports |
| Quality Center | Test execution | `qa:run` — start approved local suites |
| Quality Center | Run control | `qa:cancel` — cancel active test runs |

## Completed in this work

- Added the dev-only [RBAC user seed](../../scripts/dev-seed/rbac-users.sql).
- Seeded one active login for every currently installed RBAC role: 33 users across platform, delivery, order, returns, inventory/procurement, pricing, storefront, accounting, QA, and customer roles (including `QA_ANALYST` added by V18).
- Passwords are stored as BCrypt-compatible hashes generated by PostgreSQL `pgcrypto`; no plaintext password is inserted into `users`.
- Added legacy-tenant membership for all seeded users.
- Added tenant application entitlements for all five ACTIVE applications (`ECOMMERCE_ADMIN`, `INVENTORY`, `ACCOUNTING`, `DELIVERY_OPS`, `QUALITY_CENTER`) on the legacy development tenant.
- Added user application grants and tenant role assignments for every seeded user to their natural application; each user can now exercise `POST /api/users/login/application` without additional setup.
- Added Flyway migration V18 (`QA_ANALYST` role at level 160 with `qa:read`), filling the QUALITY_CENTER gap where previously only `SUPER_ADMIN` and `ADMIN` held QA permissions.
- Added `RbacRolePermissionTest`: Testcontainers-backed integration test covering platform login and JWT claims for all 33 roles, application-scoped login for five representative role/application pairs, and boundary enforcement (missing grant returns 403, wrong tenant returns 403, non-SUPER_ADMIN blocked from platform endpoints).
- Documented the complete test-user list with application login examples in [RBAC_TEST_USERS.md](RBAC_TEST_USERS.md).

## Incomplete or intentionally out of scope

- The seed is development-only and uses the shared password `password123`; it must not be used in shared, staging, or production environments.
- No automated test currently verifies every permission against every endpoint for every role. The parametrized test covers a representative permission per role; endpoint-specific ownership, threshold, and maker-checker rules require separate verification.
- RBAC permissions are present in the database, but not every frontend page has a dedicated UI test or complete role-aware navigation coverage.
- Application registry and tenant lifecycle functionality remains partial: grants/revocation and audit exist, while broader lifecycle, plan/limit, and registry UI workflows remain incomplete.
- Tenant isolation is not complete across all services; role permission checks do not replace service-level tenant filtering.
- STOREFRONT, VENDOR_PORTAL, CRM, and CUSTOMER_SUPPORT applications are `PLANNED`; no entitlements or grants are seeded for them.

## Validation notes

The local development database currently contains 32 seeded role users. Use the role accounts documented in [RBAC_TEST_USERS.md](RBAC_TEST_USERS.md) to verify login and permission behavior. This report describes authorization capabilities; endpoint-specific tenant, store, ownership, approval-threshold, and maker-checker rules still apply.
