#!/usr/bin/env bash
# ==============================================================================
# Universal Commerce Platform - Deployment Orchestrator
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "Deploying Universal Commerce Platform in $(pwd)..."
cd "${ROOT_DIR}"

if [ ! -f ".env" ]; then
    echo "Creating .env from .env.example..."
    cp .env.example .env
fi

echo "Starting containers via Docker Compose..."
docker compose up -d

echo "Running health checks..."
bash "${SCRIPT_DIR}/health-check.sh"
echo "Deployment complete."
