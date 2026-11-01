# Production Upgrade & Maintenance Guide

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Zero-Downtime Rolling Upgrade Strategy

To apply platform updates without interrupting active shoppers:

### Step 1: Automated Pre-Upgrade Backup
Always create a complete snapshot of all four PostgreSQL databases before initiating any code or configuration change:
```bash
bash scripts/backup.sh
```

### Step 2: Microfrontend Rolling Update
Frontend MFEs (`shell-app`, `mfe-product`, `mfe-order`, `mfe-user`) run as stateless NGINX containers. They can be updated independently:
```bash
docker compose build mfe-product
docker compose up -d --no-deps mfe-product
```

### Step 3: Backend Microservice Rolling Update
1. Build the updated microservice image.
2. In Kubernetes, apply rolling update deployment:
   ```bash
   kubectl set image deployment/order-service order-service=ecommerce/order-service:2.1.0 -n ecommerce
   ```
3. Kubernetes keeps old pods serving traffic until new pods pass their `/actuator/health` readiness probe.

### Step 4: Health & Smoke Test Verification
```bash
bash scripts/smoke-test.sh
```
