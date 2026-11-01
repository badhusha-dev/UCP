#!/usr/bin/env bash
# ==============================================================================
# Universal Commerce Platform - Database & Volume Backup Script
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

OUTPUT_DIR="${1:-"${ROOT_DIR}/devops/backups"}"
CONTAINER_NAME="${CONTAINER_NAME:-"$(docker ps -qf "name=postgres" | head -1)"}"
DB_USER="${POSTGRES_USER:-"postgres"}"
DATABASES="user_db product_db order_db notification_db"
RETENTION_DAYS="${RETENTION_DAYS:-7}"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
SNAPSHOT_DIR="${OUTPUT_DIR}/backup_${TIMESTAMP}"
mkdir -p "${SNAPSHOT_DIR}"

echo "================================================================="
echo "   Universal Commerce Platform - PostgreSQL Backup               "
echo "================================================================="
echo "Snapshot Directory: ${SNAPSHOT_DIR}"

if [ -z "${CONTAINER_NAME}" ]; then
    echo "ERROR: PostgreSQL container is not running!"
    exit 1
fi

for DB in ${DATABASES}; do
    echo "Backing up database: ${DB}..."
    SQL_FILE="${SNAPSHOT_DIR}/${DB}.sql"
    docker exec "${CONTAINER_NAME}" pg_dump -U "${DB_USER}" -d "${DB}" > "${SQL_FILE}"
    
    if command -v sha256sum > /dev/null 2>&1; then
        sha256sum "${SQL_FILE}" > "${SNAPSHOT_DIR}/${DB}.sha256"
    fi
    echo "  [OK] Dumped ${DB}.sql with SHA-256 checksum."
done

cat <<EOF > "${SNAPSHOT_DIR}/manifest.json"
{
  "timestamp": "${TIMESTAMP}",
  "version": "2.0.0",
  "databases": ["user_db", "product_db", "order_db", "notification_db"],
  "format": "plain_sql",
  "status": "COMPLETED"
}
EOF

echo "Cleaning up snapshots older than ${RETENTION_DAYS} days..."
find "${OUTPUT_DIR}" -mindepth 1 -maxdepth 1 -type d -name "backup_*" -mtime +"${RETENTION_DAYS}" -exec rm -rf {} + 2>/dev/null || true

echo "Backup completed successfully in ${SNAPSHOT_DIR}."
