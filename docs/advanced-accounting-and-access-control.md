# Accounting periods and role-based access

## Role hierarchy

Higher hierarchy levels may manage lower-level users and roles. A role may only grant permissions already held by its editor. Built-in roles cannot be deleted; `SUPER_ADMIN` is protected from permission changes, demotion, and deactivation when it is the last active Super Admin.

| Role | Level | Default scope |
| --- | ---: | --- |
| `SUPER_ADMIN` | 1000 | Full platform access and role governance |
| `ADMIN` | 800 | User, service, and accounting administration; cannot grant platform-wide configuration |
| `MANAGER` | 500 | Delegated role management and day-to-day operations |
| `ACCOUNTANT` | 350 | Accounting read/write, reconciliation, import, and export |
| `AUDITOR` | 250 | Accounting and analytics read/export |
| `USER` | 100 | Customer-facing features |

The User & Role Admin screen at `/profile/admin` configures roles, permissions, and account assignments. Role changes are written to `access_audit_log`. The API is rooted at `/api/access`; accounting authorization is enforced by the gateway using the verified JWT permission claims.

Permissions include per-module grants such as `accounting:read`, `accounting:write`, `accounting:approve`, `accounting:reconcile`, `accounting:import`, `accounting:export`, `crm:read`, `crm:manage`, `inventory:read`, `inventory:manage`, `stock:read`, `stock:manage`, `analytics:read`, `procurement:manage`, `errors:manage`, `orders:manage`, and `roles:manage`.

Permission claims are issued at login. Ask users to sign in again after a role assignment or permission change so their token reflects the new grants.

### Initial Super Admin

At startup, an existing active `ADMIN` account is promoted by the RBAC migration when no active root exists. To create the first root account, put these values in the private `.env` file and restart `user-service`:

```dotenv
SUPER_ADMIN_EMAIL=owner@example.com
SUPER_ADMIN_PASSWORD=use-a-unique-password-of-at-least-12-characters
SUPER_ADMIN_NAME=Platform Owner
```

Do not commit the real `.env` file. The configured email can promote an existing active administrator without a password. A disabled legacy seed administrator can only be reactivated and reset when the password is explicitly configured.

## Accounting periods

`/accounting/periods` manages non-overlapping fiscal periods. Closing a period requires a close note and records the actor and timestamp; journal entries and reversals dated inside a closed period are rejected. Reopening requires a reason and creates an accounting audit entry.

- `GET /api/accounting/admin/periods`
- `POST /api/accounting/admin/periods`
- `POST /api/accounting/admin/periods/{id}/close`
- `POST /api/accounting/admin/periods/{id}/reopen`

Period management is backed by the `accounting_periods` table and Flyway migration V17.

## Manual journal maker-checker approval

Manual journals follow a two-person workflow. An accounting writer submits a balanced draft; a different user with `accounting:approve` approves or rejects it. Only approval creates the posted journal entry, and the draft, decision, approver, and rejection reason are recorded in the accounting audit log. A rejected draft can be resubmitted by its creator. Closed-period rules are checked when the draft is created, submitted, and posted.

- `POST /api/accounting/admin/journals/drafts` creates a draft.
- `PUT /api/accounting/admin/journals/drafts/{id}` lets its creator edit a draft or rejected entry.
- `POST /api/accounting/admin/journals/drafts/{id}/submit` queues it for review.
- `GET /api/accounting/admin/journals/drafts?status=PENDING_APPROVAL` lists workflow entries.
- `POST /api/accounting/admin/journals/drafts/{id}/approve` posts the journal.
- `POST /api/accounting/admin/journals/drafts/{id}/reject` rejects it and requires a reason.

The Journal approvals workspace is available at `/accounting/journal-approvals`. Creators can revise rejected entries using the reviewer note before resubmitting. Service-generated accounting entries and the existing import posting path continue to post through their existing flow.
