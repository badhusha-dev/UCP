# Automation Strategy

## Trusted suite registry

The only executable plans are registered in `QaCatalogRegistry` and resolved by `QaSuiteRegistry`. The browser can send a known suite ID or feature ID, but it cannot supply a command, module name, class selector, test path, environment URL, SQL, JavaScript, or test code.

| Scope | Current execution |
|---|---|
| `all` | Full Maven backend reactor tests, then the two existing shared-library Node test files |
| `backend` | `mvn -B -ntp -f backend/pom.xml test` in the copied runner workspace |
| `frontend` | Node test runner over the registered `authSlice.test.ts` and `cartSlice.test.ts` files; JUnit output is collected |
| `rbac` | Gateway QA permission tests plus QA service permission policy tests |
| `integration` | Only registered Maven classes tagged `INTEGRATION` in the catalog |
| `regression` | Same source suites as `all`; history is available for comparison, but automatic baseline diff is roadmap work |
| Feature selection | Registered classes mapped to the chosen feature; untested features return an explicit 422 and never get a passing run |
| `smoke`, `e2e` | Disabled until a real trusted plan is added |

## Execution and result handling

- Execution runs off the HTTP request thread on one bounded worker. A database lock prevents concurrent runs across service instances.
- Maven module and class arguments come from the code registry only. Frontend test file arguments are also explicit registry entries.
- A per-run temporary workspace holds a copy of backend source and generated build artifacts. Backend `target`, `.git`, `.idea`, and config repository files are excluded. Frontend tests run against the read-only shared-library source and dependency mount; reports are written to the run workspace.
- A fixed timeout is clamped to 60–3600 seconds. The default is 1800 seconds. Cancellation terminates the active child process.
- Maven Surefire XML and Node JUnit XML are parsed with DTD/external entity support disabled. Test assertions, skips, infrastructure errors, and timeouts are persisted separately.
- Logs are bounded and redact credential/token/cookie/OTP patterns and email addresses. Reports read from the database; no filesystem paths can be supplied to download routes.
- Progress is polled from persisted state and runner output; final counts are recomputed from parsed test cases.

## Current frontend/backend frameworks

- Backend: Maven reactor, Spring Boot 3.2, JUnit 5/Spring Boot Test, H2/in-memory test contexts, and WireMock in selected modules.
- Frontend: Node's built-in test runner via `node --experimental-strip-types --test`; existing cases exercise shared Redux auth and cart reducers. There is no Vitest, React Testing Library, or Playwright suite registered in the current workspace.
- CI exists in `.github/workflows/ci-backend.yml` and `ci-frontend.yml`. Triggering hosted CI from the UI is not implemented; local execution is the only target.

## Adding a test safely

1. Add a meaningful test to the service/frontend's existing test framework.
2. Confirm it uses a dedicated in-memory/test database or controlled WireMock/sandbox dependencies; avoid production credentials and destructive live targets.
3. Add the class/file, feature, service, layer, permission metadata, and actual case count to `QaCatalogRegistry`.
4. Add/adjust focused QA suite-selection and permission tests.
5. Run that class locally and then its registered feature suite. A missing result or unavailable dependency must become `BLOCKED`, not green.
6. Update `FEATURE_TEST_CATALOG.md` and the coverage notes.

## Deferred automation

Browser E2E tests, screenshots/traces, API contract generation, route-to-test mapping, JUnit history comparison, scheduled runs, code coverage, and quality gates are not yet part of the executable registry. The UI and reports label these areas as unmeasured or unavailable.
