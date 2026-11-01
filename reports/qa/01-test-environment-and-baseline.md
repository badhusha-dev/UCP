# QA Report 01 — Test Environment and Baseline

**Branch:** `feature/ucp-completion-modernization`  
**Recorded:** 2026-11-01 / 2026-11-02  
**Phase:** Phase 0 — Initial Assessment  
**Status:** COMPLETE

---

## 1. Host environment

| Item | Value |
|---|---|
| OS | Windows 11 Pro 10.0.26300 |
| JDK | 17.0.11 (runtime for both build and tests) |
| Maven wrapper | `backend/mvnw.cmd` 3.6.3 |
| Node | 24.5 (local) |
| npm | workspace-aware (npm install runs per workspace) |
| Docker Desktop | active; 7.62 GiB memory allocated |
| Docker Compose | V2 (integrated plugin) |

---

## 2. Backend test configuration

### 2.1 Test database strategy

Each Spring Boot service uses **H2 in-memory** by default (`spring.datasource.url=jdbc:h2:mem:…`
in `src/test/resources/application-test.yml` or `application.yml`). The test profile sets
`spring.flyway.enabled=false` for H2 runs. Postgres-guarded integration tests activate only when
the corresponding `UCP_TEST_*_POSTGRES_URL` environment variable is present and its value contains
`ucp_scratch_` (enforced by a `@Condition` or `@EnabledIf` guard in the test class).

### 2.2 Postgres scratch databases (QA run)

Four scratch Postgres databases were created for this QA run in the running `ucp-test-pg` container:

| Database | Env var | Service |
|---|---|---|
| `ucp_scratch_qa_accounting` | `UCP_TEST_ACCOUNTING_POSTGRES_URL` | accounting-service |
| `ucp_scratch_qa_user` | `UCP_TEST_POSTGRES_URL` | user-service |
| `ucp_scratch_qa_delivery` | `UCP_TEST_DELIVERY_POSTGRES_URL` | delivery-service |
| `ucp_scratch_qa_inventory` | `UCP_TEST_INVENTORY_POSTGRES_URL` | inventory-service |

Password sourced from the running container's `POSTGRES_PASSWORD` env var (not written to this report).

### 2.3 Kafka in tests

No real Kafka broker is used. Tests that exercise the outbox or event publishing use one of:
- A mocked `KafkaTemplate` (unit tests)
- `SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:1` (unreachable; triggers immediate failure on connect
  attempts, which tests assert do not block normal business paths)

### 2.4 Dependency isolation

WireMock stubs replace downstream HTTP calls in `ProductResilienceWireMockTest` and
`OrderResilienceWireMockTest`. All other integration tests wire only the module's own application
context plus H2 or Postgres.

---

## 3. Backend baseline — QA run (2026-11-01 → 2026-11-02)

**Command:**
```
backend\mvnw.cmd -B -ntp -fae test
```
**Environment variables set:**
```
UCP_TEST_POSTGRES_URL=jdbc:postgresql://localhost:15432/ucp_scratch_qa_user
UCP_TEST_POSTGRES_USER=postgres
UCP_TEST_POSTGRES_PASSWORD=<from container>
UCP_TEST_ACCOUNTING_POSTGRES_URL=jdbc:postgresql://localhost:15432/ucp_scratch_qa_accounting
UCP_TEST_ACCOUNTING_POSTGRES_USER=postgres
UCP_TEST_ACCOUNTING_POSTGRES_PASSWORD=<from container>
UCP_TEST_DELIVERY_POSTGRES_URL=jdbc:postgresql://localhost:15432/ucp_scratch_qa_delivery
UCP_TEST_DELIVERY_POSTGRES_USER=postgres
UCP_TEST_DELIVERY_POSTGRES_PASSWORD=<from container>
UCP_TEST_INVENTORY_POSTGRES_URL=jdbc:postgresql://localhost:15432/ucp_scratch_qa_inventory
UCP_TEST_INVENTORY_POSTGRES_USER=postgres
UCP_TEST_INVENTORY_POSTGRES_PASSWORD=<from container>
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:1
```

**Log:** `D:\APACHE~1.6\bin\claude\D--product-UCP\798ba0ad-df34-4cee-8150-9d897351f161\scratchpad\qa-backend-baseline.log`

### Final results (QA run completed 2026-11-02 00:06:52, 17:31 min total)

