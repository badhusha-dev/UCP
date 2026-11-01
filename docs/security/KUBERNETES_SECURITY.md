# Kubernetes Security & Hardening Guide

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Pod Security Standards (PSS)

The platform enforces the Kubernetes **Restricted** Pod Security Standard on the `ecommerce` namespace:
- `allowPrivilegeEscalation: false`
- `readOnlyRootFilesystem: true` (with tmpfs mounted on `/tmp`)
- `runAsNonRoot: true`
- `capabilities.drop: ["ALL"]`

---

## 2. NetworkPolicies (Microsegmentation)

By default, all ingress and egress across pods in the `ecommerce` namespace are blocked except explicitly allowed routes:
- **`postgres-netpol`**: Allows TCP 5432 ingress only from pods labeled `app=user-service`, `app=product-service`, `app=order-service`, and `app=notification-service`.
- **`redis-netpol`**: Allows TCP 6379 ingress only from `app=product-service`.
- **`api-gateway-netpol`**: Allows ingress on 8080 from the NGINX Ingress Controller.
