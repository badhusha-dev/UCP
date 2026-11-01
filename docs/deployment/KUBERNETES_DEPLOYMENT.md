# Kubernetes & Helm Production Orchestration Guide

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Kubernetes Architecture & Workloads

In Kubernetes environments (EKS, GKE, AKS, or local K3s), workloads are organized within the `ecommerce` namespace:

- **StatefulSets**:
  - `postgres-0`: PostgreSQL relational storage with PersistentVolumeClaim (PVC).
  - `redis-0`: In-memory Redis cache with PVC.
  - `kafka-0`, `zookeeper-0`: Distributed broker cluster with persistent logs.
- **Deployments (Stateless Microservices)**:
  - `discovery-server`, `config-server` (Replicas: 2).
  - `user-service`, `product-service`, `order-service`, `notification-service` (Replicas: 2 to 10 with HPA).
  - `api-gateway` (Replicas: 3).
  - `frontend-shell`, `frontend-product`, `frontend-order`, `frontend-user` (Replicas: 2).
- **Ingress Controller**: NGINX Ingress Controller routing TLS traffic from domain hostnames to `api-gateway` and `shell-app`.

---

## 2. Helm 3 Deployment Procedure

The platform bundles an enterprise Helm umbrella chart in `devops/helm/ecommerce-platform/`:

```bash
# 1. Inspect and lint the Helm chart
helm lint devops/helm/ecommerce-platform/

# 2. Dry-run template generation for verification
helm template ucp devops/helm/ecommerce-platform/ -f devops/helm/ecommerce-platform/values-prod.yaml

# 3. Install or upgrade release onto cluster
helm upgrade --install ucp devops/helm/ecommerce-platform/ \
    --namespace ecommerce \
    --create-namespace \
    -f devops/helm/ecommerce-platform/values-prod.yaml
```

---

## 3. Horizontal Pod Autoscaler (HPA v2)

Dynamic scaling is governed by CPU and memory thresholds:
```bash
# View active autoscalers
kubectl get hpa -n ecommerce
```
Scale-up triggers automatically when microservice CPU exceeds 70% or memory exceeds 80%.
