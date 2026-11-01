#!/usr/bin/env bash
# ==============================================================================
# Universal Commerce Platform - Disaster Recovery Rollback Procedure
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

BACKUP_PATH="${1:-""}"

echo "================================================================="
echo "   Universal Commerce Platform - Rollback Procedure              "
echo "================================================================="

cd "${ROOT_DIR}"

echo "Stopping application domain microservices..."
docker compose stop user-service product-service order-service notification-service api-gateway

if [ -n "${BACKUP_PATH}" ]; then
    echo "Restoring database state from ${BACKUP_PATH}..."
    bash "${SCRIPT_DIR}/restore.sh" "${BACKUP_PATH}"
else
    echo "Restoring database state from latest pre-update snapshot..."
    bash "${SCRIPT_DIR}/restore.sh"
fi

echo "Restarting application microservices..."
docker compose up -d user-service product-service order-service notification-service api-gateway

echo "Validating system health post-rollback..."
bash "${SCRIPT_DIR}/health-check.sh"

echo "Rollback completed."
