# Performance & Scalability Architecture

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Latency & Throughput Targets (SLOs)

| Workflow | Target p95 Latency | Target p99 Latency | Scaling Mechanism |
| :--- | :--- | :--- | :--- |
| **Catalog Browsing (Cached)**| < 25 ms | < 50 ms | Redis L2 in-memory cache + Gateway reactive Netty thread pool |
| **Catalog Full-Text Search** | < 120 ms | < 250 ms | PostgreSQL GIN index / ILIKE optimization |
| **User Authentication / JWT**| < 90 ms | < 180 ms | BCrypt thread pool, cached user roles |
| **Order Checkout & Reserve** | < 150 ms | < 300 ms | Atomic row-level SQL lock, async Kafka event dispatch |
| **Notification Dispatch** | Async (< 2 sec) | Async (< 5 sec) | Kafka parallel partition consumers (4 partitions) |

---

## 2. Horizontal Scaling Dimensions

### 2.1 Microservices Scaling (Kubernetes HPA)
All domain microservices (`user-service`, `product-service`, `order-service`) deploy with Horizontal Pod Autoscaler (HPA v2) rules:
- **Min Replicas**: 2
- **Max Replicas**: 10
- **Triggers**: Average CPU utilization > 70% OR Average Memory utilization > 80%.

### 2.2 Database Connection Pool (HikariCP)
To avoid overwhelming PostgreSQL under high concurrency:
- **Maximum Pool Size**: 15 connections per container instance.
- **Minimum Idle**: 5 connections.
- **Connection Timeout**: 30,000 ms.
- **Idle Timeout**: 600,000 ms (10 minutes).

### 2.3 Kafka Partition Scaling
- The `order-events` topic is provisioned with 4 partitions.
- The `notification-group` consumer scales up to 4 parallel worker instances without partition contention.
