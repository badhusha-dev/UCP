#!/usr/bin/env bash
# ==============================================================================
# Universal Commerce Platform - Safe Uninstaller Script
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

PURGE_DATA=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --purge-data)
            PURGE_DATA=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--purge-data]"
            exit 1
            ;;
    esac
done

echo "================================================================="
echo "   Universal Commerce Platform - Safe Uninstaller                "
echo "================================================================="
cd "${ROOT_DIR}"

echo "Gracefully stopping all platform containers..."
docker compose stop

echo "Removing stopped containers and virtual networks..."
docker compose down

if [ "$PURGE_DATA" = true ]; then
    echo -e "${RED}WARNING: --purge-data flag detected!${NC}"
    echo -e "${RED}This will PERMANENTLY ERASE all PostgreSQL databases, Redis cache, and Kafka topics!${NC}"
    read -p "Type 'DELETE-ALL-DATA' to confirm destructive removal: " CONFIRMATION
    if [ "$CONFIRMATION" == "DELETE-ALL-DATA" ]; then
        echo "Purging named volumes..."
        docker compose down -v
        echo -e "${RED}All persistent volumes destroyed.${NC}"
    else
        echo "Confirmation mismatch. Persistent volumes preserved."
    fi
else
    echo -e "${GREEN}[SAFE] Persistent database volumes (postgres_data, redis_data, kafka_data) PRESERVED.${NC}"
    echo -e "To delete data completely, re-run with: $0 --purge-data"
fi

echo "Uninstallation completed."
