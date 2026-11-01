# Docker Compose Deployment Guide

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Compose Architecture & Network Topology

The production compose topology defines 23 containers joined on a unified bridge network:
- **Network Name**: `ecommerce-network` (Driver: `bridge`)
- **DNS Resolution**: Containers communicate using service DNS names (`http://api-gateway:8080`, `jdbc:postgresql://postgres:5432/order_db`).
- **No Localhost Cross-Talk**: No container makes localhost calls expecting to reach another container.

---

## 2. Persistent Storage Volumes

Persistent data is protected across container restarts using named Docker volumes:
- `postgres_data`: Stores PostgreSQL database clusters (`/var/lib/postgresql/data`).
- `redis_data`: Persists Redis snapshots (`/data`).
- `elasticsearch_data`: Retains indexed logs and traces (`/usr/share/elasticsearch/data`).

---

## 3. Container Resource Allocations

| Container Tier | Recommended CPU Limit | Memory Limit (RAM) | Memory Reservation |
| :--- | :--- | :--- | :--- |
| **Domain Microservice** (e.g. `order-service`) | 1.0 CPU | 768 MB | 384 MB |
| **API Gateway** | 1.5 CPU | 768 MB | 512 MB |
| **PostgreSQL Database** | 2.0 CPU | 2 GB | 1 GB |
| **Elasticsearch** | 2.0 CPU | 2 GB (Heap: 1GB) | 1 GB |
| **Frontend MFEs** (NGINX) | 0.5 CPU | 128 MB | 64 MB |

---

## 4. Useful Operational Commands

```bash
# View active container statuses
docker compose ps

# View live container resource consumption
docker stats --no-stream

# View logs for a specific microservice with timestamps
docker compose logs -f --tail=100 order-service

# Restart a single degraded microservice
docker compose restart product-service
```
