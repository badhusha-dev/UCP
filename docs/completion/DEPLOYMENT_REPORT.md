# Deployment Report: local Docker Compose

Deployed 2026-11-01 from branch `feature/ucp-completion-modernization` at commit `cdbc7621`, which includes the user-service B-38 fix (`f216d479`), to this machine only. Nothing was deployed to a shared, staging or production environment.

## Scope

A lean core of the platform, not the full stack. Docker Desktop has **7.62 GiB** of memory, and the full 46-service Compose stack (Elasticsearch, Kibana, Logstash, about 19 JVMs) does not fit. Compose has no profiles, and the API gateway declares 10 services as dependencies, so services were started explicitly with `--no-deps`, in stages.

| Running (19 containers, about 3.9 GiB) | Port on this machine |
|---|---|
| api-gateway | 8080 |
| shell-app (storefront shell) | 3000 |
| mfe-product, mfe-order, mfe-user, mfe-cart, mfe-super-admin | 3001, 3002, 3003, 3005, 3013 |
| user-service, product-service, order-service, cart-service, inventory-service, stock-service | internal only |
| config-server, discovery-server | 8888, 8761 |
| postgres, redis, zookeeper, kafka | 15432, 6379, 2181, 9092 (all 127.0.0.1) |

`ucp-test-pg` is the unrelated scratch Postgres used for the tests; it is not part of this deployment.

**Not deployed:** accounting, analytics, crm, delivery, error-stack, loyalty, notification, procurement and qa services; the other nine frontends; Zipkin, Prometheus, Grafana, Alertmanager, the ELK stack, Vault and Kafka UI.

## Data safety

Compose has no `name:`, so the project is `ucp` (the directory name) and it created fresh volumes `ucp_postgres_data` and `ucp_redis_data`. The older `ecommerce-platform_postgres_data` volume (248 MB) and the volumes of unrelated projects were not mounted or modified. Your `.env` was not edited; local secrets live in `scripts/dev-seed/deploy-local.env`, which is git-ignored (`.gitignore` line 27).

## Verification (all run in this pass)

| Check | Result |
|---|---|
| Postgres, Redis, Kafka, config-server, discovery-server | healthy |
| 15 databases created by `devops/init-dbs.sql` | all present |
| Services registered in Eureka | 8: api-gateway, config-server, user, product, order, cart, inventory, stock |
| Gateway `/actuator/health` | UP |
| Flyway from scratch on Postgres | user 16, product 8, order 9, inventory 5, stock 4, cart 2 applied; 0 failed; counts equal the files in the repository |
| Platform login through the gateway | 200 with a token; a wrong password gets 401 |
| Six frontends on their ports | all return 200 |
| `GET :3000/api/products` through the shell's nginx proxy | 200, 3 seeded products |
| Customer registration, login, checkout of product 1 | 201, 200, 200: order 1 `PLACED`, total 199.99 (the catalog price), for user 4 in the legacy tenant |
| Outbox | the `ORDER_CREATED` row was `PUBLISHED`; the message is on the `order-events` topic and carries `tenantId` (first run against a live broker) |
| Postgres restart | order and user rows still present; login and catalog work again after reconnect |
| Logs of the six services, gateway, config and discovery | 0 `ERROR` lines other than Zipkin export failures |

## Not verified

- **No browser run.** The frontends were fetched over HTTP; no page was rendered, no UI login or checkout was clicked, and the storefront changes from SF-01 were not exercised in a browser.
- **Stock effect of checkout.** After the order, `stock_db.stock_reservation` had 0 rows. Whether the stock reservation happens through events, later, or not on this path was not investigated.
- **Notifications and payments.** notification-service was not running, and no payment service exists.
- **The nine undeployed services** and the nine other frontends.
- **Entity validation at startup.** The services started, but whether `ddl-auto: validate` is active in the Docker profile was not checked.

## Problems found while deploying

| # | Problem | Handling |
|---|---|---|
| 1 | `.env` has no `SERVICE_TO_SERVICE_TOKEN`, which Compose requires. | Added to the git-ignored `scripts/dev-seed/deploy-local.env` with the Super Admin bootstrap credentials and a smaller `JAVA_OPTS` (`-Xmx384m`). |
| 2 | Frontend images failed with `npm install ETIMEDOUT` when two builds ran in parallel. The images resolve dependencies fresh with no lockfile (TD-08). | Retried one image at a time with `COMPOSE_PARALLEL_LIMIT=1`; all six built. |
| 3 | The gateway lists 10 dependencies, so `docker compose up api-gateway` would start four extra services. | `--no-deps` and explicit service lists. |
| 4 | `docker compose up` aborted once with "dependency stock-service failed to start" after a Docker Desktop API 500. product-service was created but not started. | Started product-service again. No data effect. |
| 5 | Eleven services have a hard-coded `container_name: ecommerce-platform-...` (for example `mfe-cart`). The container ignores the project name. | Left as is. It would collide with an older stack of the same names. Cleanup candidate. |
| 6 | Services log `ERROR ... zipkin: Name does not resolve` when Zipkin is not started. | Expected in this lean mode. |
| 7 | The login response has no user `id` field, so a client cannot learn its own ID from login. | Noted only. |
| 8 | A one-off `alpine` image was pulled from Docker Hub to size a volume. | Small utility image; no effect on the deployment. |

## Stopping and removing

- Stop, keep data: `docker compose -p ucp --env-file .env --env-file scripts/dev-seed/deploy-local.env stop`
- Remove containers, keep data: the same command with `down`
- **Remove data as well:** `down -v` deletes `ucp_postgres_data` and `ucp_redis_data`. Do not run it unless you want an empty database.

See [../deployment/LOCAL_DEPLOYMENT.md](../deployment/LOCAL_DEPLOYMENT.md) for the start sequence.
