# Logging & Monitoring Guide

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Centralized Log Ingestion Pipeline (ELK)

1. **Generation**: Microservices use `net.logstash.logback:logstash-logback-encoder` to format log lines into structured JSON.
2. **Transportation**: Logback `AsyncAppender` connects to Logstash TCP socket on port `5000`.
3. **Indexing**: Logstash parses timestamps, extracts MDC fields, and indexes documents into Elasticsearch (`ecommerce-logs-%{+YYYY.MM.dd}`).
4. **Analysis**: Query logs via Kibana web UI at `http://localhost:5601`.

### Correlating Traces to Logs
To find all log lines associated with a slow or failed request:
1. Locate the `traceId` in Zipkin (`http://localhost:9411`) or the HTTP error response body.
2. Search in Kibana: `traceId: "4c49615d474c4fce"`.
3. Kibana displays the chronological log trail across all microservices involved in that transaction.

---

## 2. Metrics & Dashboard Monitoring (Prometheus & Grafana)

- **Prometheus UI**: `http://localhost:9090` (Scrapes `/actuator/prometheus` every 15s).
- **Grafana UI**: `http://localhost:3030` (Default credentials: `admin` / check `.env`).
- **Provisioned Dashboards**:
  1. *E-Commerce Platform Overview*: Tracks overall HTTP request rate, error rate, p95 latency, active HikariCP database connections, and Kafka message throughput.
  2. *JVM (Micrometer)*: Visualizes JVM heap memory usage, garbage collection pause duration, thread counts, and classloading metrics.
