# Operations Master Runbook

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Routine Operational Procedures

### Daily Operations Checklist
1. **Health Probing**: Run `bash scripts/health-check.sh` every morning to ensure all 15 synthetic endpoints return HTTP 200.
2. **Grafana Dashboards**: Review JVM heap utilization, active DB connection pools, and HTTP 5xx error rates at `http://localhost:3030`.
3. **Kafka Lag**: Review consumer lag on `order-events` via Kafka UI at `http://localhost:8090`.
4. **DLQ Review**: Verify that the Dead Letter Queue count is zero (`GET /api/notifications/dlq`).

---

## 2. Common Administrative Actions

### Restarting Degraded Containers
```bash
# Graceful restart of API Gateway
docker compose restart api-gateway

# Graceful restart of Kafka Broker
docker compose restart kafka
```

### Rotating Database Credentials
1. Update `POSTGRES_PASSWORD` in `.env`.
2. Update database password inside PostgreSQL container using `ALTER USER`.
3. Perform rolling restart of backend microservices.
