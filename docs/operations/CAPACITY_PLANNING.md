# Capacity Planning & Sizing Guidelines

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Storage & Memory Sizing Formulas

### Memory Growth per 10,000 Daily Orders
- **PostgreSQL Database Storage**: ~25 MB per 10,000 orders (including order items, tracking records, and notification logs).
- **Elasticsearch Log Growth**: ~150 MB per 10,000 requests (at INFO log level with full trace context).
- **Kafka Log Segment Retention**: 7 days retention requires ~10 GB buffer for high-throughput promotions.

---

## 2. Infrastructure Sizing Matrix

| Traffic Tier | Daily Orders | Concurrent Users | Required Infrastructure | Recommended Topology |
| :--- | :--- | :--- | :--- | :--- |
| **Starter / Dev** | < 1,000 | < 100 | 1 Host (4 CPU, 16 GB RAM) | Single Docker Compose instance |
| **Growth / Mid** | 1,000 - 15,000 | 100 - 1,500 | 3 Kubernetes Nodes (8 vCPU, 32 GB RAM)| EKS / GKE with managed RDS Postgres & ElastiCache |
| **Enterprise / Peak**| 15,000 - 100,000+| 1,500 - 10,000+| 6 to 12 Kubernetes Nodes + Multi-AZ RDS | Multi-AZ EKS cluster with HPA auto-scaling |
