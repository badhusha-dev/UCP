# Network Architecture & Port Inventory

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Master Port Directory & Firewall Rules

| Port | Service Name | Protocol | Exposure Level | Firewall Rule & Security Policy |
| :--- | :--- | :--- | :--- | :--- |
| **3000** | `shell-app` | HTTP/TCP | **Public (Ingress)** | Allow inbound 80/443 from Public Internet (via Reverse Proxy). |
| **3001** | `mfe-product` | HTTP/TCP | **Public (Ingress)** | Allow inbound; serves remote JavaScript bundles to client browsers. |
| **3002** | `mfe-order` | HTTP/TCP | **Public (Ingress)** | Allow inbound; serves remote JavaScript bundles to client browsers. |
| **3003** | `mfe-user` | HTTP/TCP | **Public (Ingress)** | Allow inbound; serves remote JavaScript bundles to client browsers. |
| **8080** | `api-gateway` | HTTP/TCP | **Public (Ingress)** | Primary API entry point. Public access restricted to `/api/**`. |
| **8081** | `user-service` | HTTP/TCP | **Private (Internal)**| Block external. Allow only from `api-gateway` and internal cluster. |
| **8082** | `product-service` | HTTP/TCP | **Private (Internal)**| Block external. Allow only from `api-gateway` and `order-service`. |
| **8083** | `order-service` | HTTP/TCP | **Private (Internal)**| Block external. Allow only from `api-gateway`. |
| **8084** | `notification-service`| HTTP/TCP| **Private (Internal)**| Block external. Allow only from `api-gateway` (for DLQ management). |
| **8761** | `discovery-server` | HTTP/TCP | **Private (Internal)**| Eureka discovery registry. Internal cluster access only. |
| **8888** | `config-server` | HTTP/TCP | **Private (Internal)**| Spring Cloud Config. Internal cluster access only. |
| **5432** | `postgres` | TCP | **Private (Internal)**| PostgreSQL port. Block from public; allow only from microservices. |
| **6379** | `redis` | TCP | **Private (Internal)**| In-memory cache. Block from public; allow only from `product-service`. |
| **9092** | `kafka` | TCP | **Private (Internal)**| Kafka broker port. Internal messaging bus only. |
| **2181** | `zookeeper` | TCP | **Private (Internal)**| ZooKeeper coordination. Block from all except `kafka`. |
| **8090** | `kafka-ui` | HTTP/TCP | **Restricted Admin** | Kafka management UI. Restrict to VPN / Admin IP ranges. |
| **9090** | `prometheus` | HTTP/TCP | **Restricted Admin** | Metrics scraper. Restrict to SRE monitoring subnets. |
| **3030** | `grafana` | HTTP/TCP | **Restricted Admin** | Observability dashboards. Authenticated SRE access only. |
| **9093** | `alertmanager` | HTTP/TCP | **Restricted Admin** | Alerting webhook manager. Internal SRE network only. |
| **9411** | `zipkin` | HTTP/TCP | **Restricted Admin** | Distributed tracing UI. Internal SRE network only. |
| **9200** | `elasticsearch` | HTTP/TCP | **Private (Internal)**| Log search index. Block from public; allow from `logstash` & `kibana`.|
| **5000** | `logstash` | TCP | **Private (Internal)**| Logback TCP appender socket. Internal cluster only. |
| **5601** | `kibana` | HTTP/TCP | **Restricted Admin** | Log analysis dashboard. Restrict to VPN / Admin IP ranges. |
