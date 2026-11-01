#!/usr/bin/env bash
# ==============================================================================
# Universal Commerce Platform - Synthetic Health Prober
# ==============================================================================
set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BLUE}        UNIVERSAL COMMERCE PLATFORM - SYNTHETIC HEALTH PROBER                ${NC}"
echo -e "${BLUE}==============================================================================${NC}"

ENDPOINTS=(
    "Storefront Shell:http://localhost:3000/:200"
    "Product MFE:http://localhost:3001/assets/remoteEntry.js:200"
    "Order MFE:http://localhost:3002/assets/remoteEntry.js:200"
    "User MFE:http://localhost:3003/assets/remoteEntry.js:200"
    "API Gateway Health:http://localhost:8080/actuator/health:200"
    "User Service Health:http://localhost:8081/actuator/health:200"
    "Product Service Health:http://localhost:8082/actuator/health:200"
    "Order Service Health:http://localhost:8083/actuator/health:200"
    "Notification Service Health:http://localhost:8084/actuator/health:200"
    "Eureka Discovery Server:http://localhost:8761/actuator/health:200"
    "Config Server:http://localhost:8888/actuator/health:200"
    "Kafka UI:http://localhost:8090/actuator/health:200"
    "Prometheus Health:http://localhost:9090/-/healthy:200"
    "Grafana Health:http://localhost:3030/api/health:200"
    "Zipkin Health:http://localhost:9411/health:200"
)

TOTAL=0
PASSED=0

for item in "${ENDPOINTS[@]}"; do
    IFS=":" read -r NAME URL_PART1 URL_PART2 EXPECTED <<< "$item"
    URL="${URL_PART1}:${URL_PART2}"
    TOTAL=$((TOTAL + 1))
    
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 4 "$URL" || echo "000")
    
    if [ "$HTTP_CODE" == "$EXPECTED" ]; then
        echo -e "[PASS] ${NAME} -> ${URL} [HTTP ${HTTP_CODE}]"
        PASSED=$((PASSED + 1))
    else
        echo -e "[FAIL] ${NAME} -> ${URL} [Expected ${EXPECTED}, Got ${HTTP_CODE}]"
    fi
done

echo -e "${BLUE}------------------------------------------------------------------------------${NC}"
PERCENT=$(( (PASSED * 100) / TOTAL ))
echo -e "Health Probe Summary: ${PASSED} / ${TOTAL} Passed (${PERCENT}% Availability)"

if [ "$PASSED" -eq "$TOTAL" ]; then
    echo -e "${GREEN}All services operational. Platform is HEALTHY.${NC}"
    exit 0
else
    echo -e "${RED}One or more services failed health checks.${NC}"
    exit 1
fi
