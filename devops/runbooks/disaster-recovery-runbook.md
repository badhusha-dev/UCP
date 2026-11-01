# Enterprise E-Commerce Platform — Disaster Recovery & Business Continuity Runbook

## 1. Executive Summary & Recovery Objectives

This document defines the Disaster Recovery (DR) and Business Continuity Plan (BCP) for the Enterprise Distributed E-Commerce Platform. It establishes strict Recovery Point Objectives (RPO) and Recovery Time Objectives (RTO) across all platform tiers.

| Tier / Component | Target RPO (Data Loss Window) | Target RTO (Downtime Window) | Backup Mechanism | Redundancy Strategy |
| :--- | :--- | :--- | :--- | :--- |
| **Relational Data (PostgreSQL)** | **< 15 minutes** | **< 30 minutes** | Daily pg_dump + WAL Archiving (PITR) | Multi-AZ Standby + Volume Snapshots |
| **Distributed Cache (Redis)** | **< 1 hour** (Transient) | **< 5 minutes** | Redis RDB Snapshots + AOF persistence | 3-Node Multi-AZ Cluster with Auto-Failover |
| **Event Broker (Apache Kafka)** | **0 messages** (Strict) | **< 10 minutes** | Idempotent producer acks=all + MSK Tiered | 3 Brokers across 3 AZs (RF=3, min.isr=2) |
| **Compute Mesh (EKS Microservices)** | **0 state loss** (Stateless) | **< 3 minutes** | GitOps Git repository (ArgoCD) | Multi-AZ Node Groups + Pod Anti-Affinity |

---

## 2. PostgreSQL Disaster Recovery & Database Restoration

### 2.1 Automated Snapshot Verification
Automated backups run daily at 02:00 AM UTC via Kubernetes CronJob `postgres-backup-job` and the script `devops/scripts/backup-databases.ps1`.
Backups are archived with SHA-256 integrity checksums:
```powershell
# Run manual on-demand backup
powershell -ExecutionPolicy Bypass -File devops/scripts/backup-databases.ps1
```

### 2.2 Point-in-Time Recovery (PITR) Procedure
If corruption or accidental data deletion occurs:

1. **Halt Application Traffic to Affected Microservice:**
   ```bash
   kubectl scale deployment order-service --replicas=0 -n ecommerce
   ```

2. **Identify Target Backup Snapshot:**
   Inspect `devops/backups/backup_YYYYMMDD_HHMMSS/manifest.json` and verify checksums:
   ```bash
   sha256sum -c order_db.sql.gz.sha256
   ```

3. **Restore Specific Database:**
   ```powershell
   # Restore all databases from target snapshot
   powershell -ExecutionPolicy Bypass -File devops/scripts/restore-databases.ps1 -SnapshotDir "devops/backups/backup_20261021_235027"
   ```

4. **Verify Schema & Flyway History:**
   ```sql
   SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
   ```

5. **Resume Application Deployment:**
   ```bash
   kubectl scale deployment order-service --replicas=3 -n ecommerce
   ```

---

## 3. Redis In-Memory Cache Recovery & Rehydration

### 3.1 Failure Modes & Recovery Actions
1. **Node Failure:** In AWS ElastiCache, automatic Multi-AZ failover promotes a replica to primary within 15 to 30 seconds. DNS endpoints automatically track the new primary.
2. **Cluster-Wide State Loss:**
   If the Redis cache is entirely flushed or lost, the platform microservices gracefully degrade:
   - `product-service` falls back to querying PostgreSQL directly.
   - Cache misses trigger lazy rehydration:
     ```bash
     # Warm up catalog cache
     curl -s http://localhost:8080/api/products?page=0&size=50 > /dev/null
     ```

---

## 4. Apache Kafka Broker & Consumer Group Recovery

### 4.1 Broker Loss Protocol
- Brokers operate with Replication Factor $RF=3$ and $min.insync.replicas=2$. If 1 broker node fails, the cluster remains fully operational without service disruption.
- Topic metadata automatically rebalances partitions to healthy brokers.

### 4.2 Poison Pill & Dead Letter Queue (DLQ) Recovery
If a message causes consumer crashes:
1. Spring Kafka non-blocking `@RetryableTopic` attempts processing 3 times with exponential backoff.
2. If all attempts fail, the message is routed to `@DltHandler` and persisted in `dead_letter_events`.
3. Inspect and replay via administrative REST endpoint:
   ```bash
   # Inspect DLQ
   curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/notifications/dlq

   # Replay poison pill after bug fix
   curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/notifications/dlq/1/replay
   ```

---

## 5. Multi-AZ Cloud Failover & Disaster Recovery Runbook

1. **Infrastructure Loss Scenario (Availability Zone Outage):**
   - EKS worker nodes across remaining 2 AZs automatically absorb evicted pods via Kubernetes scheduler.
   - PodDisruptionBudgets guarantee minimum available replicas (`minAvailable: 1`).
2. **Region-Wide Cloud Outage:**
   - Standby secondary AWS region infrastructure provisioned via Terraform:
     ```bash
     cd devops/terraform/environments/prod
     terraform apply -var="aws_region=us-west-2"
     ```
   - ArgoCD syncs state to new regional cluster:
     ```bash
     argocd app sync app-of-apps
     ```
   - Route53 DNS latency routing shifts traffic to the active region.
