# Project Structure & Repository Organization

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. High-Level Directory Layout

```
ecommerce-platform/
├── .github/                   # Continuous Integration & Delivery workflows
│   └── workflows/             # GitHub Actions CI/CD pipelines
├── backend/                   # Spring Boot Microservices monorepo root
│   ├── api-gateway/           # Spring Cloud Gateway (Port 8080)
│   ├── config-repo/           # Centralized Git configuration profiles
│   ├── config-server/         # Spring Cloud Config Server (Port 8888)
│   ├── discovery-server/      # Netflix Eureka Service Registry (Port 8761)
│   ├── notification-service/  # Event consumer, DLQ & notification dispatch (Port 8084)
│   ├── order-service/         # Order state machine & checkout (Port 8083)
│   ├── product-service/       # Catalog & stock management (Port 8082)
│   ├── user-service/          # Authentication & user profile (Port 8081)
│   └── pom.xml                # Parent Maven POM with dependency management
├── frontend/                  # React Vite Microfrontends workspace
│   ├── shared-library/        # Shared TypeScript contracts & utility types
│   ├── shell-app/             # Host application & navigation shell (Port 3000)
│   ├── mfe-product/           # Product catalog remote MFE (Port 3001)
│   ├── mfe-order/             # Cart & checkout remote MFE (Port 3002)
│   ├── mfe-user/              # Authentication & user profile remote MFE (Port 3003)
│   └── package.json           # Root npm workspace with React 18.3.1 overrides
├── devops/                    # Operational tooling, infrastructure & runbooks
│   ├── backups/               # Database backup snapshots and manifests
│   ├── certs/                 # TLS / SSL certificates and generation tools
│   ├── grafana/               # Grafana dashboard definitions and datasources
│   ├── helm/                  # Production Helm 3 umbrella deployment charts
│   ├── k8s/                   # Raw Kubernetes manifests (StatefulSets, Ingress, HPA)
│   ├── load-testing/          # K6 & JMeter performance load test scenarios
│   ├── logstash/              # Logstash pipeline and configuration
│   ├── prometheus/            # Prometheus scrape targets and alerting rules
│   ├── runbooks/              # Production operations and disaster recovery runbooks
│   ├── scripts/               # Specialized operational verification scripts
│   ├── terraform/             # Terraform infrastructure-as-code for AWS EKS/RDS
│   └── vault/                 # HashiCorp Vault secrets management configs
├── docs/                      # Comprehensive platform documentation suite
│   ├── assessment/            # Initial baseline audits and risk registers
│   ├── business/              # Business rules, user journeys and workflows
│   ├── deployment/            # Server installation, Docker Compose & K8s guides
│   ├── diagrams/              # Editable Mermaid architecture models (.mmd)
│   ├── features/              # Implemented features catalog and limitations
│   ├── future/                # Product roadmap, technical debt & backlog
│   ├── handover/              # Engineering onboarding and transition package
│   ├── operations/            # Operational runbooks, monitoring and backups
│   ├── quality/               # QA test plans, test reports and quality gates
│   ├── security/              # Threat models, container hardening and CVE audits
│   └── technical/             # Deep technical specifications and database design
├── kubernetes/                # Master Kubernetes manifests
├── scripts/                   # Cross-platform installation and maintenance scripts
├── .env.example               # Reference environment variables template
├── docker-compose.yml         # Master Compose definition (23 containers)
├── README.md                  # Developer & operator onboarding overview
└── RELEASE_NOTES_v2.0.0.md    # Production release notes
```
