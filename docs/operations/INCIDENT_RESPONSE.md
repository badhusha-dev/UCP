# Incident Response & Escalation Protocol

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Incident Severity Classifications

| Severity | Definition & Impact | Response SLA | Target Resolution (MTTR) |
| :--- | :--- | :--- | :--- |
| **P1 - Critical** | Platform-wide outage; checkout completely blocked; database corruption. | < 5 minutes | < 30 minutes |
| **P2 - Major** | Degraded performance; single MFE failing; circuit breaker open; high latency. | < 15 minutes | < 2 hours |
| **P3 - Minor** | Non-critical operational anomaly; DLQ consumer lag; isolated UI bug. | < 1 hour | < 8 hours |
| **P4 - Low** | Cosmetic defect; documentation mismatch; non-blocking query. | Next business day | Next sprint release |

---

## 2. Standard Operating Procedure for P1 Outages

1. **Detection**: Alertmanager fires P1 alert to Slack/PagerDuty; or synthetic prober fails.
2. **War Room Assembly**: SRE Lead, Backend Lead, and Database Admin join the bridge.
3. **Containment**:
   - If bad release: execute `bash scripts/rollback.sh`.
   - If database connection exhaustion: execute `docker compose restart api-gateway`.
   - If node disk full: clean old Elasticsearch logs and backup archives.
4. **Validation**: Run `bash scripts/smoke-test.sh` to confirm 100% recovery.
5. **Post-Mortem**: Document root cause, timeline, contributing factors, and permanent preventive actions within 48 hours.
