#!/usr/bin/env bash
# ==============================================================================
# Universal Commerce Platform - Multi-Database Disaster Recovery Restore
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

BACKUP_DIR="${1:-""}"

if [ -z "${BACKUP_DIR}" ]; then
    LATEST=$(find "${ROOT_DIR}/devops/backups" -mindepth 1 -maxdepth 1 -type d -name "backup_*" | sort -r | head -1 || true)
    if [ -n "${LATEST}" ]; then
        BACKUP_DIR="${LATEST}"
        echo "Auto-detected latest backup directory: ${BACKUP_DIR}"
    else
        echo "Usage: $0 <path-to-backup-dir>"
        exit 1
    fi
fi

CONTAINER_NAME="${CONTAINER_NAME:-"$(docker ps -qf "name=postgres" | head -1)"}"
DB_USER="${POSTGRES_USER:-"postgres"}"
DATABASES="user_db product_db order_db notification_db"

echo "================================================================="
echo "   Universal Commerce Platform - Database Restore Procedure      "
echo "================================================================="
echo "Source Snapshot: ${BACKUP_DIR}"

for DB in ${DATABASES}; do
    SQL_FILE="${BACKUP_DIR}/${DB}.sql"
    SHA_FILE="${BACKUP_DIR}/${DB}.sha256"

    if [ ! -f "${SQL_FILE}" ]; then
        echo "ERROR: Backup file ${SQL_FILE} not found!"
        exit 1
    fi

    # Checksum validation
    if [ -f "${SHA_FILE}" ] && command -v sha256sum > /dev/null 2>&1; then
        echo "Verifying SHA-256 for ${DB}..."
        EXPECTED=$(cat "${SHA_FILE}" | awk '{print $1}')
        ACTUAL=$(sha256sum "${SQL_FILE}" | awk '{print $1}')
        if [ "${EXPECTED}" != "${ACTUAL}" ]; then
            echo "ERROR: Checksum mismatch for ${DB}!"
            exit 1
        fi
        echo "  [OK] Checksum matched."
    fi

    echo "Terminating connections and restoring ${DB}..."
    docker exec "${CONTAINER_NAME}" psql -U "${DB_USER}" -d postgres -c \
        "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${DB}' AND pid <> pg_backend_pid();" > /dev/null 2>&1 || true

    docker exec -i "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB}" < "${SQL_FILE}"
    echo "  [OK] Successfully restored ${DB}."
done

echo "Multi-database restore completed successfully."
