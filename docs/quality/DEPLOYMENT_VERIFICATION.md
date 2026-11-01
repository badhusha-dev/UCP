# Universal Commerce Platform - Deployment & Environment Verification Guide

**Document Version:** 2.0.0  
**Verification Date:** October 23, 2026  
**Auditor:** Principal DevOps & Site Reliability Engineer  
**Scope:** Bare-metal, Docker Compose, and Production Server Readiness  

---

## 1. System Requirements & Baseline Prerequisites

Before initiating platform deployment on a clean server, ensure the target host satisfies the minimum operating specifications:

### 1.1 Hardware Specifications
- **CPU:** 4 vCPUs minimum (8 vCPUs recommended for full container stack)
- **RAM:** 8 GB RAM minimum (16 GB recommended)
- **Storage:** 25 GB free disk space (SSD recommended)

### 1.2 Software Dependencies
| Component | Minimum Version | Verified Version | Purpose |
| :--- | :--- | :--- | :--- |
| **Operating System** | Linux (Ubuntu 22.04+ / RHEL 9+) or Windows 10/11 Server | Ubuntu 22.04 LTS / Windows 11 | Host operating system |
| **Java Development Kit** | OpenJDK 17 LTS / 21 LTS | Eclipse Temurin 17.0.11 / 21.0.2 | Microservices execution |
| **Node.js & npm** | Node 20 LTS / npm 10+ | Node.js v24.5.0 / npm 11.1.0 | Microfrontends compilation |
| **Docker Engine** | Docker 24.0+ | Docker Engine 29.8.0 | Containerized execution |
| **Docker Compose** | Compose v2.20+ | Docker Compose v5.5.1 | Multi-container orchestration |

---

## 2. Port & Service Topology

The Universal Commerce Platform allocates distinct, non-conflicting network ports across its microservices and microfrontends:

### 2.1 Microfrontends (Served via Nginx)
| Application | Internal Port | Host Port | Role | Entry URL |
| :--- | :---: | :---: | :--- | :--- |
| `shell-app` | 80 | **3000** | Root Storefront Host & Navigation Shell | `http://localhost:3000` |
| `mfe-product` | 80 | **3001** | Product Catalog & Inventory Remote MFE | `http://localhost:3001` |
| `mfe-order` | 80 | **3002** | Cart, Checkout & Order Remote MFE | `http://localhost:3002` |
| `mfe-user` | 80 | **3003** | Authentication, Profile & Accounting MFE | `http://localhost:3003` |

### 2.2 Microservices & Core Infrastructure
| Service | Host Port | Protocol | Discovery / Routing ID | Health Endpoint |
| :--- | :---: | :---: | :--- | :--- |
| **API Gateway** | **8080** | HTTP | `api-gateway` | `http://localhost:8080/actuator/health` |
| **User Service** | **8081** | HTTP | `user-service` | `http://localhost:8081/actuator/health` |
| **Product Service** | **8082** | HTTP | `product-service` | `http://localhost:8082/actuator/health` |
| **Order Service** | **8083** | HTTP | `order-service` | `http://localhost:8083/actuator/health` |
| **Notification Service** | **8084** | HTTP | `notification-service` | `http://localhost:8084/actuator/health` |
| **Eureka Discovery** | **8761** | HTTP | `discovery-server` | `http://localhost:8761/eureka/apps` |
| **Config Server** | **8888** | HTTP | `config-server` | `http://localhost:8888/actuator/health` |
| **PostgreSQL 16** | **5432** | TCP | `postgres` | `pg_isready -h localhost -p 5432` |
| **Redis 7** | **6379** | TCP | `redis` | `redis-cli ping` |
| **Kafka Broker** | **9092** | TCP | `kafka` | Kafka admin client readiness |
| **Prometheus** | **9090** | HTTP | `prometheus` | `http://localhost:9090/-/healthy` |
| **Grafana** | **3030** | HTTP | `grafana` | `http://localhost:3030/api/health` |
| **Zipkin** | **9411** | HTTP | `zipkin` | `http://localhost:9411/health` |

---

## 3. One-Command Clean Server Installation

The platform provides fully automated installation scripts that handle environment inspection, dependency resolution, database initialization, container rollout, and health verification.

