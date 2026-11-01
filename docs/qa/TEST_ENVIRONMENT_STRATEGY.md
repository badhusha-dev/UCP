# Test Environment Strategy

## Allowed target

The first Quality Center release accepts only `LOCAL_ISOLATED`. Other environments (including staging or production) are rejected by the backend. The selection is not a free-form URL.

The QA API and run history use their own `qa_db`. The worker writes to a UUID-named workspace under the dedicated QA workspace directory and removes it after evidence has been saved. In Compose, backend source, shared-library test source, and the existing frontend dependency directory are mounted read-only; the QA worker has separate writable workspace and Maven cache volumes. The repo root and `.env` are not mounted.

## Process isolation

- Request data can select only a static suite ID or static catalog feature ID. No shell, SQL, JavaScript, file, or user-supplied test selector is accepted.
- Child process environment is cleared; only `PATH`, `JAVA_HOME`, `HOME`, `CI`, `TZ`, `QA_ENVIRONMENT`, and bounded `MAVEN_OPTS` are set. Service/database/token credentials are not inherited.
- Process concurrency is one. The worker enforces timeouts, bounded captured output, run-rate limits, and cancellation. A persistent lock prevents concurrent application runs.
- Backend code is copied before test execution so build output never writes to the read-only source mount. The run copy omits targets, VCS metadata, config repo, and IDE/build output.
- Payment/carrier/email integrations must remain mocked or sandboxed in tests. A test is not safe merely because it is named `IntegrationTest`.

## Preflight

Before any test process starts, the worker checks the selected environment, source markers, Maven/Node executables needed by the plan, frontend dependencies when needed, writable runner storage, and at least 300 MB of free space. Failure creates a persistent `BLOCKED` result with its reason. Test run status is not set to `PASSED` if the runner cannot produce parseable test cases.

## Data and cleanup

- QA catalog, audit, run, result, and sanitized log data live in `qa_db`; business tables are not used for run history.
- The test child process receives no service database credentials. Existing Spring test profiles and in-memory/mocked dependencies are used by the checked-in tests; every newly registered integration test must be reviewed for isolation before it is added.
- Run workspaces are generated from a server-created UUID and constrained below the configured QA work root. Cleanup runs after terminal persistence; a service restart marks orphaned active runs `BLOCKED` and clears the active lock.
- Persistent test history and sanitized result evidence are retained in `qa_db`; workspace binaries and temporary reports are deleted when processing finishes.

## Local deployment requirements

Compose requires PostgreSQL, config server, and discovery server for the QA service. Fresh databases create `qa_db` through `devops/init-dbs.sql`; an existing PostgreSQL volume needs that database created once before `qa-service` starts. The runtime image includes Maven and Node. The QA service port is internal to the Compose network and is routed through the authenticated gateway.
