# Production Rollback & Recovery Guide

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Emergency Rollback Triggers

Initiate immediate rollback if any of the following occur post-deployment:
1. `scripts/health-check.sh` reports less than 100% endpoint availability.
2. Checkout error rates exceed 1.0% in Prometheus/Grafana.
3. Database migrations fail to apply cleanly.
4. Response latency on `/api/products` or `/api/orders/checkout` spikes above 1000ms.

---

## 2. Automated Rollback Execution

The platform includes an automated rollback script that halts domain services and restores the prior database state:

```bash
# Roll back to the latest pre-update database snapshot
bash scripts/rollback.sh

# Or specify a target snapshot directory explicitly
bash scripts/rollback.sh devops/backups/backup_20261022_120000
```

---

## 3. Kubernetes Rollback Procedure

```bash
# Rollback Order Service deployment to previous revision
kubectl rollout undo deployment/order-service -n ecommerce

# Check rollout status
kubectl rollout status deployment/order-service -n ecommerce
```
