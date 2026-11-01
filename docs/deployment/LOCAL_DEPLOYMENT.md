# Local Deployment (lean core)

How the core of the platform was run on one machine with Docker Compose, in a memory budget of about 8 GB. Results of the first run are in [../completion/DEPLOYMENT_REPORT.md](../completion/DEPLOYMENT_REPORT.md). This is the path that was run; it is not a production procedure.

## Prerequisites

- Docker Desktop with the Linux engine running, Compose v2, and 8 GB or more of memory for Docker.
- Java 17 (the Maven wrapper pins Maven 3.6.3) and Node.js with npm.
- A `.env` at the repository root (copy `.env.example`). It must define `POSTGRES_PASSWORD`, `JWT_SECRET`, `GRAFANA_ADMIN_PASSWORD` and `DELIVERY_OTP_PEPPER`.
- Free host ports: 3000, 3001, 3002, 3003, 3005, 3013, 8080, 8761, 8888, 9092, 2181, 6379 and 15432.

## 1. Secrets file (git-ignored)

Create `scripts/dev-seed/deploy-local.env`. The `scripts/dev-seed/*.env` pattern is already ignored. It needs:

- `SERVICE_TO_SERVICE_TOKEN`: a long random string. Compose refuses to start without it.
- `SUPER_ADMIN_EMAIL` and `SUPER_ADMIN_PASSWORD` (12 or more characters): on an empty database user-service creates the first Super Admin from these.
- `JAVA_OPTS`: for example `-Xms128m -Xmx384m -XX:+UseG1GC` to fit 8 GB.

Never commit this file. Compose reads it after `.env`, so its values win.

## 2. Build

```bash
cd backend
./mvnw -B -DskipTests package -pl config-server,discovery-server,api-gateway,user-service,product-service,order-service,cart-service,inventory-service,stock-service -am
```

Backend images copy the built jar, so build the jars first. Frontend images build from source. Build them **one at a time**; two parallel `npm install` runs timed out:

```bash
export COMPOSE_PARALLEL_LIMIT=1
docker compose -p ucp --env-file .env --env-file scripts/dev-seed/deploy-local.env build shell-app
```

Repeat for `mfe-product`, `mfe-order`, `mfe-user`, `mfe-cart` and `mfe-super-admin`.

## 3. Start, in stages

Use the same `docker compose -p ucp --env-file .env --env-file scripts/dev-seed/deploy-local.env` prefix, with `up -d --no-deps`:

1. `postgres redis zookeeper kafka`
2. `config-server discovery-server` (wait until both are healthy)
3. `user-service product-service order-service cart-service inventory-service stock-service`
4. `api-gateway`
5. `shell-app mfe-product mfe-order mfe-user mfe-cart mfe-super-admin`

`--no-deps` matters: the gateway's dependency list would otherwise start four more JVMs.

## 4. Check

- `http://localhost:8080/actuator/health` returns `UP`.
- `http://localhost:8761` shows the registered services.
- `http://localhost:3000` serves the shell, and `http://localhost:3000/api/products` returns the catalog through the shell's nginx proxy.
- Super Admin sign-in: `POST http://localhost:8080/api/users/login` with the email and password from the secrets file.

## 5. Stop

`stop` keeps data. `down` removes containers and keeps volumes. `down -v` also deletes the `ucp_*` data volumes; it is irreversible.

## Known limits

Observability (Zipkin, Prometheus, Grafana, ELK) and nine services are not started in this mode, so services log Zipkin export errors. To add a service, start it with `--no-deps` after checking memory with `docker stats --no-stream`.
