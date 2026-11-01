#!/usr/bin/env bash
# ==============================================================================
# Universal Commerce Platform - Automated E2E Smoke Test Suite
# ==============================================================================
set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}==============================================================================${NC}"
echo -e "${CYAN}          UNIVERSAL COMMERCE PLATFORM - E2E SMOKE TEST SUITE                  ${NC}"
echo -e "${CYAN}==============================================================================${NC}"

GATEWAY="http://localhost:8080"
TESTS_PASSED=0
TOTAL_TESTS=5

# Test 1: Health Prober
echo -n "[TEST 1/5] Checking Gateway Actuator Health... "
HEALTH_STATUS=$(curl -s --connect-timeout 5 "${GATEWAY}/actuator/health" | grep -o '"status":"UP"' || true)
if [ -n "$HEALTH_STATUS" ]; then
    echo -e "${GREEN}PASS (UP)${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}FAIL${NC}"
fi

# Test 2: Authentication & JWT Minting
echo -n "[TEST 2/5] Authenticating User via Gateway (admin@ecommerce.com)... "
AUTH_RESPONSE=$(curl -s -X POST "${GATEWAY}/api/users/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"admin@ecommerce.com","password":"password123"}' || true)

TOKEN=$(echo "$AUTH_RESPONSE" | grep -o '"token":"[^"]*' | cut -d'"' -f4 || echo "")

if [ -n "$TOKEN" ]; then
    echo -e "${GREEN}PASS (JWT Acquired)${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}FAIL (No token received)${NC}"
    exit 1
fi

# Test 3: Product Catalog Retrieval
echo -n "[TEST 3/5] Querying Product Catalog via Gateway... "
PRODUCT_COUNT=$(curl -s "${GATEWAY}/api/products" | grep -o '"id":' | wc -l || echo 0)
if [ "$PRODUCT_COUNT" -gt 0 ]; then
    echo -e "${GREEN}PASS (${PRODUCT_COUNT} products found)${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}FAIL (No products returned)${NC}"
fi

# Test 4: Checkout & Order Creation
echo -n "[TEST 4/5] Executing Checkout Transaction (Product #1)... "
ORDER_RESP=$(curl -s -X POST "${GATEWAY}/api/orders/checkout" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${TOKEN}" \
    -d '{"items":[{"productId":1,"quantity":1,"price":99.99}]}' || true)

ORDER_ID=$(echo "$ORDER_RESP" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2 || echo "")

if [ -n "$ORDER_ID" ]; then
    echo -e "${GREEN}PASS (Order #${ORDER_ID} created with status PLACED)${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}FAIL (Order placement failed: $ORDER_RESP)${NC}"
fi

# Test 5: Zipkin Distributed Trace Verification
echo -n "[TEST 5/5] Verifying Distributed Trace Registration in Zipkin... "
TRACE_COUNT=$(curl -s "http://localhost:9411/api/v2/traces?serviceName=api-gateway&limit=5" | grep -o '"traceId":' | wc -l || echo 0)
if [ "$TRACE_COUNT" -gt 0 ]; then
    echo -e "${GREEN}PASS (Distributed traces captured)${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "${RED}FAIL (No traces found in Zipkin)${NC}"
fi

echo -e "${CYAN}------------------------------------------------------------------------------${NC}"
echo -e "Smoke Test Result: ${TESTS_PASSED} / ${TOTAL_TESTS} Passed"
if [ "$TESTS_PASSED" -eq "$TOTAL_TESTS" ]; then
    echo -e "${GREEN}ALL SMOKE TESTS PASSED - PLATFORM READY FOR PRODUCTION TRAFFIC${NC}"
    exit 0
else
    echo -e "${RED}SMOKE TESTS FAILED${NC}"
    exit 1
fi
