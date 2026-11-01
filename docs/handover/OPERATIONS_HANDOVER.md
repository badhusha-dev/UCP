# Operations & SRE Handover Guide

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Operational Overview & Responsibility Matrix

| Area | Responsibility | Tool / Command | Frequency |
| :--- | :--- | :--- | :--- |
| **Synthetic Health** | Verify all 15 endpoints return HTTP 200 | `bash scripts/health-check.sh` | Daily at 08:00 UTC |
| **Cluster Metrics** | Monitor JVM heap, CPU, and p95 latency | Grafana (`http://localhost:3030`) | Continuous / On Alert |
| **Database Backups** | Execute automated backup of all 4 DBs | `bash scripts/backup.sh` | Daily via cron job |
| **Kafka Lag & Topics**| Inspect consumer lag and topic partitions| Kafka UI (`http://localhost:8090`)| Weekly review |
| **Dead Letter Queue** | Inspect poison pills and execute replay | `GET /api/notifications/dlq` | Daily / On Alert |
| **Log Rotation** | Clean elasticsearch indexes older than 14d| Curator / ILM Policy in Kibana | Automated |

---

## 2. Recommended Cron Schedule for Server Maintenance

```bash
# Automated database backup every midnight
0 0 * * * cd /opt/ecommerce-platform && ./scripts/backup.sh >> /var/log/ucp-backup.log 2>&1

# Automated synthetic health check every 15 minutes
*/15 * * * * cd /opt/ecommerce-platform && ./scripts/health-check.sh >> /var/log/ucp-health.log 2>&1
```
