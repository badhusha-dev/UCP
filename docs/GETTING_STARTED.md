# Getting Started Guide

**Universal Commerce Platform**  
*Document Version:* 2.0.0  
*Target Audience:* New Developers, DevOps Engineers, and SREs

---

## 1. Prerequisites & Tooling

To run or develop on the Universal Commerce Platform, the following tools are required:

| Prerequisite | Minimum Version | Recommended Version | Verification Command |
| :--- | :--- | :--- | :--- |
| **Operating System**| Linux (Ubuntu 22.04+), macOS 13+, or Windows 11 | Ubuntu 22.04 LTS / Windows 11 | `uname -a` or `winver` |
| **Docker Engine** | 24.0.0+ | 27.x+ | `docker --version` |
| **Docker Compose** | 2.20.0+ | 2.29.x+ | `docker compose version` |
| **Java JDK** | 17 LTS | Eclipse Temurin 17 / 21 LTS | `java -version` |
| **Node.js** | 18 LTS | Node.js 20.x or 22.x LTS | `node --version` |
| **Hardware** | 8 GB RAM, 4 CPU Cores, 20 GB Disk | 16 GB RAM, 6+ CPU Cores, 40 GB SSD | Checked via validation script |

---

## 2. Quick Setup & One-Command Installation

### 2.1 Automated Installation
Run the installer script for your operating system:

```bash
# On Linux / macOS
chmod +x scripts/*.sh
./scripts/install.sh --generate-secrets

# On Windows (PowerShell Administrator or standard shell)
powershell -ExecutionPolicy Bypass -File ".\scripts\install.ps1" -GenerateSecrets
```

The script will automatically:
1. Verify operating system, RAM, CPU cores, disk space, and open ports.
2. Initialize `.env` configuration and generate cryptographic JWT and database secrets.
3. Validate `docker-compose.yml` syntax.
4. Launch the cluster in ordered phases (Databases -> Brokers -> Platform -> Microservices -> Observability).
5. Run the synthetic health prober across all 15 endpoints.

---

## 3. Running Services Locally for Development

If developing locally without Docker containers for domain services:

### 3.1 Start Backing Services (Infra Only)
```bash
docker compose up -d postgres redis zookeeper kafka discovery-server config-server
```

### 3.2 Run Backend Microservices
In separate terminal tabs or your preferred IDE (IntelliJ IDEA):
```bash
# Start User Service
cd backend/user-service && mvn spring-boot:run

# Start Product Service
cd backend/product-service && mvn spring-boot:run

# Start Order Service
cd backend/order-service && mvn spring-boot:run

# Start Notification Service
cd backend/notification-service && mvn spring-boot:run

# Start API Gateway
cd backend/api-gateway && mvn spring-boot:run
```

### 3.3 Run Frontend Microfrontends
```bash
cd frontend
npm install

# Start Product MFE (Port 3001)
cd mfe-product && npm run dev

# Start Order MFE (Port 3002)
cd ../mfe-order && npm run dev

# Start User MFE (Port 3003)
cd ../mfe-user && npm run dev

# Start Shell App (Port 3000)
cd ../shell-app && npm run dev
```

---

## 4. Verification & Smoke Testing

Run the automated smoke test script to verify end-to-end functionality:
```bash
# Linux/macOS
bash scripts/smoke-test.sh

# Windows PowerShell
powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1
```
