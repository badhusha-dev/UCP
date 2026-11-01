# Universal Commerce Platform (UCP)

[![Platform Version](https://img.shields.io/badge/version-2.0.0--enterprise-blue.svg)](RELEASE_NOTES_v2.0.0.md)
[![Java](https://img.shields.io/badge/java-17%20%7C%2021%20LTS-orange.svg)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/spring--boot-3.2.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/spring--cloud-2023.0.1-green.svg)](https://spring.io/projects/spring-cloud)
[![React](https://img.shields.io/badge/react-18.3.1-61dafb.svg)](https://react.dev)
[![Docker Containers](https://img.shields.io/badge/docker%20containers-23%2F23%20UP-success.svg)](docker-compose.yml)
[![Cluster Health](https://img.shields.io/badge/cluster%20health-100%25%20SLO-brightgreen.svg)](scripts/health-check.sh)
[![License](https://img.shields.io/badge/license-Proprietary%20%2F%20Enterprise-lightgrey.svg)](#)

---

## 1. Executive Summary

The **Universal Commerce Platform (UCP)** is a cloud-native, highly resilient, distributed e-commerce system built for modern enterprise scale. Engineered with a decoupled **Vite Module Federation Microfrontend** tier and an event-driven **Spring Boot 3.2.4 Microservices** backend, UCP incorporates automated failover, distributed tracing, database schema versioning, non-blocking Kafka dead-letter handling, and complete observability out of the box.

Every service runs in production-hardened Docker containers or Kubernetes pods with validated health endpoints, structured JSON logging, and transactional consistency.

---

## 2. Platform Architecture

```
                          [ Client Browser / Mobile Web ]
                                         │
                 ┌───────────────────────┴───────────────────────┐
                 │                                               │
        [ Storefront Shell :3000 ]                               │
                 │                                               │
      ┌──────────┼──────────┐                                    │
      ▼          ▼          ▼                                    ▼
[ Product MFE ] [ Order MFE ] [ User MFE ]          [ Master API Gateway :8080 ]
  Port: 3001     Port: 3002    Port: 3003                        │
                                         ┌───────────────────────┼───────────────────────┐
                                         ▼                       ▼                       ▼
                                 [ User Service ]       [ Product Service ]     [ Order Service ]
                                    Port: 8081              Port: 8082              Port: 8083
                                         │                       │                       │
                                 ( PostgreSQL )           ( Redis & PG )          ( PostgreSQL )
                                                                                         │
                                                                                         ▼
                                                                                 [ Apache Kafka :9092 ]
                                                                                         │
                                                                                         ▼
                                                                              [ Notification Service :8084 ]
                                                                                         │
                                                                                  ( DLQ & Postgres )
```

---

## 3. Quick Start (One-Command Deployment)

The platform provides cross-platform automated deployment scripts that validate hardware prerequisites, create directories, configure secrets, and orchestrate all 23 containers sequentially.

### Linux / macOS Server
```bash
# 1. Clone repository & enter workspace
git clone <repository-url> ecommerce-platform
cd ecommerce-platform

# 2. Run one-command automated installer
chmod +x scripts/*.sh
./scripts/install.sh --generate-secrets
```

### Windows Server / Desktop (PowerShell)
```powershell
# 1. Enter workspace directory
Set-Location "d:\practical\ecommerce-platform"

# 2. Run one-command automated installer
powershell -ExecutionPolicy Bypass -File ".\scripts\install.ps1" -GenerateSecrets
```

---

## 4. Service & Port Access Directory

| Component | Port | Category | Access URL | Health Endpoint |
| :--- | :--- | :--- | :--- | :--- |
| **Storefront Shell App** | `3000` | Frontend | [http://localhost:3000](http://localhost:3000) | `http://localhost:3000/` |
| **Product Microfrontend** | `3001` | Frontend | [http://localhost:3001](http://localhost:3001) | `http://localhost:3001/assets/remoteEntry.js` |
| **Order Microfrontend** | `3002` | Frontend | [http://localhost:3002](http://localhost:3002) | `http://localhost:3002/assets/remoteEntry.js` |
| **User Microfrontend** | `3003` | Frontend | [http://localhost:3003](http://localhost:3003) | `http://localhost:3003/assets/remoteEntry.js` |
| **Spring Cloud API Gateway**| `8080` | Ingress | [http://localhost:8080](http://localhost:8080) | `http://localhost:8080/actuator/health` |
| **OpenAPI / Swagger UI** | `8080` | Ingress | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | Direct Web UI |
| **User Service** | `8081` | Microservice | Internal `user-service:8081` | `http://localhost:8081/actuator/health` |
| **Product Service** | `8082` | Microservice | Internal `product-service:8082` | `http://localhost:8082/actuator/health` |
| **Order Service** | `8083` | Microservice | Internal `order-service:8083` | `http://localhost:8083/actuator/health` |
| **Notification Service** | `8084` | Microservice | Internal `notification-service:8084`| `http://localhost:8084/actuator/health` |
| **Eureka Discovery Server** | `8761` | Registry | [http://localhost:8761](http://localhost:8761) | `http://localhost:8761/actuator/health` |
| **Spring Cloud Config Server**| `8888` | Config | [http://localhost:8888](http://localhost:8888) | `http://localhost:8888/actuator/health` |
| **PostgreSQL Database** | `5432` | Storage | Internal `postgres:5432` | `pg_isready -p 5432` |
| **Redis Cache** | `6379` | Cache | Internal `redis:6379` | `redis-cli ping` |
| **Kafka Event Broker** | `9092` | Streaming | Internal `kafka:9092` | Internal PLAINTEXT |
| **Kafka UI Dashboard** | `8090` | Operations | [http://localhost:8090](http://localhost:8090) | `http://localhost:8090/actuator/health` |
| **Prometheus TSDB** | `9090` | Observability | [http://localhost:9090](http://localhost:9090) | `http://localhost:9090/-/healthy` |
| **Grafana Dashboards** | `3030` | Observability | [http://localhost:3030](http://localhost:3030) | `http://localhost:3030/api/health` |
| **Alertmanager** | `9093` | Observability | [http://localhost:9093](http://localhost:9093) | `http://localhost:9093/-/healthy` |
| **Zipkin Distributed Tracing**| `9411` | Observability | [http://localhost:9411](http://localhost:9411) | `http://localhost:9411/health` |
| **Elasticsearch** | `9200` | Logging | Internal `elasticsearch:9200` | `http://localhost:9200/_cluster/health` |
| **Logstash Pipeline** | `5000` | Logging | TCP Socket `:5000` | TCP Listener |
| **Kibana Log Viewer** | `5601` | Logging | [http://localhost:5601](http://localhost:5601) | `http://localhost:5601/api/status` |

---

## 5. Local User Accounts

The following is a snapshot of the users in the existing local Docker database on 2026-10-28. It is environment-specific and may differ after a clean install, account changes, or database reset. `Active` is the account's enabled flag; only the Super Admin login was verified in this deployment. An active account does not by itself grant access to every application.

| Name | Email / Login | Role | Account status |
| :--- | :--- | :--- | :--- |
| Admin | `admin` | `ADMIN` | Disabled |
| Admin | `admin@test.com` | `ADMIN` | Disabled |
| Admin | `admin@example.com` | `ADMIN` | Disabled |
| Admin | `admin@ecommerce.com` | `SUPER_ADMIN` | Active |
| Operations Manager | `manager@ecommerce.com` | `MANAGER` | Disabled |
| Sarah Connor | `shopper@ecommerce.com` | `USER` | Disabled |
| Admin | `admin@admin.com` | `ADMIN` | Disabled |
| Admin | `admin@shopifygenie.com` | `ADMIN` | Disabled |
| Customer_security_test | `customer_security_test@ecommerce.com` | `USER` | Disabled |
| John | `john@example.com` | `USER` | Disabled |
| Admin | `admin@ecommerce.local` | `ADMIN` | Disabled |
| Admin User | `admin_test@ecommerce.com` | `ADMIN` | Active |
| Admin OK | `admin_ok@ecommerce.com` | `ADMIN` | Active |

Passwords are intentionally not listed here. The V3 migration disables legacy accounts whose passwords were stored as plaintext; do not use the old sample passwords. For a fresh local deployment, set `SUPER_ADMIN_EMAIL`, `SUPER_ADMIN_PASSWORD` (at least 12 characters), and optionally `SUPER_ADMIN_NAME` in a private `.env` file before starting the User Service. Never commit real credentials.

## 6. Verification & Testing

Verify end-to-end operational readiness using automated test suites:

```bash
# Run Synthetic Health Prober (Tests all 15 active HTTP endpoints)
bash scripts/health-check.sh

# Run Automated E2E Smoke Test (Auth, Catalog, Checkout, Kafka & Tracing)
bash scripts/smoke-test.sh
# or on Windows:
powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1
```

---

## 7. Comprehensive Documentation Directory

| Documentation Suite | Description | Link |
| :--- | :--- | :--- |
| **Assessment & Baselines** | Audit reports, dependency versions, and initial risk registers | [docs/assessment/](docs/assessment/BASELINE_ASSESSMENT.md) |
| **Master Implementation Checklist**| 37-point verified project checklist with concrete evidence | [docs/PROJECT_IMPLEMENTATION_CHECKLIST.md](docs/PROJECT_IMPLEMENTATION_CHECKLIST.md) |
| **Detailed Technical Docs** | 21 in-depth architecture, data, and resilience specifications | [docs/technical/](docs/technical/SYSTEM_ARCHITECTURE.md) |
| **Business Documentation** | Business rules, user journeys, workflows, and personas | [docs/business/](docs/business/BUSINESS_OVERVIEW.md) |
| **Implemented Features** | Exhaustive catalog of verified, active application features | [docs/features/](docs/features/IMPLEMENTED_FEATURES.md) |
| **Admin Analytics Baseline** | Pre-implementation architectural audit & gap analysis | [docs/quality/ADMIN_ANALYTICS_BASELINE.md](docs/quality/ADMIN_ANALYTICS_BASELINE.md) |
| **Analytics Metric Dictionary** | Canonical definitions, status inclusions/exclusions, mathematical formulas | [docs/quality/ANALYTICS_METRIC_DICTIONARY.md](docs/quality/ANALYTICS_METRIC_DICTIONARY.md) |
| **Admin Analytics Implementation** | Executive KPI engine, SVG time-series, inventory valuation, AI Copilot | [docs/quality/ADMIN_ANALYTICS_IMPLEMENTATION.md](docs/quality/ADMIN_ANALYTICS_IMPLEMENTATION.md) |
| **Analytics Test Matrix** | 96/96 automated tests verified across backend and frontend | [docs/quality/ADMIN_ANALYTICS_TEST_MATRIX.md](docs/quality/ADMIN_ANALYTICS_TEST_MATRIX.md) |
| **Performance & Frugal Architecture** | B-tree indexing plans, PostgreSQL aggregates, query caps | [docs/quality/ADMIN_ANALYTICS_PERFORMANCE.md](docs/quality/ADMIN_ANALYTICS_PERFORMANCE.md) |
| **Security & Formula Injection Review** | OWASP CSV injection defense, server-side RBAC, privacy safeguards | [docs/quality/ADMIN_ANALYTICS_SECURITY_REVIEW.md](docs/quality/ADMIN_ANALYTICS_SECURITY_REVIEW.md) |
| **Analytics Deployment Guide** | Migration execution, environment variables, and telemetry endpoints | [docs/quality/ADMIN_ANALYTICS_DEPLOYMENT.md](docs/quality/ADMIN_ANALYTICS_DEPLOYMENT.md) |
| **Future Features & Roadmap** | Strategic backlog, technical debt, and RFC proposals | [docs/future/](docs/future/FUTURE_FEATURES.md) |
| **Deployment & Server Setup** | Clean server installation, Docker Compose, and Kubernetes | [docs/deployment/](docs/deployment/INSTALLATION_GUIDE.md) |
| **Operations Runbooks** | Backup, recovery, synthetic monitoring, and troubleshooting | [docs/operations/](docs/operations/OPERATIONS_RUNBOOK.md) |
| **Security & Quality Gates** | Threat modeling, secrets management, and testing reports | [docs/security/](docs/security/SECURITY_CHECKLIST.md) |
| **Handover Package** | Team onboarding, operational transitions, and completion report | [docs/handover/](docs/handover/PROJECT_HANDOVER.md) |
| **Mermaid Architecture Diagrams** | 14 editable `.mmd` diagrams modeling the full platform | [docs/diagrams/](docs/diagrams/) |
