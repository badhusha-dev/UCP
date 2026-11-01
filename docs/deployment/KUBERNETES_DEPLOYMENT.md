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
    -f devops/helm/ecommerce-platform/values-prod.yaml \
    --set secrets.postgresPassword="$POSTGRES_PASSWORD" \
    --set secrets.jwtSecret="$JWT_SECRET"
```

The chart's `values.yaml` holds only `REPLACE_ME` placeholders for secrets. Always pass real values with `--set` or a private values file that is not committed. The placeholder JWT secret is too short to be an HS256 key, so services stop at startup if you forget it.

### Kustomize (plain manifests)

`kubernetes/kustomization.yml` reads `kubernetes/base/02-secrets.yml`. That file is gitignored, so create it from the committed template first:

```bash
cp kubernetes/base/02-secrets.example.yml kubernetes/base/02-secrets.yml
# replace every REPLACE_ME, for example with values from: openssl rand -hex 32
kubectl kustomize kubernetes > /dev/null   # check that the manifests build
kubectl apply -k kubernetes
```

`GRAFANA_ADMIN_PASSWORD` must be present: Grafana reads it from `ecommerce-secrets`. Existing `02-secrets.yml` files created before 2026-11-01 do not have it.

---

## 3. Horizontal Pod Autoscaler (HPA v2)

Dynamic scaling is governed by CPU and memory thresholds:
```bash
# View active autoscalers
kubectl get hpa -n ecommerce
```
Scale-up triggers automatically when microservice CPU exceeds 70% or memory exceeds 80%.
