#!/usr/bin/env bash
# Live check of storefront checkout tenancy (dev stack only). Only refusal paths are exercised, so no order is created and
# no stock moves; the script verifies that by counting orders before and after.
# Needs: accounting-test-users.env, accounting-test-tenants.sql (users) and storefront-test.sql applied.
set -u
cd "$(dirname "$0")"
set -a; . ./accounting-test-users.env; set +a
GW=${GW:-http://localhost:8080}
FAILS=0
check() { if [[ " $2 " == *" $3 "* ]]; then echo "PASS  $1 -> $3"; else echo "FAIL  $1 -> got $3, expected $2"; FAILS=$((FAILS+1)); fi; }
orders() { docker exec ecommerce-platform-postgres-1 psql -U ecommerce_admin -d order_db -tAc "select count(*) from orders"; }

# A shopper session: platform login of a test user (role USER).
SHOPPER=$(curl -s -X POST "$GW/api/users/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$UCP_TEST_AUDIT_A_EMAIL\",\"password\":\"$UCP_TEST_AUDIT_A_PASSWORD\"}" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
check "shopper login" "yes" "$([ -n "$SHOPPER" ] && echo yes || echo no)"
LEGACY_PRODUCT=$(docker exec ecommerce-platform-postgres-1 psql -U ecommerce_admin -d product_db -tAc "select id from product where tenant_id='00000000-0000-4000-8000-000000000001' order by id limit 1")
BEFORE=$(orders)

checkout() { # storefront
  curl -s -o /tmp/ucp_e2e_body -w '%{http_code}' -X POST "$GW/api/orders/checkout" -H "Authorization: Bearer $SHOPPER" -H 'Content-Type: application/json' \
    -d "{\"storefront\":\"$1\",\"items\":[{\"productId\":$LEGACY_PRODUCT,\"name\":\"x\",\"price\":0.01,\"quantity\":1}]}"
}
check "unknown storefront refused" "400" "$(checkout no-such-shop-xyz)"
check "tenant A storefront refuses a legacy-tenant product" "400" "$(checkout ucp-test-acct-a-shop)"
echo "      response: $(sed -n 's/.*"\(message\|error\)":"\([^"]*\)".*/\2/p' /tmp/ucp_e2e_body)"
check "malformed storefront slug refused" "400" "$(checkout '../etc')"
check "no order was created" "$BEFORE" "$(orders)"

rm -f /tmp/ucp_e2e_body
echo "failures: $FAILS"
exit $FAILS
