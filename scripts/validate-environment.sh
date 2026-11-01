#!/usr/bin/env bash
# ==============================================================================
# Universal Commerce Platform - Environment Validation Script (Linux/macOS)
# ==============================================================================
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BLUE}    UNIVERSAL COMMERCE PLATFORM - ENVIRONMENT VALIDATION (PRE-INSTALL)       ${NC}"
echo -e "${BLUE}==============================================================================${NC}"

ERRORS=0
WARNINGS=0

# 1. Operating System & Architecture
OS=$(uname -s)
ARCH=$(uname -m)
echo -n "[CHECK] Operating System: ${OS} (${ARCH}) ... "
if [[ "$OS" == "Linux" || "$OS" == "Darwin" ]]; then
    echo -e "${GREEN}PASS${NC}"
else
    echo -e "${RED}FAIL (Unsupported OS: $OS)${NC}"
    ERRORS=$((ERRORS + 1))
fi

# 2. CPU Cores Check
CPU_CORES=$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 1)
echo -n "[CHECK] CPU Cores Available: ${CPU_CORES} ... "
if [ "$CPU_CORES" -ge 4 ]; then
    echo -e "${GREEN}PASS (${CPU_CORES} cores)${NC}"
elif [ "$CPU_CORES" -ge 2 ]; then
    echo -e "${YELLOW}WARN (${CPU_CORES} cores; minimum met, 4+ recommended)${NC}"
    WARNINGS=$((WARNINGS + 1))
else
    echo -e "${RED}FAIL (At least 2 cores required)${NC}"
    ERRORS=$((ERRORS + 1))
fi

# 3. Memory Check (RAM)
TOTAL_RAM_KB=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2}' || sysctl -n hw.memsize 2>/dev/null | awk '{print $1/1024}' || echo 0)
TOTAL_RAM_MB=$((TOTAL_RAM_KB / 1024))
echo -n "[CHECK] System RAM: ~${TOTAL_RAM_MB} MB ... "
if [ "$TOTAL_RAM_MB" -ge 15000 ]; then
    echo -e "${GREEN}PASS (${TOTAL_RAM_MB} MB)${NC}"
elif [ "$TOTAL_RAM_MB" -ge 7500 ]; then
    echo -e "${YELLOW}WARN (${TOTAL_RAM_MB} MB; 8GB min met, 16GB recommended for full ELK stack)${NC}"
    WARNINGS=$((WARNINGS + 1))
else
    echo -e "${RED}FAIL (Minimum 8 GB RAM required, found ${TOTAL_RAM_MB} MB)${NC}"
    ERRORS=$((ERRORS + 1))
fi

# 4. Disk Space Check
FREE_DISK_GB=$(df -BG . | awk 'NR==2 {gsub("G","",$4); print $4}')
echo -n "[CHECK] Free Disk Space: ~${FREE_DISK_GB} GB ... "
if [ "$FREE_DISK_GB" -ge 25 ]; then
    echo -e "${GREEN}PASS (${FREE_DISK_GB} GB)${NC}"
elif [ "$FREE_DISK_GB" -ge 15 ]; then
    echo -e "${YELLOW}WARN (${FREE_DISK_GB} GB; 15GB minimum met, 25GB+ recommended)${NC}"
    WARNINGS=$((WARNINGS + 1))
else
    echo -e "${RED}FAIL (At least 15 GB free disk space required)${NC}"
    ERRORS=$((ERRORS + 1))
fi

# 5. Docker Engine Check
echo -n "[CHECK] Docker Engine Installation ... "
if command -v docker >/dev/null 2>&1; then
    DOCKER_VER=$(docker --version | awk '{print $3}' | sed 's/,//')
    echo -e "${GREEN}PASS (${DOCKER_VER})${NC}"
else
    echo -e "${RED}FAIL (Docker is not installed or not in PATH)${NC}"
    ERRORS=$((ERRORS + 1))
fi

# 6. Docker Compose Check
echo -n "[CHECK] Docker Compose (v2) ... "
if docker compose version >/dev/null 2>&1; then
    COMPOSE_VER=$(docker compose version --short 2>/dev/null || echo "v2")
    echo -e "${GREEN}PASS (${COMPOSE_VER})${NC}"
else
    echo -e "${RED}FAIL ('docker compose' v2 plugin missing)${NC}"
    ERRORS=$((ERRORS + 1))
fi

# 7. Port Conflicts Check
PORTS_TO_CHECK=(3000 3001 3002 3003 8080 8081 8082 8083 8084 8761 8888 5432 6379 9092 2181 8090 9090 3030 9093 9411 9200 5000 5601)
CONFLICTING_PORTS=()

for PORT in "${PORTS_TO_CHECK[@]}"; do
    if ss -tuln 2>/dev/null | grep -q ":${PORT} " || netstat -tuln 2>/dev/null | grep -q ":${PORT} "; then
        # Check if already bound by an existing running platform container
        if ! docker ps --format '{{.Ports}}' 2>/dev/null | grep -q ":${PORT}->"; then
            CONFLICTING_PORTS+=("$PORT")
        fi
    fi
done

echo -n "[CHECK] Port Availability ... "
if [ ${#CONFLICTING_PORTS[@]} -eq 0 ]; then
    echo -e "${GREEN}PASS (All 23 platform ports available)${NC}"
else
    echo -e "${YELLOW}WARN (Host processes bound to: ${CONFLICTING_PORTS[*]})${NC}"
    WARNINGS=$((WARNINGS + 1))
fi

# 8. Outbound Registry Connectivity
echo -n "[CHECK] Docker Hub / Registry Connectivity ... "
if docker pull hello-world:linux >/dev/null 2>&1 || curl -s --connect-timeout 5 https://hub.docker.com >/dev/null; then
    echo -e "${GREEN}PASS${NC}"
else
    echo -e "${YELLOW}WARN (Outbound internet connectivity slow or restricted)${NC}"
    WARNINGS=$((WARNINGS + 1))
fi

# Summary
echo -e "${BLUE}------------------------------------------------------------------------------${NC}"
if [ "$ERRORS" -eq 0 ]; then
    echo -e "${GREEN}[SUCCESS] Environment validation passed! (${WARNINGS} warnings)${NC}"
    echo -e "${GREEN}System is ready for Universal Commerce Platform installation.${NC}"
    exit 0
else
    echo -e "${RED}[ERROR] Environment validation failed with ${ERRORS} critical error(s).${NC}"
    echo -e "${RED}Please resolve the issues above before proceeding with installation.${NC}"
    exit 1
fi