| Module | Tests run | Failures | Errors | Skipped | Status | Postgres-gated |
|---|---|---|---|---|---|---|
| `ucp-outbox` | 10 | 0 | 0 | 0 | **PASS** | No |
| `user-service` | 19 | 0 | 0 | 0 | **PASS** | Yes (2 of 19) |
| `product-service` | 83 | 0 | 0 | 0 | **PASS** | No |
| `inventory-service` | 29 | 0 | 0 | 0 | **PASS** | Yes (20 of 29) |
| `stock-service` | 11 | 0 | 0 | 0 | **PASS** | No |
| `cart-service` | 9 | 0 | 0 | 0 | **PASS** | No |
| `loyalty-service` | 9 | 0 | 0 | 0 | **PASS** | No |
| `accounting-service` | 27 | 0 | 0 | 0 | **PASS** | Yes (5 of 27) |
| `crm-service` | 2 | 0 | 0 | 0 | **PASS** | No |
| `procurement-service` | 8 | 0 | 0 | 0 | **PASS** | No |
| `analytics-service` | 8 | 0 | 0 | 0 | **PASS** | No |
| `order-service` | 101 | 0 | 0 | 0 | **PASS** | No |
| `notification-service` | 11 | 0 | 0 | 0 | **PASS** | No (Kafka mocked) |
| `api-gateway` | 41 | 0 | 0 | 0 | **PASS** | No |
| `discovery-server` | 1 | 0 | 0 | 0 | **PASS** | No |
| `config-server` | 1 | 0 | 0 | 0 | **PASS** | No |
| `error-stack-service` | 6 | 0 | 0 | 0 | **PASS** | No |
| `qa-service` | 16 | 0 | 0 | 0 | **PASS** | No |
| `delivery-service` | 37 | 0 | 0 | 0 | **PASS** | Yes (6 of 37) |
| **TOTAL (19 modules)** | **429** | **0** | **0** | **0** | **BUILD SUCCESS** | — |

> Evidence: `qa-backend-baseline.log` (8241 lines). Results taken directly from surefire's
> `[INFO] Tests run:` summary lines. No test was mocked, skipped, or hidden.

### Previous full-backend run for reference

Run from `full-backend.log` (2026-11-01 8:44 PM, before B-38 fix, without Postgres ITEs except those already enabled):

| Module | Run | Failures | Notes |
|---|---|---|---|
| ucp-outbox | 10 | 0 | — |
| config-server | 1 | 0 | — |
| discovery-server | 1 | 0 | — |
| api-gateway | 41 | 0 | — |
| user-service | 18 | **1** | B-38 failure (since fixed); prior to new negative test |
| product-service | 83 | 0 | — |
| order-service | 101 | 0 | — |
| accounting-service | 27 | 0 | — |
| analytics-service | 8 | 0 | — |
| cart-service | 8 | 0 | H2 only; CartTransactionContractTest not yet written |
| crm-service | 2 | 0 | — |
| delivery-service | 37 | 0 | — |
| error-stack-service | 6 | 0 | — |
| inventory-service | 29 | 0 | — |
| loyalty-service | 9 | 0 | — |
| notification-service | 11 | 0 | — |
| procurement-service | 8 | 0 | — |
| qa-service | 16 | 0 | — |
| stock-service | 11 | 0 | — |
| **Total** | **427** | **1** | — |

---

## 4. Frontend baseline — QA run (2026-11-01)

**Commands:** TypeScript check per workspace, then build, lint (workspaces with lint scripts), and
tests (workspaces with test scripts). One orphan test file in `mfe-inventory` run manually via
`node --experimental-strip-types --test`.

**Log files:** `scratchpad/qa-tsc-<workspace>.log`, `qa-build-<workspace>.log`,
`qa-lint-<workspace>.log`; test output captured in the task output file.

### TypeScript — all 16 workspaces

| Workspace | tsc exit | Notes |
|---|---|---|
| shared-library | **0** | — |
| shell-app | **0** | — |
| mfe-accounting | **0** | — |
| mfe-analytics | **0** | — |
| mfe-cart | **0** | — |
| mfe-crm | **0** | — |
| mfe-delivery | **0** | — |
| mfe-error-stack | **0** | — |
| mfe-inventory | **0** | — |
| mfe-loyalty | **0** | — |
| mfe-order | **0** | — |
| mfe-procurement | **0** | — |
| mfe-product | **0** | — |
| mfe-stock | **0** | — |
| mfe-super-admin | **0** | — |
| mfe-user | **0** | — |

**TypeScript result: PASS (16/16 workspaces, 0 type errors)**

### Build — all 16 workspaces

All 16 workspaces: `vite build` exit 0. No build errors.

**Build result: PASS (16/16)**

