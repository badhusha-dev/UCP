#!/usr/bin/env bash
# Live check that E-commerce Admin tenant logins work and stay tenant-scoped (dev stack only).
# Needs: accounting-test-users.env, accounting-test-tenants.sql and ecommerce-admin-test-grants.sql applied.
# Prints statuses only — never tokens or passwords. Exit code = number of failed checks.
set -u
cd "$(dirname "$0")"
set -a; . ./accounting-test-users.env; set +a
GW=${GW:-http://localhost:8080}
FAILS=0
check() { if [[ " $2 " == *" $3 "* ]]; then echo "PASS  $1 -> $3"; else echo "FAIL  $1 -> got $3, expected $2"; FAILS=$((FAILS+1)); fi; }
login() { # email password tenantSlug application
  curl -s -X POST "$GW/api/users/login/application" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$2\",\"applicationCode\":\"$4\",\"tenantSlug\":\"$3\"}" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p'
}
code() { curl -s -o /tmp/ucp_e2e_body -w '%{http_code}' -H "Authorization: Bearer $2" "$GW$1"; }

A=$(login "$UCP_TEST_ACCT_A_EMAIL" "$UCP_TEST_ACCT_A_PASSWORD" ucp-test-acct-a ECOMMERCE_ADMIN)
B=$(login "$UCP_TEST_ACCT_B_EMAIL" "$UCP_TEST_ACCT_B_PASSWORD" ucp-test-acct-b ECOMMERCE_ADMIN)
ACCT=$(login "$UCP_TEST_ACCT_A_EMAIL" "$UCP_TEST_ACCT_A_PASSWORD" ucp-test-acct-a ACCOUNTING)
AUD=$(login "$UCP_TEST_AUDIT_A_EMAIL" "$UCP_TEST_AUDIT_A_PASSWORD" ucp-test-acct-a ECOMMERCE_ADMIN)
check "E-commerce Admin login, tenant A" "yes" "$([ -n "$A" ] && echo yes || echo no)"
check "E-commerce Admin login, tenant B" "yes" "$([ -n "$B" ] && echo yes || echo no)"
check "auditor A has no E-commerce Admin grant" "no" "$([ -n "$AUD" ] && echo yes || echo no)"

check "A lists orders" "200" "$(code /api/orders/admin/orders "$A")"
check "A sees none of the legacy tenant's orders" "yes" "$(grep -q '"orderNumber"' /tmp/ucp_e2e_body && echo no || echo yes)"
check "A order dashboard" "200" "$(code /api/orders/admin/dashboard "$A")"
LEGACY_ID=${LEGACY_ORDER_ID:-}
if [ -n "$LEGACY_ID" ]; then check "A cannot open legacy order #$LEGACY_ID" "403 404" "$(code /api/orders/admin/orders/$LEGACY_ID "$A")"; fi
check "ORDER_ANALYST cannot open pricing admin" "403" "$(code /api/products/admin/pricing/dashboard "$A")"
check "tenant token refused on legacy /api/orders" "403" "$(code /api/orders "$A")"
check "E-commerce Admin token refused by accounting admin" "401 403" "$(code /api/accounting/admin/accounts "$A")"
check "Accounting token refused by order admin" "401 403" "$(code /api/orders/admin/orders "$ACCT")"
check "B lists orders" "200" "$(code /api/orders/admin/orders "$B")"

rm -f /tmp/ucp_e2e_body
echo "failures: $FAILS"
exit $FAILS
