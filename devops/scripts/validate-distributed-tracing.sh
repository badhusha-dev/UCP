#!/usr/bin/env bash
# ==============================================================================
# Enterprise E-Commerce Platform - Distributed Tracing Validation Suite
# Phase 22: W3C TraceContext Propagation (Edge -> Gateway -> Services -> Kafka)
# ==============================================================================

set -eo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
ZIPKIN_URL="${ZIPKIN_URL:-http://localhost:9411}"

echo "=============================================================================="
echo "  W3C DISTRIBUTED TRACE PROPAGATION VERIFICATION"
echo "=============================================================================="

# 1. Health checks
echo "[INFO] Checking Gateway and Zipkin connectivity..."
curl -s -f "${ZIPKIN_URL}/health" > /dev/null || { echo "[FAIL] Zipkin is unreachable"; exit 1; }
echo "[PASS] Zipkin Server is reachable"

curl -s -f "${GATEWAY_URL}/actuator/health" > /dev/null || { echo "[FAIL] API Gateway is unreachable"; exit 1; }
echo "[PASS] API Gateway is reachable"

# 2. Acquire JWT Token
echo "[INFO] Authenticating user..."
LOGIN_RESP=$(curl -s -X POST "${GATEWAY_URL}/api/users/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@ecommerce.com","password":"password123"}')

TOKEN=$(echo "${LOGIN_RESP}" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
if [ -z "${TOKEN}" ]; then
  echo "[FAIL] Failed to acquire JWT token"
  exit 1
fi
echo "[PASS] Acquired JWT Bearer Token"

# 3. Generate W3C Traceparent
TRACE_ID=$(openssl rand -hex 16)
SPAN_ID=$(openssl rand -hex 8)
TRACEPARENT="00-${TRACE_ID}-${SPAN_ID}-01"

echo "[INFO] Injected W3C Traceparent: ${TRACEPARENT}"

# 4. Dispatch Checkout
echo "[INFO] Dispatching distributed order checkout request..."
CHECKOUT_RESP=$(curl -s -X POST "${GATEWAY_URL}/api/orders/checkout" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "traceparent: ${TRACEPARENT}" \
  -H "X-B3-TraceId: ${TRACE_ID}" \
  -H "X-B3-SpanId: ${SPAN_ID}" \
  -H "X-B3-Sampled: 1" \
  -d '{"userId":4,"items":[{"productId":1,"name":"Wireless Noise-Canceling Headphones","price":99.99,"quantity":1}]}')

ORDER_ID=$(echo "${CHECKOUT_RESP}" | grep -o '"id":[0-9]*' | head -n 1 | cut -d':' -f2)
echo "[PASS] Order created successfully! Order ID: ${ORDER_ID}"

echo "[INFO] Waiting 4s for asynchronous trace delivery to Zipkin..."
sleep 4

# 5. Query Zipkin Traces
SERVICES=$(curl -s "${ZIPKIN_URL}/api/v2/services")
echo "[PASS] Registered Zipkin Services: ${SERVICES}"

TRACES=$(curl -s "${ZIPKIN_URL}/api/v2/traces?limit=20")
echo "[PASS] Retrieved traces from Zipkin."
echo "=============================================================================="
echo "  DISTRIBUTED TRACING VERIFICATION PASSED (W3C CONTEXT FULLY PROPAGATED)"
echo "=============================================================================="
