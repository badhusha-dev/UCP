# Backup & Restoration Runbook

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Automated Database Backup Procedure

The platform bundles an automated database backup utility (`scripts/backup.sh`):

```bash
# Execute multi-database backup
bash scripts/backup.sh
```

### Backup Mechanics
1. **Target Directory**: Stored in `devops/backups/backup_YYYYMMDD_HHMMSS/`.
2. **Databases Included**:
   - `user_db.sql`
   - `product_db.sql`
   - `order_db.sql`
   - `notification_db.sql`
3. **Cryptographic Checksums**: Automatically generates a `.sha256` hash alongside each `.sql` dump file to guarantee file integrity.
4. **Automated Pruning**: Backups older than 7 days (`RETENTION_DAYS`) are automatically purged to prevent disk saturation.

---

## 2. Multi-Database Disaster Recovery Restoration

```bash
# Automatically restore from the latest snapshot
bash scripts/restore.sh

# Or specify a targeted snapshot directory explicitly
bash scripts/restore.sh devops/backups/backup_20261022_120000
```

### Safety Controls
- Verifies SHA-256 checksums prior to restoring.
- Terminates existing open connections to PostgreSQL databases before running imports.
- Logs step-by-step progress with explicit confirmation upon completion.
