# Server Installation Guide (Automated & Manual)

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Automated Installation (Recommended)

### Linux Server (Ubuntu 22.04 LTS / RHEL 9)
```bash
# 1. Clone the repository
git clone <repo-url> ecommerce-platform
cd ecommerce-platform

# 2. Grant execution permissions
chmod +x scripts/*.sh

# 3. Execute one-command installer
./scripts/install.sh --generate-secrets
```

### Windows Server / Desktop (PowerShell)
```powershell
Set-Location "d:\practical\ecommerce-platform"
powershell -ExecutionPolicy Bypass -File ".\scripts\install.ps1" -GenerateSecrets
```

---

## 2. Manual Step-by-Step Installation Procedure

If automated scripts cannot be executed due to restricted shell policies:

### Step 1: Pre-flight Verification
- Ensure Docker Engine 24+ and Docker Compose v2 are active:
  ```bash
  docker --version && docker compose version
  ```

### Step 2: Configure Environment
- Copy configuration template:
  ```bash
  cp .env.example .env
  ```
- Edit `.env` to set unique cryptographic values for `JWT_SECRET`, `POSTGRES_PASSWORD`, and `GRAFANA_ADMIN_PASSWORD`.

### Step 3: Launch Infrastructure Tier
```bash
docker compose up -d postgres redis zookeeper kafka
# Wait 10 seconds for databases and Kafka broker to initialize
sleep 10
```

### Step 4: Launch Platform Services
```bash
docker compose up -d discovery-server config-server
# Wait 15 seconds for Eureka and Spring Cloud Config
sleep 15
```

### Step 5: Launch Domain Microservices & Edge Gateway
```bash
docker compose up -d user-service product-service order-service notification-service api-gateway
```

### Step 6: Launch Frontend MFEs & Observability Stack
```bash
docker compose up -d shell-app mfe-product mfe-order mfe-user \
    prometheus grafana alertmanager zipkin kafka-ui elasticsearch logstash kibana
```

### Step 7: Run Smoke Test Validation
```bash
bash scripts/smoke-test.sh
```
