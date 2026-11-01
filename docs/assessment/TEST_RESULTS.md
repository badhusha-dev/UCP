# Test Results

Recorded 2026-11-01 on `feature/ucp-completion-modernization`. Each row is a command that was run in this pass or in the session, with its actual result. Rows marked **not run** were not attempted, and no result is claimed for them.

## Backend

| Run | Command | Result |
|---|---|---|
| Full backend, Postgres enabled | `backend\mvnw.cmd -B -ntp -fae test`, with the four `UCP_TEST_*_POSTGRES_URL` variables set to fresh `ucp_scratch_full_*` databases | 427 executed, 426 passed, **1 failed**, 0 errors, 0 skipped. Failure: `TenantAccessPostgresIntegrationTest` at line 109 (B-38). 18 of 19 modules succeed. |
| product-service | `mvnw -pl product-service test` | 83 run, 0 failed (`BUILD SUCCESS`) |
| accounting-service Postgres | `mvnw -pl accounting-service test -Dtest=AccountingTenantIsolationPostgresTest` | 5 run, 0 failed (Flyway reached V19) |
| delivery-service Postgres | `mvnw -pl delivery-service test -Dtest=DeliveryTenantIsolationPostgresIntegrationTest` | 6 run, 0 failed (Flyway V1 to V2) |
| user-service Postgres | `mvnw -pl user-service test -Dtest=TenantAccessPostgresIntegrationTest` | Before the B-38 fix: 1 run, **1 failed** at line 109; remaining assertions not reached. After `f216d479`: 2 run, 0 failed, including the original test to its end and a new negative test. |
| user-service, all tests | `mvnw -pl user-service test`, with `UCP_TEST_POSTGRES_URL` set to a scratch database | 19 run, 0 failed (`BUILD SUCCESS`) |
| inventory-service | `mvnw -pl inventory-service -am test`, with `UCP_TEST_INVENTORY_POSTGRES_URL` set | inventory 29 run, 0 failed; outbox 10 run, 0 failed (Flyway V1 to V5) |
| Migrations, stock, cart, loyalty, order | `psql -v ON_ERROR_STOP=1` in version order into scratch databases | All files apply. **Not a Flyway run**; no tests. |

A run with the first `-Dtest` attempt (comma-separated list) did not execute any tests. `cmd` split the argument. That attempt is not counted.

## Frontend

| Check | Command | Result |
|---|---|---|
| TypeScript, all 16 workspaces | `npx tsc --noEmit -p <workspace tsconfig>` | 0 errors |
| shared-library tests | `npm test --workspace=shared-library` | 48 pass, 0 fail |
| mfe-product tests | `npm test --workspace=mfe-product` | 17 pass, 0 fail |
| mfe-order tests | `npm test --workspace=mfe-order` | 20 pass, 0 fail |
| Builds, changed workspaces | `npm run build` for shared-library, mfe-product, mfe-order, mfe-analytics | exit 0 |
| Lint, changed workspaces | `npm run lint` for mfe-order and mfe-product | exit 0 |

## Not run

- Container images: not built or started in this pass. The Docker daemon is running.
- Compose stack: `docker compose config` was run with placeholder secrets (46 services). The stack was not started.
- Kubernetes: `kubectl kustomize` builds (34 Deployments, 18 NetworkPolicies). Not applied to a cluster.
- Browser: no UI flow was exercised in any app, including the storefront changes (SF-01).
- Kafka: no broker. Outbox publication is tested in the outbox module only.
- Postgres for the cart, loyalty, order, stock, notification, qa, error-stack and crm services: no Postgres test path exists yet.
- Load, DAST and penetration testing.

## Stale results

- The earlier full run (385 executed, 5 skipped, `BUILD SUCCESS`) is superseded by the run above. It skipped the Postgres tests that now run.
- Status documents that say "no Docker daemon" are out of date.
