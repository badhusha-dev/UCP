#!/usr/bin/env bash
# Live end-to-end check of the tenant-scoped Accounting API through the gateway (dev stack only).
# Needs: scripts/dev-seed/accounting-test-users.env and scripts/dev-seed/accounting-test-tenants.sql applied.
# Prints statuses and ids only — never tokens or passwords. Exit code = number of failed checks.
set -u
cd "$(dirname "$0")"
set -a; . ./accounting-test-users.env; set +a
GW=${GW:-http://localhost:8080}
RUN=$(date +%s | tail -c 7)
FAILS=0
check() { # label expected actual
  if [[ " $2 " == *" $3 "* ]]; then echo "PASS  $1 -> $3"; else echo "FAIL  $1 -> got $3, expected $2"; FAILS=$((FAILS+1)); fi
}
login() { # email password tenantSlug -> token (stdout)
  curl -s -X POST "$GW/api/users/login/application" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$2\",\"applicationCode\":\"ACCOUNTING\",\"tenantSlug\":\"$3\"}" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p'
}
code() { # method token path [json]
  if [ $# -ge 4 ]; then curl -s -o /tmp/ucp_e2e_body -w '%{http_code}' -X "$1" -H "Authorization: Bearer $2" -H 'Content-Type: application/json' -d "$4" "$GW$3"
  else curl -s -o /tmp/ucp_e2e_body -w '%{http_code}' -X "$1" -H "Authorization: Bearer $2" "$GW$3"; fi
}
body_id() { sed -n 's/^{"id":\([0-9]*\).*/\1/p' /tmp/ucp_e2e_body; }

A=$(login "$UCP_TEST_ACCT_A_EMAIL" "$UCP_TEST_ACCT_A_PASSWORD" ucp-test-acct-a)
AUD=$(login "$UCP_TEST_AUDIT_A_EMAIL" "$UCP_TEST_AUDIT_A_PASSWORD" ucp-test-acct-a)
B=$(login "$UCP_TEST_ACCT_B_EMAIL" "$UCP_TEST_ACCT_B_PASSWORD" ucp-test-acct-b)
check "login accountant A (tenant A)" "yes" "$([ -n "$A" ] && echo yes || echo no)"
check "login auditor A (tenant A)" "yes" "$([ -n "$AUD" ] && echo yes || echo no)"
check "login accountant B (tenant B)" "yes" "$([ -n "$B" ] && echo yes || echo no)"
WRONG=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$GW/api/users/login/application" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$UCP_TEST_ACCT_A_EMAIL\",\"password\":\"$UCP_TEST_ACCT_A_PASSWORD\",\"applicationCode\":\"ACCOUNTING\",\"tenantSlug\":\"ucp-test-acct-b\"}")
check "accountant A cannot log in to tenant B" "401 403" "$WRONG"

CASH="C$RUN"; SALES="S$RUN"
for T in "$A" "$B"; do
  check "create cash account $CASH" "201" "$(code POST "$T" /api/accounting/admin/accounts "{\"code\":\"$CASH\",\"name\":\"Cash\",\"accountType\":\"ASSET\",\"normalBalance\":\"DEBIT\",\"cashAccount\":true}")"
  check "create sales account $SALES" "201" "$(code POST "$T" /api/accounting/admin/accounts "{\"code\":\"$SALES\",\"name\":\"Sales\",\"accountType\":\"REVENUE\",\"normalBalance\":\"CREDIT\"}")"
done
JOURNAL="{\"idempotencyKey\":\"e2e-$RUN\",\"description\":\"E2E sale $RUN\",\"currency\":\"USD\",\"lines\":[{\"accountCode\":\"$CASH\",\"debitAmount\":\"125.00\",\"creditAmount\":\"0\"},{\"accountCode\":\"$SALES\",\"debitAmount\":\"0\",\"creditAmount\":\"125.00\"}]}"
check "A posts journal" "201" "$(code POST "$A" /api/accounting/admin/journals "$JOURNAL")"; JA=$(body_id)
check "B posts journal with the same idempotency key" "201" "$(code POST "$B" /api/accounting/admin/journals "$JOURNAL")"; JB=$(body_id)
echo "      journal ids: A=$JA B=$JB"
check "A and B got different journals" "yes" "$([ -n "$JA" ] && [ "$JA" != "$JB" ] && echo yes || echo no)"

code GET "$B" "/api/accounting/admin/journals?size=100" >/dev/null
check "B's journal list excludes A's journal" "yes" "$(grep -q "\"id\":$JA," /tmp/ucp_e2e_body && echo no || echo yes)"
code GET "$A" "/api/accounting/admin/journals?size=100" >/dev/null
check "A's journal list includes A's journal" "yes" "$(grep -q "\"id\":$JA," /tmp/ucp_e2e_body && echo yes || echo no)"
check "B cannot reverse A's journal" "403 404" "$(code POST "$B" /api/accounting/admin/journals/$JA/reverse '{"reason":"cross tenant"}')"

check "auditor A can read journals" "200" "$(code GET "$AUD" "/api/accounting/admin/journals?size=5")"
check "auditor A cannot post a journal" "403" "$(code POST "$AUD" /api/accounting/admin/journals "$JOURNAL")"
check "accountant A cannot approve drafts (no accounting:approve)" "403" "$(code POST "$A" /api/accounting/admin/journals/drafts/1/approve)"
check "tenant token refused on customer accounting API" "403" "$(code GET "$A" /api/accounting/balance)"
check "tenant token refused on CRM" "403" "$(code GET "$A" /api/crm/customers)"
check "forged X-Tenant-ID header is ignored" "yes" "$(curl -s -H "Authorization: Bearer $B" -H 'X-Tenant-ID: a0c0a0c0-0000-4000-8000-0000000000a1' "$GW/api/accounting/admin/journals?size=100" | grep -q "\"id\":$JA," && echo no || echo yes)"

rm -f /tmp/ucp_e2e_body
echo "failures: $FAILS"
exit $FAILS
