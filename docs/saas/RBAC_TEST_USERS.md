# RBAC Test Users

This is a local-development seed only. It must not be applied to shared, staging, or production databases.

## Seed

Run this after `user-service` has started and its Flyway migrations have completed:

```bash
docker exec -i ucp-postgres-1 psql -v ON_ERROR_STOP=1 -U ecommerce_admin -d user_db < scripts/dev-seed/rbac-users.sql
```

The seed is idempotent. It creates one active account for every role in `access_roles`, hashes the password with PostgreSQL `pgcrypto`, and adds the accounts to the legacy tenant.

All seeded accounts use:

```text
Password: password123
```

## Accounts

| Role | Email |
|---|---|
| ACCOUNTANT | `rbac.accountant@ucp-rbac.test` |
| ADMIN | `rbac.admin@ucp-rbac.test` |
| AUDITOR | `rbac.auditor@ucp-rbac.test` |
| CONTENT_EDITOR | `rbac.content_editor@ucp-rbac.test` |
| DELIVERY_ADMIN | `rbac.delivery_admin@ucp-rbac.test` |
| DELIVERY_ANALYST | `rbac.delivery_analyst@ucp-rbac.test` |
| DELIVERY_PARTNER | `rbac.delivery_partner@ucp-rbac.test` |
| DELIVERY_RECIPIENT | `rbac.delivery_recipient@ucp-rbac.test` |
| DISPATCHER | `rbac.dispatcher@ucp-rbac.test` |
| INVENTORY_ANALYST | `rbac.inventory_analyst@ucp-rbac.test` |
| INVENTORY_MANAGER | `rbac.inventory_manager@ucp-rbac.test` |
| MANAGER | `rbac.manager@ucp-rbac.test` |
| MARKETING_MANAGER | `rbac.marketing_manager@ucp-rbac.test` |
| ORDER_ANALYST | `rbac.order_analyst@ucp-rbac.test` |
| ORDER_MANAGER | `rbac.order_manager@ucp-rbac.test` |
| ORDER_OPERATOR | `rbac.order_operator@ucp-rbac.test` |
| ORDER_SUPPORT | `rbac.order_support@ucp-rbac.test` |
| PRICING_ANALYST | `rbac.pricing_analyst@ucp-rbac.test` |
| PRICING_APPROVER | `rbac.pricing_approver@ucp-rbac.test` |
| PRICING_MANAGER | `rbac.pricing_manager@ucp-rbac.test` |
| PROCUREMENT_MANAGER | `rbac.procurement_manager@ucp-rbac.test` |
| PROCUREMENT_OFFICER | `rbac.procurement_officer@ucp-rbac.test` |
| REFUND_APPROVER | `rbac.refund_approver@ucp-rbac.test` |
| RETURNS_AGENT | `rbac.returns_agent@ucp-rbac.test` |
| RETURNS_ANALYST | `rbac.returns_analyst@ucp-rbac.test` |
| RETURNS_MANAGER | `rbac.returns_manager@ucp-rbac.test` |
| STOREFRONT_MANAGER | `rbac.storefront_manager@ucp-rbac.test` |
| STOREFRONT_PUBLISHER | `rbac.storefront_publisher@ucp-rbac.test` |
| SUPER_ADMIN | `rbac.super_admin@ucp-rbac.test` |
| USER | `rbac.user@ucp-rbac.test` |
| WAREHOUSE_INSPECTOR | `rbac.warehouse_inspector@ucp-rbac.test` |
| WAREHOUSE_OPERATOR | `rbac.warehouse_operator@ucp-rbac.test` |
| QA_ANALYST | `rbac.qa_analyst@ucp-rbac.test` |

## Platform login

Returns a platform JWT with the role's full permission set.

```http
POST http://localhost:8080/api/users/login
Content-Type: application/json

{
  "email": "rbac.delivery_partner@ucp-rbac.test",
  "password": "password123"
}
```

Replace the email with any account from the table above to test that role's permissions.

## Application-scoped login

Returns a scoped JWT bound to a tenant and application. The seed grants every test user to their natural application on the legacy tenant (`legacy`). Example:

```http
POST http://localhost:8080/api/users/login/application
Content-Type: application/json

{
  "email": "rbac.delivery_partner@ucp-rbac.test",
  "password": "password123",
  "applicationCode": "DELIVERY_OPS",
  "tenantSlug": "legacy"
}
```

### Natural application per role

| Application | Roles |
|---|---|
| `ECOMMERCE_ADMIN` | ADMIN, MANAGER, ORDER_MANAGER/OPERATOR/SUPPORT/ANALYST, RETURNS_MANAGER/AGENT/INSPECTOR/APPROVER/ANALYST, PRICING_MANAGER/APPROVER/ANALYST, MARKETING_MANAGER, STOREFRONT_MANAGER/PUBLISHER, CONTENT_EDITOR, INVENTORY_MANAGER/ANALYST, PROCUREMENT_MANAGER/OFFICER |
| `INVENTORY` | WAREHOUSE_OPERATOR (only role that passes the inventory-only permission gate) |
| `ACCOUNTING` | ACCOUNTANT, AUDITOR |
| `DELIVERY_OPS` | DELIVERY_ADMIN, DISPATCHER, DELIVERY_PARTNER, DELIVERY_ANALYST, DELIVERY_RECIPIENT |
| `QUALITY_CENTER` | QA_ANALYST, ADMIN |

Roles not listed (SUPER_ADMIN, USER) use platform tokens only.

## Verification commands

Check that all 33 test accounts exist and have the expected role:

```bash
docker exec -i ucp-postgres-1 psql -U ecommerce_admin -d user_db \
  -c "SELECT email, role, active FROM users WHERE email LIKE 'rbac.%@ucp-rbac.test' ORDER BY role, email;"
```

Check entitlements on the legacy tenant:

```bash
docker exec -i ucp-postgres-1 psql -U ecommerce_admin -d user_db \
  -c "SELECT application_code, enabled FROM tenant_application_entitlements WHERE tenant_id = '00000000-0000-4000-8000-000000000001' ORDER BY application_code;"
```

Check which test users have application grants:

```bash
docker exec -i ucp-postgres-1 psql -U ecommerce_admin -d user_db \
  -c "SELECT u.email, u.role, g.application_code FROM user_application_grants g JOIN users u ON u.id = g.user_id WHERE u.email LIKE 'rbac.%@ucp-rbac.test' AND g.active ORDER BY u.role, g.application_code;"
```
