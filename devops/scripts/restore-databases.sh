#!/bin/sh
# ==============================================================================
# Phase 14: Automated PostgreSQL Disaster Recovery Restore & Verification (POSIX)
# ==============================================================================

set -e

BACKUP_DIR=$1
CONTAINER_NAME=${CONTAINER_NAME:-"ecommerce-platform-postgres-1"}
DB_USER=${DB_USER:-"ecommerce_admin"}
TARGET_DB=$2

if [ -z "${BACKUP_DIR}" ]; then
    BACKUP_DIR=$(find ./devops/backups -mindepth 1 -maxdepth 1 -type d -name "backup_*" | sort -r | head -n 1)
fi

if [ -z "${BACKUP_DIR}" ]; then
    echo "[ERROR] No valid backup directory found."
    exit 1
fi

echo "================================================================="
echo "   Starting Disaster Recovery Database Restoration               "
echo "================================================================="
echo "Restoring from: ${BACKUP_DIR}"

DATABASES=${TARGET_DB:-"user_db product_db order_db notification_db"}

for DB in ${DATABASES}; do
    SQL_FILE="${BACKUP_DIR}/${DB}.sql"
    HASH_FILE="${BACKUP_DIR}/${DB}.sha256"

    if [ ! -f "${SQL_FILE}" ]; then
        echo "[WARN] Skipping ${DB}, file not found."
        continue
    fi

    # Verify SHA-256 Checksum
    if [ -f "${HASH_FILE}" ]; then
        echo "Verifying SHA-256 for ${DB}..."
        if command -v sha256sum > /dev/null 2>&1; then
            sha256sum -c "${HASH_FILE}"
        fi
    fi

    echo "Restoring ${DB} into container ${CONTAINER_NAME}..."
    docker exec -i "${CONTAINER_NAME}" psql -U "${DB_USER}" -d "${DB}" < "${SQL_FILE}"
    echo "  -> Restored ${DB} successfully."
done

echo "Restoration complete."
