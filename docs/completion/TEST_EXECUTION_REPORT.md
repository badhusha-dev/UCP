# Test Execution Report — 2026-11-01

Environment: Windows 11, JDK 17, Maven wrapper offline (`-o`), H2 for JPA tests, **no Docker** (Kafka/Postgres/Redis down),
Node 24.5. The Inventory & Procurement session was editing inventory/procurement code in the same tree during the run,
so their modules' numbers reflect their in-progress work at that moment.

## Commands
```
backend> mvnw.cmd -o -fae -B test                         # baseline; stopped at cart-service after >10 min (Kafka hang)
backend> mvnw.cmd -o -fae -B test -rf :cart-service       # cart passed after test-config fix; loyalty hung the same way
backend> mvnw.cmd -o -fae -B test -rf :loyalty-service    # rest of the reactor
backend> mvnw.cmd -o -fae -B test -pl error-stack-service,stock-service,api-gateway   # after fixes
frontend/<pkg>> npm test                                   # shared-library, mfe-order, mfe-product
frontend/<app>> npx tsc --noEmit -p .                      # all 15 apps
```
Logs: session scratchpad (`baseline-part1.log`, `baseline-part2.log`, `baseline-part3.log`, `rerun.log`).

## Backend results (final numbers per module)

| Module | Tests | Failures/Errors | Notes |
|---|---|---|---|
| user-service | 12 | 0 | `TenantAccessPostgresIntegrationTest` needs Postgres (not exercised) |
| product-service | 75 | 0 | |
| inventory-service | 27 | 0 | sibling work in progress |
| stock-service | 9 | 0 | 8 baseline + 1 new token test |
| cart-service | 7 | 0 | **baseline: hung >10 min** (Kafka `max.block.ms` 60 s per send); fixed via test config |
| loyalty-service | 8 | 0 | same hang; fixed via test config |
| accounting-service | 18 | 0 | |
| crm-service | 2 | 0 | |
| procurement-service | 8 | 0 | tests added by the sibling session |
| analytics-service | 8 | 0 | |
| order-service | 93 | 0 | |
| notification-service | 9 | 0 | |
| api-gateway | 37 | 0 | 30 existing + 7 new (`GatewayTenantBoundaryTest` 5, `GatewayHeaderSanitizationTest` 2) |
| discovery-server | 1 | 0 | |
| config-server | 1 | 0 | |
| error-stack-service | 6 | 0 | **baseline: 1 error** (`testOverviewMetrics_aggregatesCorrectly`: `TO_CHAR` pattern `'HH24:00'` rejected by H2); fixed with a portable pattern |
| qa-service | 16 | 0 | |
| delivery-service | 31 | 0 | |
| **Total** | **368** | **0** | |

Baseline defects found: 2 hanging suites (cart, loyalty) and 1 erroring test (error-stack). All three are fixed and
re-run green.

## Frontend results
| Package | Result |
|---|---|
| shared-library | 39/39 pass |
| mfe-order | 20/20 pass |
| mfe-product | 17/17 pass |
| `tsc --noEmit`, 15 apps | 0 errors in each |

No UI render, browser, E2E, Postgres, Kafka, load or security-scan tests were run.

## Caveat on the new gateway tests
The new tests were written against the fixed code. Their "before" behaviour (e.g. `/api/orders/admin/orders/swagger`
forwarded without authentication) is established by reading the old `contains` logic, not by running the tests against
the old code.
