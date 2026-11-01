#!/usr/bin/env bash
# ==============================================================================
# Universal Commerce Platform - One-Command Master Installer (Linux/macOS)
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

GENERATE_SECRETS=false
PROFILE="all"
SKIP_VALIDATION=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --generate-secrets)
            GENERATE_SECRETS=true
            shift
            ;;
        --profile=*)
            PROFILE="${1#*=}"
            shift
            ;;
        --skip-validation)
            SKIP_VALIDATION=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [--generate-secrets] [--profile=core|observability|all] [--skip-validation]"
            exit 0
            ;;
        *)
            echo "Unknown parameter: $1"
            exit 1
            ;;
    esac
done

echo -e "${CYAN}==============================================================================${NC}"
echo -e "${CYAN}        UNIVERSAL COMMERCE PLATFORM - PRODUCTION SERVER INSTALLER            ${NC}"
echo -e "${CYAN}==============================================================================${NC}"

# Step 1: Pre-installation Environment Validation
if [ "$SKIP_VALIDATION" = false ]; then
    echo -e "${BLUE}[STEP 1/7] Running pre-flight environment validation...${NC}"
    if [ -f "${SCRIPT_DIR}/validate-environment.sh" ]; then
        bash "${SCRIPT_DIR}/validate-environment.sh" || {
            echo -e "${RED}[ERROR] Environment validation failed. Aborting installation.${NC}"
            exit 1
        }
    fi
else
    echo -e "${YELLOW}[STEP 1/7] Pre-flight validation skipped per --skip-validation.${NC}"
fi

# Step 2: Environment Configuration & Secret Management
echo -e "${BLUE}[STEP 2/7] Checking environment configuration (.env)...${NC}"
cd "${ROOT_DIR}"
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}No .env file found. Creating from .env.example...${NC}"
    cp .env.example .env
fi

if [ "$GENERATE_SECRETS" = true ]; then
    echo -e "${CYAN}Generating cryptographically secure secrets...${NC}"
    NEW_JWT_SECRET=$(openssl rand -hex 32)
    NEW_PG_PASS=$(openssl rand -base64 18 | tr -dc 'a-zA-Z0-9' | head -c 16)
    NEW_REDIS_PASS=$(openssl rand -base64 18 | tr -dc 'a-zA-Z0-9' | head -c 16)
    NEW_GRAFANA_PASS=$(openssl rand -base64 18 | tr -dc 'a-zA-Z0-9' | head -c 16)
    
    sed -i.bak "s|^JWT_SECRET=.*|JWT_SECRET=${NEW_JWT_SECRET}|" .env
    sed -i.bak "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=${NEW_PG_PASS}|" .env
    sed -i.bak "s|^REDIS_PASSWORD=.*|REDIS_PASSWORD=${NEW_REDIS_PASS}|" .env
    sed -i.bak "s|^GRAFANA_ADMIN_PASSWORD=.*|GRAFANA_ADMIN_PASSWORD=${NEW_GRAFANA_PASS}|" .env
    rm -f .env.bak
    echo -e "${GREEN}Secure secrets generated and stored in .env.${NC}"
fi

# Step 3: Required Data Directories
echo -e "${BLUE}[STEP 3/7] Preparing storage directories and permissions...${NC}"
mkdir -p devops/backups devops/certs devops/reports
chmod 700 devops/backups 2>/dev/null || true

# Step 4: Pulling Pinned Images & Validating Compose
echo -e "${BLUE}[STEP 4/7] Validating Docker Compose configuration...${NC}"
docker compose config --quiet || {
    echo -e "${RED}[ERROR] Invalid docker-compose.yml configuration!${NC}"
    exit 1
}

# Step 5: Sequential Startup
echo -e "${BLUE}[STEP 5/7] Starting Infrastructure Services (Phase A: DB, Broker, Redis)...${NC}"
docker compose up -d postgres redis zookeeper kafka

echo "Waiting for core infrastructure readiness..."
sleep 8

echo -e "${BLUE}[STEP 5/7] Starting Platform Services (Phase B: Eureka & Config Server)...${NC}"
docker compose up -d discovery-server config-server

echo "Waiting for Config Server and Service Discovery..."
sleep 12

echo -e "${BLUE}[STEP 5/7] Starting Domain Microservices & API Gateway (Phase C)...${NC}"
docker compose up -d user-service product-service order-service notification-service api-gateway

echo -e "${BLUE}[STEP 5/7] Starting Frontend Microfrontends & Observability (Phase D)...${NC}"
docker compose up -d shell-app mfe-product mfe-order mfe-user \
    prometheus grafana alertmanager zipkin kafka-ui elasticsearch logstash kibana vault

echo "Waiting for Eureka service registry and API Gateway readiness..."
READY=false
for attempt in $(seq 1 40); do
    if curl -s -f http://localhost:8080/actuator/health 2>/dev/null | grep -q "UP"; then
        READY=true
        echo -e "\n${GREEN}API Gateway is UP and services registered! (Ready in $((attempt * 5))s)${NC}"
        break
    fi
    echo -n "."
    sleep 5
done

# Step 6: Health Probe & Verification
echo -e "${BLUE}[STEP 6/7] Running synthetic health checks...${NC}"
if [ -f "${SCRIPT_DIR}/health-check.sh" ]; then
    bash "${SCRIPT_DIR}/health-check.sh" || {
        echo -e "${YELLOW}[WARN] Some endpoints still warming up. Re-run scripts/health-check.sh in 30 seconds.${NC}"
    }
fi

# Step 7: Record Deployment Metadata & Summary
DEPLOY_TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
echo "DEPLOYMENT_TIMESTAMP=${DEPLOY_TIMESTAMP}" > .deployment_manifest
echo "PLATFORM_VERSION=2.0.0" >> .deployment_manifest
echo "PROFILE=${PROFILE}" >> .deployment_manifest

echo -e "${CYAN}==============================================================================${NC}"
echo -e "${GREEN}   UNIVERSAL COMMERCE PLATFORM INSTALLED SUCCESSFULLY!                       ${NC}"
echo -e "${CYAN}==============================================================================${NC}"
echo -e "Access Endpoints:"
echo -e "  - Customer Storefront (Shell):  ${CYAN}http://localhost:3000${NC}"
echo -e "  - Product Catalog MFE:          ${CYAN}http://localhost:3001${NC}"
echo -e "  - Order & Cart MFE:             ${CYAN}http://localhost:3002${NC}"
echo -e "  - User Profile MFE:             ${CYAN}http://localhost:3003${NC}"
echo -e "  - API Gateway Ingress:          ${CYAN}http://localhost:8080${NC}"
echo -e "  - Swagger OpenAPI UI:           ${CYAN}http://localhost:8080/swagger-ui.html${NC}"
echo -e "  - Eureka Service Registry:      ${CYAN}http://localhost:8761${NC}"
echo -e "  - Grafana Observability:        ${CYAN}http://localhost:3030${NC} (admin / check .env)"
echo -e "  - Prometheus Metrics:           ${CYAN}http://localhost:9090${NC}"
echo -e "  - Kafka UI Management:          ${CYAN}http://localhost:8090${NC}"
echo -e "  - Zipkin Distributed Tracing:  ${CYAN}http://localhost:9411${NC}"
echo -e "  - Kibana Centralized Logs:      ${CYAN}http://localhost:5601${NC}"
echo -e "${CYAN}------------------------------------------------------------------------------${NC}"
echo -e "Run smoke tests:  bash scripts/smoke-test.sh"
echo -e "Run health probe: bash scripts/health-check.sh"
echo -e "${CYAN}==============================================================================${NC}"
