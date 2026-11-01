# Observability & Distributed Telemetry Architecture

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Unified Telemetry Framework

The Universal Commerce Platform integrates **Distributed Tracing**, **Metrics**, and **Centralized Logging** into a single cohesive telemetry fabric.

```
       ┌────────────────────────────────────────────────────────┐
       │             Microservices Application Tier             │
       └───────┬──────────────────────┬──────────────────┬──────┘
               │                      │                  │
        Traces (OTel)           Metrics (Micrometer)   Logs (TCP)
               │                      │                  │
               ▼                      ▼                  ▼
       [ Zipkin :9411 ]     [ Prometheus :9090 ]   [ Logstash :5000 ]
                                      │                  │
                                      ▼                  ▼
                             [ Grafana :3030 ]     [ Elasticsearch :9200 ]
                                                         │
                                                         ▼
                                                   [ Kibana :5601 ]
```

---

## 2. Distributed Tracing (OpenTelemetry & Zipkin)

- **Library**: `io.micrometer:micrometer-tracing-bridge-otel` + `io.opentelemetry:opentelemetry-exporter-zipkin`.
- **Sampling Rate**: Configured to 100% (`management.tracing.sampling.probability: 1.0`).
- **Context Propagation**:
  - **HTTP**: Propagates W3C `traceparent` headers across API Gateway and inter-service RestTemplate calls.
  - **Kafka**: Propagates trace context headers (`traceparent`) across producer and consumer records.
- **Trace Visualization**: View traces across up to 13 spans at `http://localhost:9411`.

---

## 3. Metrics & Alerting (Prometheus & Grafana)

- **Scrape Configuration**: Prometheus scrapes Spring Boot actuator endpoints every 15s (`devops/prometheus/prometheus.yml`).
- **Key Metrics Monitored**:
  - `http_server_requests_seconds_count` & `max` (Throughput and latency percentiles).
  - `jvm_memory_used_bytes` / `jvm_memory_max_bytes` (Heap utilization).
  - `hikaricp_connections_active` (Database connection pool saturation).
  - `resilience4j_circuitbreaker_state` (Circuit breaker transitions).
- **Grafana Provisioning**: Automated datasource and dashboard loading on port 3030 (`devops/grafana/`).

---

## 4. Centralized Logging (ELK Stack)

- **Appender**: Non-blocking asynchronous TCP socket appender in `logback-spring.xml` targeting Logstash on `5000`.
- **MDC Correlation**: Every log event includes:
  ```json
  {
    "timestamp": "2026-10-22T16:31:40.123Z",
    "service": "order-service",
    "traceId": "4c49615d474c4fce",
    "spanId": "b24c3e0b8d7b7db3",
    "level": "INFO",
    "message": "Order 79 created successfully with total 99.99"
  }
  ```
- **Kibana**: Query and correlate logs by `traceId` at `http://localhost:5601`.
