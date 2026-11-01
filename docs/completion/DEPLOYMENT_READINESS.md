# Deployment Readiness

Verdict: **not production ready.** Nothing in this session was deployed; the Docker daemon was not running.

| Area | State | Blocking? |
|---|---|---|
| docker-compose | Full stack defined; requires `JWT_SECRET`, `DELIVERY_OTP_PEPPER` and (new) `SERVICE_TO_SERVICE_TOKEN` in `.env`. The local `.env` lacks `SERVICE_TO_SERVICE_TOKEN`, so `docker compose up` now stops with a clear message until it is set. | Yes, until `.env` is updated |
| Kubernetes | Manifests for 5 services + gateway + config-server; 10 services missing; `02-secrets.yml` git-ignored | Yes |
| CI | Now matrix-tests all 18 backend modules and builds all 15 frontends, plus frontend unit tests on Node 22 (edited this session, not yet run on GitHub) | No |
| Migrations | delivery V1/V2 and inventory V4 never applied to Postgres | Yes |
| Secrets | dev DB passwords in config-repo; Vault token defaults to `root` in prod profile | Yes for prod |
| Frontend config | hard-coded `http://localhost:8080` API bases in mfe-order/mfe-product | Yes for any non-local deploy |
| Payment | no Payment service | Yes for real checkout |

Required `.env` additions before the next `docker compose up`:
```
SERVICE_TO_SERVICE_TOKEN=<openssl rand -hex 32>
```
