# Health Checks & Probing Specification

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Synthetic Health Probe Directory

The platform provides an automated synthetic health script (`scripts/health-check.sh`):

| Probe Target | Protocol / Port | Probe Endpoint URL | Expected Status |
| :--- | :--- | :--- | :--- |
| **Storefront Shell** | HTTP :3000 | `http://localhost:3000/` | HTTP 200 |
| **Product MFE Remote** | HTTP :3001 | `http://localhost:3001/assets/remoteEntry.js`| HTTP 200 |
| **Order MFE Remote** | HTTP :3002 | `http://localhost:3002/assets/remoteEntry.js`| HTTP 200 |
| **User MFE Remote** | HTTP :3003 | `http://localhost:3003/assets/remoteEntry.js`| HTTP 200 |
| **API Gateway** | HTTP :8080 | `http://localhost:8080/actuator/health` | HTTP 200 (`"status":"UP"`) |
| **User Service** | HTTP :8081 | `http://localhost:8081/actuator/health` | HTTP 200 (`"status":"UP"`) |
| **Product Service** | HTTP :8082 | `http://localhost:8082/actuator/health` | HTTP 200 (`"status":"UP"`) |
| **Order Service** | HTTP :8083 | `http://localhost:8083/actuator/health` | HTTP 200 (`"status":"UP"`) |
| **Notification Service** | HTTP :8084 | `http://localhost:8084/actuator/health` | HTTP 200 (`"status":"UP"`) |
| **Eureka Discovery** | HTTP :8761 | `http://localhost:8761/actuator/health` | HTTP 200 (`"status":"UP"`) |
| **Config Server** | HTTP :8888 | `http://localhost:8888/actuator/health` | HTTP 200 (`"status":"UP"`) |
| **Kafka UI** | HTTP :8090 | `http://localhost:8090/actuator/health` | HTTP 200 (`"status":"UP"`) |
| **Prometheus** | HTTP :9090 | `http://localhost:9090/-/healthy` | HTTP 200 |
| **Grafana** | HTTP :3030 | `http://localhost:3030/api/health` | HTTP 200 (`"database":"ok"`) |
| **Zipkin** | HTTP :9411 | `http://localhost:9411/health` | HTTP 200 (`"status":"UP"`) |

---

## 2. Kubernetes Liveness & Readiness Probes

In Kubernetes manifests (`devops/k8s/`):
- **Liveness Probe**: Path `/actuator/health/liveness`, Initial delay: 30s, Period: 10s. If it fails 3 consecutive times, Kubelet restarts the container.
- **Readiness Probe**: Path `/actuator/health/readiness`, Initial delay: 20s, Period: 5s. If it fails, traffic is immediately removed from the Service endpoints.
