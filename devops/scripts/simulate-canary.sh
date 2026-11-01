#!/usr/bin/env bash
# ==============================================================================
# Phase 15: Progressive Canary Delivery & Automated Rollback Simulation Script
# ==============================================================================
set -e

echo -e "\033[36m=================================================================\033[0m"
echo -e "\033[36m   Starting Phase 15: GitOps & Progressive Canary Simulation     \033[0m"
echo -e "\033[36m=================================================================\033[0m"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
K8S_DIR="${PROJECT_ROOT}/kubernetes"
GITOPS_DIR="${K8S_DIR}/gitops"

echo -e "\n\033[33m--- 1. Pre-flight GitOps & Flagger Manifest Check ---\033[0m"
for manifest in \
    "${GITOPS_DIR}/argocd/argocd-project.yml" \
    "${GITOPS_DIR}/argocd/argocd-application.yml" \
    "${GITOPS_DIR}/argocd/argocd-applicationset.yml" \
    "${GITOPS_DIR}/flagger/flagger-canary-order-service.yml" \
    "${GITOPS_DIR}/flagger/flagger-metric-templates.yml" \
    "${GITOPS_DIR}/flagger/flagger-alert-provider.yml"; do
    if [ -f "$manifest" ]; then
        echo -e " \033[32m[OK]\033[0m Manifest found: $(basename "$manifest")"
    else
        echo -e " \033[31m[FAIL]\033[0m Missing manifest: $manifest"
        exit 1
    fi
done

echo -e "\n\033[33m--- 2. Scenario A: Progressive Canary Progression (v1.0.0 -> v2.0.0) ---\033[0m"
echo -e " \033[36m[GITOPS]\033[0m ArgoCD detected commit on main: Update order-service to image tag 'v2.0.0'"
echo -e " \033[32m[GITOPS]\033[0m ArgoCD sync state: InSync | Health: Healthy"
echo -e " \033[36m[FLAGGER]\033[0m Initializing Canary analysis for order-service"

for weight in 10 20 30 40 50; do
    primary=$((100 - weight))
    echo -e " \033[36m[TRAFFIC]\033[0m Shifting Canary Weight -> ${weight}% (Primary: ${primary}% | Canary: ${weight}%)"
    echo -e "   - request-success-rate: 99.8% (threshold >= 99.0%) -> \033[32mPASS\033[0m"
    echo -e "   - request-duration-p99: 135ms (threshold <= 500ms) -> \033[32mPASS\033[0m"
    sleep 0.1
done

echo -e " \033[32m[PROMOTION]\033[0m Promoting order-service:v2.0.0 to Primary Deployment"
echo -e " \033[32m[FLAGGER]\033[0m Canary status: Succeeded"

echo -e "\n\033[33m--- 3. Scenario B: Metric Degradation & Automated Rollback ---\033[0m"
echo -e " \033[36m[GITOPS]\033[0m ArgoCD detected candidate: order-service:v2.1.0-defect"
echo -e " \033[36m[TRAFFIC]\033[0m Canary Weight -> 10%"
echo -e " \033[31m[CHAOS]\033[0m Simulating 5xx server errors and high latency..."

for check in 1 2 3 4 5; do
    echo -e " \033[33m[PROMETHEUS]\033[0m Evaluation #${check} / 5: \033[31mFAILED\033[0m (Success Rate < 99.0%, P99 Latency > 500ms)"
    sleep 0.1
done

echo -e " \033[31m[ROLLBACK]\033[0m Threshold reached (5/5). Flagger halting rollout!"
echo -e " \033[31m[ROLLBACK]\033[0m Canary weight reset to 0%. 100% traffic reverted to primary (v2.0.0)!"
echo -e " \033[31m[FLAGGER]\033[0m Canary status: Failed"
echo -e " \033[32m[RECOVERY]\033[0m Zero customer impact on stable primary."

echo -e "\n\033[36m=================================================================\033[0m"
echo -e "\033[36m   Phase 15: Progressive Canary Delivery Simulation Completed!   \033[0m"
echo -e "\033[36m=================================================================\033[0m"
