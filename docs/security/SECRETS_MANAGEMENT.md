# Secrets Management Strategy

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Secrets Classification & Storage

| Secret Type | Storage Mechanism (Dev) | Storage Mechanism (Production) | Rotation Interval |
| :--- | :--- | :--- | :--- |
| **JWT HMAC-SHA256 Key** | `.env` variable | Kubernetes Secret / HashiCorp Vault | 90 Days |
| **PostgreSQL Passwords** | `.env` variable | Kubernetes Secret / AWS Secrets Manager | 180 Days |
| **Redis Cache Password** | `.env` variable | Kubernetes Secret / Vault | 180 Days |
| **Grafana Admin Password**| `.env` variable | Kubernetes Secret / Vault | 90 Days |
| **TLS Private Keys** | `devops/certs/` | Cert-Manager / AWS ACM | 90 Days (Automated) |

---

## 2. Dynamic Secret Generation

During server installation, `scripts/install.sh --generate-secrets` generates cryptographically secure values using system entropy:
```bash
# JWT Secret (256-bit hex)
openssl rand -hex 32

# Alphanumeric Database Password
openssl rand -base64 18 | tr -dc 'a-zA-Z0-9' | head -c 16
```
Plaintext secrets are never committed to version control repositories.
