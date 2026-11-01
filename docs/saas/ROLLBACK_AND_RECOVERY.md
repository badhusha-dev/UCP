# Tenant Inventory Slice — Rollback and Recovery

## Migration scope

- `user-service` V6 creates tenant, application, entitlement, membership, grant, role-assignment and access-audit tables. It seeds application metadata and the `legacy` tenant, then backfills existing accounts as legacy memberships.
- `inventory-service` V3 adds `tenant_id` to inventory-owned tables, assigns existing rows to the same stable `legacy` tenant UUID, enforces tenant ownership, and changes product uniqueness to `(tenant_id, product_id)`. The legacy UUID is the migration default for older instances that omit the new field during a controlled rollout.
- Historical migrations are not edited. The migrations contain no destructive data removal.

## Before a shared-environment rollout

1. Take and verify restorable backups of both service-owned databases.
2. Record row counts for `inventory_item`, `stock_reservation`, `stock_movement` and `back_in_stock_alert`.
3. Apply the `user-service` migration before enabling new Inventory logins.
4. Apply the Inventory migration with the Inventory service stopped or in a coordinated maintenance window.
5. Confirm all pre-existing rows have the legacy tenant ID and that row counts match the pre-migration counts.
6. Deploy the tenant-aware Inventory binary before creating multiple tenant rows for the same product ID.
7. Run the PostgreSQL-backed access and tenant-isolation tests before resuming writes.

## Recovery

- If migration validation fails before application traffic resumes, stop the affected service, preserve its database and logs, correct the forward migration in a new migration, and retry against a restored test copy first.
- Do not delete the new tenant columns/tables or rewrite an applied Flyway migration as a rollback.
- If an application rollback is required after tenant-owned Inventory data has been written, stop Inventory writes and restore the Inventory database from the pre-V3 backup together with the matching previous application version. The older binary has no tenant filtering and cannot safely serve duplicate product IDs across tenants.
- `user-service` V6 data can be left in place for a code rollback because it is additive, but restore its matching backup if control-plane records must be discarded. Restore both service databases from the same recorded recovery point if their tenant state diverges.
- Never drop persistent Docker Compose database volumes as a rollback shortcut.

## Local isolated migration tests

The PostgreSQL integration tests can use Testcontainers when Docker is available. On machines with a local PostgreSQL server, point the tests at newly created, disposable databases using:

- `UCP_TEST_POSTGRES_URL`, `UCP_TEST_POSTGRES_USERNAME`, `UCP_TEST_POSTGRES_PASSWORD` for user-service migrations and the tenant-access scenario.
- `UCP_TEST_INVENTORY_POSTGRES_URL`, `UCP_TEST_INVENTORY_POSTGRES_USERNAME`, `UCP_TEST_INVENTORY_POSTGRES_PASSWORD` for Inventory migrations and tenant-isolated API tests.

Do not point either test at an application database. Test setup clears Inventory tables and the identity test writes users, tenants and grants.
