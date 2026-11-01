#!/bin/sh
# ==============================================================================
# Phase 14: Automated PostgreSQL Multi-Database Disaster Recovery Backup (POSIX)
# ==============================================================================

set -e

OUTPUT_DIR=${1:-"./devops/backups"}
CONTAINER_NAME=${CONTAINER_NAME:-"ecommerce-platform-postgres-1"}
DB_USER=${DB_USER:-"ecommerce_admin"}
DATABASES="user_db product_db order_db notification_db"
RETENTION_DAYS=${RETENTION_DAYS:-7}

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
SNAPSHOT_DIR="${OUTPUT_DIR}/backup_${TIMESTAMP}"
mkdir -p "${SNAPSHOT_DIR}"

echo "================================================================="
echo "   Starting Automated Disaster Recovery Database Backup          "
echo "================================================================="
echo "Target directory: ${SNAPSHOT_DIR}"

for DB in ${DATABASES}; do
    echo "Dumping database: ${DB}..."
    SQL_FILE="${SNAPSHOT_DIR}/${DB}.sql"
    docker exec "${CONTAINER_NAME}" pg_dump -U "${DB_USER}" -d "${DB}" > "${SQL_FILE}"
    
    # Generate SHA-256 Checksum
    if command -v sha256sum > /dev/null 2>&1; then
        sha256sum "${SQL_FILE}" > "${SNAPSHOT_DIR}/${DB}.sha256"
    elif command -v shasum > /dev/null 2>&1; then
        shasum -a 256 "${SQL_FILE}" > "${SNAPSHOT_DIR}/${DB}.sha256"
    fi
    echo "  -> Saved ${DB}.sql with SHA-256 checksum."
done

echo "Retention cleanup: removing backups older than ${RETENTION_DAYS} days..."
find "${OUTPUT_DIR}" -mindepth 1 -maxdepth 1 -type d -name "backup_*" -mtime +${RETENTION_DAYS} -exec rm -rf {} + || true

echo "Backup completed successfully in ${SNAPSHOT_DIR}."
