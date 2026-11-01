# Feature Configuration & Toggle Reference

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Feature Configuration Properties

| Property Key | Default Value | Target Feature | Description |
| :--- | :--- | :--- | :--- |
| `spring.cache.type` | `redis` | Redis Caching (`FT-11`) | Toggles between in-memory Redis or simple local cache. |
| `spring.cache.redis.time-to-live`| `3600000ms` (1h) | Redis Caching (`FT-11`) | Default expiration TTL for cached product catalog records. |
| `management.tracing.sampling.probability`| `1.0` (100%) | Distributed Tracing (`FT-21`)| Sampling probability of traces exported to Zipkin. |
| `spring.kafka.producer.acks` | `all` | Kafka Producer (`FT-15`) | Level of broker acknowledgement required before considering send successful. |
| `spring.kafka.producer.properties.enable.idempotence`| `true` | Kafka Producer (`FT-15`) | Prevents duplicate event dispatch at the Kafka broker layer. |
| `spring.flyway.enabled` | `true` | Migrations | Automatically runs unapplied database migration scripts on startup. |
| `spring.flyway.baseline-on-migrate`| `true` | Migrations | Automatically baselines existing databases if no schema history table exists. |
| `resilience4j.circuitbreaker.configs.default.failureRateThreshold`| `50.0` | Fault Tolerance | Percentage of failed calls before tripping circuit breaker to OPEN state. |
