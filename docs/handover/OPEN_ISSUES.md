# Open Issues & Operational Advisory Log

**Universal Commerce Platform**  
*Document Version:* 2.0.0  
*Status as of Release:* All Critical & High Blockers Resolved

---

## 1. Non-Blocking Operational Advisories

| Advisory ID | Category | Component | Description & Operational Guidance |
| :--- | :--- | :--- | :--- |
| **ADV-01** | Operations | Kafka / ZooKeeper | On abrupt host power failures, ZooKeeper retains ephemeral broker registrations. Operations should restart ZooKeeper before starting Kafka, or upgrade to KRaft in v2.1.0. |
| **ADV-02** | Telemetry | Zipkin | Tracing spans are sent via non-blocking asynchronous batches. Automated verification tests should pause 1-2 seconds after HTTP requests before polling Zipkin traces. |
| **ADV-03** | Frontend | Module Federation | Avoid adding dependencies that introduce multiple React instances. Keep React 18.3.1 pinned via root `package.json` overrides. |
