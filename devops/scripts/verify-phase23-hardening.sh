#!/usr/bin/env bash
# Phase 23 Automated Verification Suite: Production Hardening & Release Handoff
set -eo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

echo "=============================================================================="
echo "  PHASE 23: PRODUCTION HARDENING, SECURITY AUDIT & RELEASE HANDOFF"
echo "=============================================================================="

TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

assert_test() {
    local desc="$1"
    local cond="$2"
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    if eval "$cond"; then
        PASSED_TESTS=$((PASSED_TESTS + 1))
        echo " [PASS] $desc"
    else
        FAILED_TESTS=$((FAILED_TESTS + 1))
        echo " [FAIL] $desc"
    fi
}

# 1. Container Security
assert_test "Container security scanner exists" "[ -f '$PROJECT_ROOT/devops/scripts/scan-container-security.sh' ]"
assert_test "Container security audit report exists" "[ -f '$PROJECT_ROOT/devops/reports/container-security-audit.json' ]"

# 2. OWASP Headers
assert_test "API Gateway configures X-Content-Type-Options" "grep -q 'X-Content-Type-Options' '$PROJECT_ROOT/backend/config-repo/api-gateway.yml'"
assert_test "Frontend Nginx configures X-Content-Type-Options" "grep -q 'X-Content-Type-Options' '$PROJECT_ROOT/frontend/nginx.conf'"

# 3. Kubernetes Network Policies
assert_test "NetworkPolicies manifest exists" "[ -f '$PROJECT_ROOT/kubernetes/security/network-policies.yml' ]"
assert_test "NetworkPolicies defines default-deny" "grep -q 'default-deny-ingress' '$PROJECT_ROOT/kubernetes/security/network-policies.yml'"

# 4. RBAC & PSA
assert_test "RBAC manifest exists" "[ -f '$PROJECT_ROOT/kubernetes/security/rbac-roles.yml' ]"
assert_test "Namespace enforces PSA restricted" "grep -q 'pod-security.kubernetes.io/enforce: restricted' '$PROJECT_ROOT/kubernetes/base/00-namespace.yml'"

# 5. Database Backup
assert_test "Backup CronJob manifest exists" "[ -f '$PROJECT_ROOT/kubernetes/database/backup-cronjob.yml' ]"
assert_test "Backup automation script exists" "[ -f '$PROJECT_ROOT/devops/scripts/backup-databases.sh' ]"

# 6. Runbooks
assert_test "Disaster Recovery Runbook exists" "[ -f '$PROJECT_ROOT/devops/runbooks/disaster-recovery-runbook.md' ]"
assert_test "Operations Manual exists" "[ -f '$PROJECT_ROOT/devops/runbooks/OPERATIONS_AND_RELEASE_HANDOFF.md' ]"

echo "=============================================================================="
echo " Total Tests: $TOTAL_TESTS"
echo " Passed     : $PASSED_TESTS"
echo " Failed     : $FAILED_TESTS"
echo "=============================================================================="

if [ "$FAILED_TESTS" -eq 0 ]; then
    exit 0
else
    exit 1
fi
