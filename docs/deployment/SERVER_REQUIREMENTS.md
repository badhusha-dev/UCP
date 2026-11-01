# Server Hardware & System Requirements

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Single-Server Standalone Requirements (Docker Compose)

Running all 23 containers simultaneously (Domain Microservices, Databases, Kafka, and full ELK/Observability):

| Resource | Minimum (Core Stack Only) | Recommended (Full Production Stack) |
| :--- | :--- | :--- |
| **CPU** | 2 Logical Cores | 4 to 8 Logical Cores (Intel Xeon / AMD EPYC / Modern i7/i9) |
| **Memory (RAM)** | 8 GB System RAM | 16 GB to 32 GB System RAM (DDR4 / DDR5) |
| **Disk Storage** | 20 GB Free Disk Space | 50 GB to 100 GB High-Speed NVMe SSD |
| **Network** | 100 Mbps Ethernet | 1 Gbps / 10 Gbps with outbound internet for image registry |

---

## 2. Multi-Node Kubernetes Production Requirements

| Node Pool Type | Node Quantity | Specifications per Node | Total Capacity |
| :--- | :--- | :--- | :--- |
| **Control Plane** | 3 Nodes | 2 vCPU, 4 GB RAM (Managed EKS/GKE) | High-Availability Etcd |
| **Microservices Worker Pool**| 3 to 6 Nodes | 4 vCPU, 16 GB RAM (`m6i.xlarge`) | 12-24 vCPU, 48-96 GB RAM |
| **Data / Storage Worker Pool**| 3 Nodes | 4 vCPU, 32 GB RAM, Dedicated EBS (`r6i.xlarge`)| In-memory caching & Kafka brokers |
