#!/usr/bin/env bash
# ==============================================================================
# Universal Commerce Platform - Rolling Update Orchestrator
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "Starting rolling update procedure..."
cd "${ROOT_DIR}"

# 1. Trigger automated pre-update backup
echo "Creating pre-update database snapshot..."
bash "${SCRIPT_DIR}/backup.sh"

# 2. Build or pull latest images
echo "Rebuilding and updating containers..."
docker compose build --parallel
docker compose up -d --remove-orphans

# 3. Verify health
echo "Verifying health post-update..."
bash "${SCRIPT_DIR}/health-check.sh"
echo "Update successfully applied."
