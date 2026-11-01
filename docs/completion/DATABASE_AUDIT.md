# Database Audit

Engine: PostgreSQL (compose `postgres`, host port 15432). Schema management: Flyway per service (`ddl-auto: validate` on
services that were checked in earlier reports). Applied migrations were **not edited** in this session.

| Service | Flyway files | Tables (CREATE TABLE count) | tenant_id | Notes |
|---|---|---|---|---|
| user-service | V1–V12 | 15 | tenancy tables | V8–V12 are uncommitted (earlier sessions); V1–V12 applied on real Postgres per returns report |
| product-service | V1–V8 | 31 | V6–V8 | V7/V8 applied on live DB per storefront report |
| order-service | V1, V4–V10 | 34 | V8+ | V9/V10 applied live per returns report; `orders.tenant_id` default = legacy tenant |
| accounting-service | 13 (V1, V4, V5, V10–V19) | 21 | V19 (12 tables) | own `accounting_db` with a synced copy of order tables; postings immutable only by application code (reversal = compensating entry; no DB trigger) |
| inventory-service | V1–V4 | 13 | yes | V4 new from sibling session, not applied anywhere yet |
| delivery-service | V1–V2 | 21 | yes | **never run on Postgres** (H2 PostgreSQL mode only); partial unique index untested |
| crm-service | 2 | 13 | none | |
| procurement-service | 1 | 9 | none | sibling adding V2 |
| qa-service | 1 | 9 | none | |
| analytics-service | 4 | 6 | none | |
| stock-service | 3 | 5 | none | |
| cart, loyalty, error-stack, notification | 1–2 each | 2–3 each | none | |

Risks
1. accounting-service keeps a synced copy of order tables (and history of V1/V4/V5 order migrations); a name collision with order-service already happened
   (`refund_requests`, see returns report).
2. No migration has been run against Postgres for delivery V1/V2 or inventory V4 — must be done before deployment.
3. Tenant backfill plan needed for every service that will gain `tenant_id` (default legacy tenant, then NOT NULL).
4. Backups: `kubernetes/database/backup-cronjob.yml`, `scripts/backup.sh` exist; restore not tested this session.
