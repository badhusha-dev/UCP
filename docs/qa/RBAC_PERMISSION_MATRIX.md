# RBAC Permission Matrix

## Source of truth inspected

Default roles and permissions are created by `backend/user-service/src/main/resources/db/migration/V4__hierarchical_rbac.sql`; V5 adds the QA permissions. JWTs carry role and permission claims from `RbacService.permissionsFor`. The gateway verifies the JWT, strips caller-supplied identity headers, and enforces admin-route permission mappings in `JwtAuthenticationFilter`. `AccessControlController` and `RbacService` handle dynamic role management and hierarchy checks.

The runtime Quality Center matrix is a persisted snapshot of the checked-in default role policy. A custom role created later in user-service is not synchronized yet and must be treated as unresolved by this page until live role synchronization is implemented.

## Default role grants

| Role | Default permissions from migration | QA access |
|---|---|---|
| `SUPER_ADMIN` | `*` (all permissions) | `qa:read`, `qa:run`, `qa:cancel` |
| `ADMIN` | Every seeded permission except `application:admin` | All three QA permissions added in V5 |
| `MANAGER` | `users:read/manage`, `roles:manage`; all accounting permissions; `crm:read/manage`; `inventory:read/manage`; `stock:read/manage`; `analytics:read`; `loyalty:manage`; `orders:manage`; `procurement:read/manage`; `errors:manage` | No QA grant by default |
| `ACCOUNTANT` | `accounting:read/write/reconcile/import/export` | No QA grant |
| `AUDITOR` | `accounting:read/export`, `analytics:read` | No QA grant |
| `USER` | No default permission rows | No QA grant |

Seeded permission keys currently include: `application:admin`; `users:read`, `users:manage`; `roles:manage`; six accounting permissions; `crm:read`, `crm:manage`; `inventory:read`, `inventory:manage`; `stock:read`, `stock:manage`; `analytics:read`; `loyalty:manage`; `orders:manage`; `procurement:read`, `procurement:manage`; `errors:manage`; `catalog:manage`; and `qa:read`, `qa:run`, `qa:cancel`.

## Quality Center operation matrix

| Operation | Required permission | Super Admin | Admin | Manager | Accountant | Auditor | User |
|---|---|---:|---:|---:|---:|---:|---:|
| Read catalog, coverage, matrix, history, reports | `qa:read` | Allow | Allow | Deny | Deny | Deny | Deny |
| Start approved suite / feature / rerun failures | `qa:run` | Allow | Allow | Deny | Deny | Deny | Deny |
| Cancel active run | `qa:cancel` | Allow | Allow | Deny | Deny | Deny | Deny |
| Manage runner/environment configuration | `application:admin` | Allow | Deny | Deny | Deny | Deny | Deny |

Every API call also requires authenticated user identity. QA service methods repeat the action permission check even though the gateway enforces it first.

## Gateway policy map and gaps

| API family | Current enforcement source | Observed policy | Coverage state |
|---|---|---|---|
| `/api/admin/qa/**` | Gateway plus QA service | Read → `qa:read`; create/retry → `qa:run`; cancel → `qa:cancel` | Tested positive and negative at gateway; QA policy has unit tests |
| `/api/access/**` | Gateway | `roles:manage` | Gateway policy exists; complete role-hierarchy cases are not in the current automated suite |
| `/api/users` and `/api/users/{id}` | Gateway plus user service | Directory read requires `users:read`; own-account checks use JWT user ID | Partial; cross-user coverage needs expansion |
| `/api/accounting/admin/**` | Gateway plus accounting tests | Read/write/approve/reconcile/import/export split by action | Partial; verify every endpoint/method against controller access rules |
| `/api/inventory/admin/**`, `/api/stock/admin/**` | Gateway | Read vs manage based on HTTP method | Partial; object/warehouse scopes need dedicated tests |
| `/api/crm/**` | Gateway plus CRM staff filter | Read requires `crm:read`; writes require `crm:manage` | Partial; record ownership/internal notes need tests |
| `/api/procurement/**` | Gateway | Read vs manage based on HTTP method | Partial; no service test class currently exists |
| `/api/analytics/**` and order analytics route | Gateway | `analytics:read` | Partial; export and object-scope behavior needs coverage |
| `/api/products/admin/**` | Gateway | `catalog:manage` | Partial; feature/controller tests exist, role matrix is incomplete |
| `/api/cart/admin/**`, `/api/orders/admin/**` | Gateway | `orders:manage` | Partial; customer ownership and status transition denial cases need coverage |
| `/api/errors/admin/**`, `/dlq/**` | Gateway | `errors:manage` | Partial; redaction and artifact access need adversarial cases |
| Other service API methods | Gateway behavior plus each service's filters/security config | If no gateway mapping matches, policy depends on downstream service | Unresolved until a generated endpoint-to-policy inventory and direct negative tests exist |

The gateway has legacy staff/admin handling for old tokens without a `permissions` claim. New QA permissions are enforced again inside `qa-service`, which rejects a missing permission even if a legacy gateway path allows the request through. Other services need their own legacy-token review.

## Automated QA permission tests

`QualityCenterRbacTest` signs test-only JWTs using the test profile and verifies:

- A manager with unrelated accounting/CRM grants is denied QA reads.
- An admin with `qa:read` but not `qa:run` is denied run creation.
- QA read and run grants pass gateway authorization (the test then observes downstream unavailability because no QA service is launched in that test context).
- `qa:run` does not grant `qa:cancel`.

`QaPermissionServiceTest` checks exact grants, wildcard, Super Admin, substring denial, and malformed/missing identity. These are QA-operation tests, not complete positive/negative coverage for all business routes, roles, tenants, object ownership, or privilege-escalation paths.