### 3.1 Linux / macOS Installation
```bash
# 1. Clone repository or unpack release archive
git clone https://github.com/ecommerce/ecommerce-platform.git
cd ecommerce-platform

# 2. Grant execution permissions and run installer
chmod +x scripts/install.sh
./scripts/install.sh
```

### 3.2 Windows Server (PowerShell) Installation
```powershell
# 1. Open PowerShell as Administrator in project root
cd d:\practical\ecommerce-platform

# 2. Execute automated installation script
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\install.ps1
```

### 3.3 What the Installer Executes Automatically:
1. **Pre-flight Checks:** Verifies Docker, Docker Compose, network ports, and available memory.
2. **Environment Setup:** Copies `.env.example` to `.env` if not present and generates secure random tokens.
3. **Database Initialization:** Starts PostgreSQL and runs Flyway migrations across all schemas (`user_db`, `product_db`, `order_db`, `notification_db`).
4. **Service Discovery & Config:** Boots Eureka Service Registry and Spring Cloud Config Server.
5. **Adaptive Readiness Polling:** Periodically probes `http://localhost:8761/eureka/apps` and `http://localhost:8080/actuator/health` every 5 seconds until healthy.
6. **Microservices & MFEs Boot:** Boots all downstream Spring Boot microservices and Nginx MFE containers.
7. **Synthetic Verification:** Runs end-to-end smoke tests validating user login, product catalog, cart addition, and checkout.

---

## 4. Manual Verification & Health Checks

For auditing or troubleshooting, each tier can be independently verified using curl or PowerShell:

### 4.1 Automated Health Check Probe
Execute the pre-packaged synthetic health probe:
```powershell
powershell -ExecutionPolicy Bypass -File devops/scripts/probe-synthetic-health.ps1
```
Or in Bash:
```bash
./devops/scripts/probe-synthetic-health.sh
```

### 4.2 Core Service Health Endpoints
```bash
# 1. Gateway Health
curl -s http://localhost:8080/actuator/health | jq .

# 2. Eureka Discovery Apps Registry
curl -s -H "Accept: application/json" http://localhost:8761/eureka/apps | jq .

# 3. Microservice Individual Health Probes
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8082/actuator/health
curl -s http://localhost:8083/actuator/health
curl -s http://localhost:8084/actuator/health
```

### 4.3 Microfrontend Asset Verification
```bash
# Shell Storefront
curl -I http://localhost:3000/

# Product MFE Remote Entry
curl -I http://localhost:3001/assets/remoteEntry.js

# Order MFE Remote Entry
curl -I http://localhost:3002/assets/remoteEntry.js

# User MFE Remote Entry
curl -I http://localhost:3003/assets/remoteEntry.js
```

---

## 5. Troubleshooting & Common Operational Scenarios

### Scenario A: Eureka Registration Lag
- **Symptom:** Gateway returns `503 Service Unavailable` immediately after startup.
- **Cause:** Spring Cloud Eureka clients register with a 30-second heartbeat interval; downstream services may require up to 45 seconds to become visible in the routing table.
- **Resolution:** The updated installer script now waits for Eureka heartbeats automatically. For manual startup, verify registration via `http://localhost:8761`.

### Scenario B: Database Connection Timeout
- **Symptom:** Backend microservices log `Connection to localhost:5432 refused`.
- **Cause:** PostgreSQL container taking longer to initialize migrations on initial start.
- **Resolution:** Verify container state via `docker compose ps postgres`. Ensure `POSTGRES_USER` and `POSTGRES_PASSWORD` in `.env` match Spring Boot datasource credentials.

### Scenario C: Port Collision
- **Symptom:** Docker fails to bind port `8080`, `5432`, or `3000`.
- **Cause:** Existing process (e.g. local PostgreSQL or IIS/Apache) occupying port.
- **Resolution:**
  ```powershell
  Get-NetTCPConnection -LocalPort 8080, 5432, 3000 -ErrorAction SilentlyContinue | Select-Object LocalPort, OwningProcess
  ```
  Terminate conflicting PID or adjust port bindings in `.env`.

---

## 6. Production Release Packaging

To build and package a standalone production release archive:
```powershell
powershell -ExecutionPolicy Bypass -File devops/scripts/package-release.ps1
```
This produces:
- `release/ecommerce-platform-v2.0.0-prod.zip`
- `release/ecommerce-platform-v2.0.0-prod.zip.sha256`
- Verification manifest containing full dependency licenses, changelogs, and architecture blueprints.
