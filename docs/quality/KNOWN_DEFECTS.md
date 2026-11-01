# Known Defects & Workaround Log

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Tracked Defects & Operational Workarounds

| Defect ID | Severity | Component | Defect Description | Workaround | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **DEF-01** | Low | Zipkin Exporter | OpenTelemetry asynchronous span flushing introduces a 1000-2000ms delay between HTTP request completion and trace visibility in the Zipkin UI. | Allow 2 seconds after transaction completion before polling Zipkin API in automated tests. | Expected Behavior |
| **DEF-02** | Medium | ZooKeeper / Kafka | If the Docker daemon abruptly stops without clean shutdown, ZooKeeper retains the ephemeral `/brokers/ids/1` znode, causing Kafka to crash on immediate restart. | Restart ZooKeeper first, wait 5 seconds, then restart Kafka. Documented in Disaster Recovery runbook. | Workaround Active |
| **DEF-03** | Low | Checkout API Contract | `POST /api/orders/checkout` expects the `price` attribute within each `CartItemDto`. Submitting without price causes an NPE in price calculation. | Ensure client cart items always serialize `price` from catalog data before submitting checkout request. | Documented Contract |
