# Architecture Verification

| Documented element (docs/architecture, docs/technical) | Verified in code? | Notes |
|---|---|---|
| Spring Cloud Gateway as single entry point | Yes | `api-gateway`, routes in `config-repo/api-gateway.yml` |
| Eureka discovery + Config Server | Yes | `discovery-server`, `config-server`, `config-repo/` |
| Database per service | Partly | accounting-service has its own `accounting_db` but keeps a synced read model of order tables (`orders`, `order_items`, …); the returns report records a past `refund_requests` name clash; stock-service reads a legacy DB (`STOCK_LEGACY_DB_*`) |
| Kafka event bus (`order-events`, outbox) | Yes for order→notification, delivery outbox, cart/loyalty events | consumers not tenant-aware |
| Redis caching | Configured in compose | not re-verified this session |
| Module Federation frontends | Yes | 14 MFEs + shell; several MFEs hard-code `http://localhost:8080` |
| Multi-tenant SaaS (docs/saas) | Partly | 5 of 15 services tenant-scoped; gateway now enforces the boundary for tenant tokens |
| Service-to-service auth | Shared static token | no mTLS; token default is public (compose now requires it) |
| Observability (Zipkin, Prometheus, ELK) | Configured in compose | not exercised this session (Docker daemon off) |
| Kubernetes deployment | Partly | manifests for user, product, order, notification, delivery services + gateway + config-server; the other 10 services have none |

Decision recorded: tenant isolation for header-trusting services is enforced at the gateway (fail closed for tenant tokens)
as an interim control; the target remains per-service JWT verification + `tenant_id` predicates, because a gateway-only
control is bypassed by anything that can reach the service network.
