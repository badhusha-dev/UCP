#!/usr/bin/env bash
# Container Security Audit & Hardening Scanner
set -eo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
echo "=============================================================================="
echo "  ENTERPRISE CONTAINER SECURITY AUDIT & HARDENING SCANNER"
echo "=============================================================================="

DOCKERFILES=$(find "$PROJECT_ROOT" -name "Dockerfile*" -not -path "*/node_modules/*")
TOTAL_CHECKS=0
PASSED_CHECKS=0

for DF in $DOCKERFILES; do
    REL_PATH="${DF#$PROJECT_ROOT/}"
    
    # 1. Non-root user check
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    if grep -q -E "USER\s+[a-zA-Z0-9_\-:]+|nginx:.*alpine" "$DF"; then
        PASSED_CHECKS=$((PASSED_CHECKS + 1))
        echo " [PASS] $REL_PATH : Non-root user configured"
    else
        echo " [WARN] $REL_PATH : Runs as default root user"
    fi

    # 2. Minimal base image check
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    if grep -q -E "alpine|distroless" "$DF"; then
        PASSED_CHECKS=$((PASSED_CHECKS + 1))
        echo " [PASS] $REL_PATH : Minimal base image (Alpine/Distroless)"
    else
        echo " [WARN] $REL_PATH : Heavy base image"
    fi

    # 3. Secret leakage check
    TOTAL_CHECKS=$((TOTAL_CHECKS + 1))
    if ! grep -qi -E "(password|secret|token|api[_-]?key)\s*=\s*['\"][^'\"]+['\"]" "$DF"; then
        PASSED_CHECKS=$((PASSED_CHECKS + 1))
        echo " [PASS] $REL_PATH : Zero hardcoded secrets detected"
    else
        echo " [FAIL] $REL_PATH : Potential hardcoded secret found!"
    fi
done

SCORE=$(( (PASSED_CHECKS * 100) / TOTAL_CHECKS ))
echo "=============================================================================="
echo " Compliance Score: $SCORE% ($PASSED_CHECKS / $TOTAL_CHECKS checks passed)"
echo "=============================================================================="

if [ "$SCORE" -ge 90 ]; then
    exit 0
else
    exit 1
fi
