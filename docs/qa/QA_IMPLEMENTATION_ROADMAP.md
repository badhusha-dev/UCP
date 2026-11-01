# QA Implementation Roadmap

## Implemented in this vertical slice

- Source-grounded feature/test inventory and baseline default RBAC matrix.
- Isolated Spring `qa-service`, Flyway-owned `qa_db` tables, seeded suite/feature/role inventory, run history, result persistence, and action audit.
- Dedicated `qa:read`, `qa:run`, `qa:cancel` permissions; gateway and service-side enforcement; positive/negative gateway and service-policy tests.
- Fixed local backend, frontend, integration, RBAC, regression, and selected-feature plans. Unsupported smoke/E2E plans are rejected explicitly.
- Persistent run state machine, one-run lock, bounded executor, rate limit, preflight, timeout, cancellation, sanitized output, JUnit result parsing, JSON/JUnit reports, and progress polling.
- Authenticated Quality Center route in the existing shell with overview, feature catalog, run controls/history/results, RBAC view, and coverage explorer.
- Local Compose wiring, internal-only QA service routing, QA database initialization, and documentation in this directory.

## Remaining phases

1. Synchronize dynamically created user-service roles into the QA matrix and verify the matrix against live access-role records.
2. Generate an endpoint-level inventory from controller mappings/OpenAPI and verify each route's gateway and downstream authorization policy, including direct negative tests.
3. Add meaningful feature tests for uncovered procurement behavior and fill current gaps in CRM ownership/support, payment/refund, delivery assignment, inventory concurrency, order ownership, and accounting period-close flows.
4. Add Playwright E2E browser suites for login, feature navigation, checkout, core admin workflows, and the Quality Center itself; capture screenshots/traces for failures.
5. Add route-to-test and test-to-permission metadata at method/case level; expand positive, negative, object-ownership, tenant, token-revocation, and privilege-escalation RBAC coverage.
6. Add JUnit history comparison, failure triage links, artifact retention policy, export controls, and quality gates based on reviewed risk categories.
7. Integrate with approved CI workflows and source commit identity. Keep hosted pipeline triggers allow-listed and separate from deployment approvals.
8. Add code coverage collection only where it can be interpreted alongside functional/API/RBAC evidence; never use a percentage alone as a correctness claim.

## Acceptance status for this slice

The one-page flow is designed to create persistent runs, execute real registered tests, and show persisted evidence. Full application acceptance is not yet met until this code is built/deployed, the gateway and shell build pass, a local run is exercised end-to-end, and the remaining matrix/route/browser gaps above are addressed. The catalog deliberately marks unsupported or unmeasured areas as such.