### Lint — workspaces with lint script (4 workspaces)

| Workspace | lint exit |
|---|---|
| mfe-order | **0** |
| mfe-product | **0** |
| mfe-user | **0** |
| shell-app | **0** |

**Lint result: PASS (4/4 workspaces with lint scripts)**

### Tests — workspaces with test scripts

| Workspace | Tests | Pass | Fail | Exit |
|---|---|---|---|---|
| shared-library | 48 | 48 | 0 | **0** |
| mfe-product | 17 | 17 | 0 | **0** |
| mfe-order | 20 | 20 | 0 | **0** |
| mfe-inventory (orphan, manual) | 12 | 12 | 0 | **0** |
| **Total** | **97** | **97** | **0** | — |

**Test result: PASS (4/4 runnable test suites, 97/97 tests)**

---

## 5. Infrastructure checks

| Check | Tool | Result | Details |
|---|---|---|---|
| Docker Compose config | `docker compose config` | **PASS** | 46 services, valid |
| Kubernetes manifests | `kubectl kustomize` | **PASS** | 34 Deployments, 36 Services, 18 NetworkPolicies |
| YAML parse | PowerShell YAML check | **PASS with notes** | 193 files valid; 16 Helm template files produce false-positive parse warnings (not application YAML) |
| JSON parse | PowerShell ConvertFrom-Json | **PASS with notes** | 36 files; 3 files have UTF-8 BOM (`devops/backups/`, `devops/reports/`) — generated artifacts, tracked. No impact on runtime. |
| Secret scan (private keys) | regex | **PASS** | No PEM-format private keys found in tracked files |
| Secret scan (Slack token) | regex | **NEEDS REVIEW** | `kubernetes/gitops/flagger/flagger-alert-provider.yml` line 12 contains a value with the shape of a Slack webhook URL (three uppercase-only segments, no digits). Cannot be proven fake without external verification. The file includes `secretRef:` on the next line. Value not reproduced here. Present on `origin/main` since commit `c57de2be`. **Owner must review and rotate if real.** |
| Default `admin_password` literal | grep | **LOW** | Literal `admin_password` in test YAML and two doc files; also in `backend/config-repo` service YMLs. No production secret; placeholder default. |

---

## 6. Runtime health snapshot (deployed `ucp` stack)

Taken 2026-11-01 23:57 from the running `ucp` Docker Compose project.

### Containers

| Container | Status | Actuator health |
|---|---|---|
| ucp-postgres-1 | Up 26 min (healthy) | n/a |
| ucp-redis-1 | Up 46 min (healthy) | n/a |
| ucp-zookeeper-1 | Up 46 min | n/a |
| ucp-kafka-1 | Up 46 min (healthy) | n/a |
| ucp-config-server-1 | Up 42 min (healthy) | UP |
| ucp-discovery-server-1 | Up 43 min (healthy) | UP |
| ucp-user-service-1 | Up 38 min | UP |
| ucp-product-service-1 | Up 33 min | UP |
| ucp-order-service-1 | Up 34 min (healthy) | UP |
| ucp-cart-service-1 | Up 11 min | UP |
| ucp-inventory-service-1 | Up 38 min (healthy) | UP |
| ucp-stock-service-1 | Up 35 min (healthy) | UP |
| ucp-api-gateway-1 | Up 31 min | UP |
| ecommerce-platform-mfe-cart-1 | Up 31 min | n/a (nginx) |
| ucp-mfe-order-1 | Up 31 min | n/a (nginx) |
| ucp-mfe-product-1 | Up 31 min | n/a (nginx) |
| ucp-mfe-super-admin-1 | Up 31 min | n/a (nginx) |
| ucp-mfe-user-1 | Up 31 min | n/a (nginx) |
| ucp-shell-app-1 | Up 31 min | n/a (nginx) |

**Deployed service count:** 19 containers.  
**Not deployed:** accounting, analytics, crm, delivery, error-stack, loyalty, notification,
procurement, qa services; 9 other frontends; Zipkin, Prometheus, Grafana, Alertmanager, ELK, Vault, Kafka UI.

### Frontend HTTP check

| Port | Service | HTTP status |
|---|---|---|
| 3000 | shell-app | 200 |
| 3001 | mfe-product | 200 |
| 3002 | mfe-order | 200 |
| 3003 | mfe-user | 200 |
| 3005 | mfe-cart | 200 |
| 3013 | mfe-super-admin | 200 |

**All 9 deployed backend services report `UP`. All 6 deployed frontends return HTTP 200.**
