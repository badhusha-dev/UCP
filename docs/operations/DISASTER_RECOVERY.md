# Disaster Recovery (DR) Plan

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Disaster Recovery Objectives

- **Recovery Point Objective (RPO)**: **< 1 Hour**. Maximum tolerable data loss duration in a catastrophic site failure. Enforced through hourly database snapshots and WAL persistence.
- **Recovery Time Objective (RTO)**: **< 15 Minutes**. Maximum allowable elapsed time to rebuild and restore the complete e-commerce platform on a fresh server.

---

## 2. Disaster Scenarios & Recovery Workflows

### Scenario A: Complete Physical Server Loss (Total Host Failure)
1. **Provision New Host**: Launch an Ubuntu 22.04 LTS instance with at least 16GB RAM and 50GB SSD.
2. **Clone & Install**:
   ```bash
   git clone <repo-url> ecommerce-platform && cd ecommerce-platform
   chmod +x scripts/*.sh
   ./scripts/install.sh --skip-validation
   ```
3. **Restore Data Snapshot**: Copy the latest offsite backup archive from S3 into `devops/backups/` and run:
   ```bash
   ./scripts/restore.sh
   ```
4. **Validate Live Health**:
   ```bash
   ./scripts/smoke-test.sh
   ```

### Scenario B: ZooKeeper Ephemeral Node Retention (`NodeExistsException`)
If the server terminates abruptly without clean container shutdown:
- ZooKeeper retains the lock on `/brokers/ids/1`, preventing Kafka broker from rejoining.
- **Fix**: Restart ZooKeeper first, wait 5 seconds, then restart Kafka:
  ```bash
  docker compose restart zookeeper && sleep 5 && docker compose restart kafka
  ```
