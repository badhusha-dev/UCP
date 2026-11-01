#!/bin/bash
# ==============================================================================
# Universal Commerce Platform - Synthetic Health Prober (Bash)
# Phase 18: Production Observability, Alerting & SLO Automation
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
REPORT_DIR="${PROJECT_ROOT}/devops/reports"
REPORT_FILE="${REPORT_DIR}/synthetic-probe-latest.json"

mkdir -p "${REPORT_DIR}"

echo "================================================================="
echo "  Universal Commerce — Synthetic Health & SLO Edge Prober (Bash) "
echo "================================================================="
echo " Timestamp: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo " Report:    ${REPORT_FILE}"
echo ""

declare -a TARGETS=(
    "Discovery Server|Platform|http://localhost:8761/|200"
    "Config Server|Platform|http://localhost:8888/actuator/health|200"
    "API Gateway Core|Gateway|http://localhost:8080/actuator/health|200"
    "API Gateway Route|Gateway Route|http://localhost:8080/product-service/v3/api-docs|200"
    "User Microservice|Domain Service|http://localhost:8081/actuator/health|200"
    "Product Microservice|Domain Service|http://localhost:8082/actuator/health|200"
    "Order Microservice|Domain Service|http://localhost:8083/actuator/health|200"
    "Notification Service|Domain Service|http://localhost:8084/actuator/health|200"
    "Prometheus Server|Observability|http://localhost:9090/-/healthy|200"
    "Alertmanager|Observability|http://localhost:9093/-/healthy|200"
    "Grafana Dashboards|Observability|http://localhost:3030/api/health|200"
    "Shell App MFE|Storefront MFE|http://localhost:3000/|200"
    "Product MFE|Storefront MFE|http://localhost:3001/|200"
    "Order MFE|Storefront MFE|http://localhost:3002/|200"
    "User MFE|Storefront MFE|http://localhost:3003/|200"
)

TOTAL_TARGETS=${#TARGETS[@]}
PASSED_COUNT=0
FAILED_COUNT=0

for TARGET in "${TARGETS[@]}"; do
    IFS="|" read -r NAME CATEGORY URL EXPECTED_STATUS <<< "${TARGET}"
    
    START=$(date +%s%N)
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${URL}" || echo "000")
    END=$(date +%s%N)
    LATENCY_MS=$(( (END - START) / 1000000 ))

    if [ "${HTTP_CODE}" -eq "${EXPECTED_STATUS}" ]; then
        PASSED_COUNT=$((PASSED_COUNT + 1))
        printf " [UP]   %-22s | %-14s | HTTP %s | %4d ms\n" "${NAME}" "${CATEGORY}" "${HTTP_CODE}" "${LATENCY_MS}"
    else
        FAILED_COUNT=$((FAILED_COUNT + 1))
        printf " [DOWN] %-22s | %-14s | HTTP %s | %4d ms\n" "${NAME}" "${CATEGORY}" "${HTTP_CODE}" "${LATENCY_MS}"
    fi
done

AVAILABILITY=$(awk "BEGIN {printf \"%.2f\", (${PASSED_COUNT}/${TOTAL_TARGETS})*100}")

echo ""
echo "-----------------------------------------------------------------"
echo " Synthetic Health Summary:"
echo "   Total Endpoints:   ${TOTAL_TARGETS}"
echo "   Healthy:           ${PASSED_COUNT}"
echo "   Failed:            ${FAILED_COUNT}"
echo "   Availability:      ${AVAILABILITY}%"
echo "-----------------------------------------------------------------"

if [ "${FAILED_COUNT}" -eq 0 ]; then
    echo " ALL SYNTHETIC PROBES HEALTHY (SLO 100% COMPLIANT)"
    exit 0
else
    echo " SYNTHETIC PROBE ALERT: ${FAILED_COUNT} endpoints unhealthy"
    exit 1
fi
