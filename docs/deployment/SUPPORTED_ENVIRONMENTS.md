# Supported Operating Environments

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Supported Operating Systems & Baselines

| Environment Tier | Operating System | Architecture | Kernel / Version | Support Level |
| :--- | :--- | :--- | :--- | :--- |
| **Production Server (Primary)**| Ubuntu Server LTS | x86_64 (amd64) | 22.04 LTS / 24.04 LTS | Tier 1 (Recommended Production) |
| **Enterprise Server (RHEL)** | Red Hat Enterprise Linux / Rocky | x86_64 (amd64) | 9.x / 8.x | Tier 1 (Enterprise Enterprise) |
| **Cloud Managed Nodes** | AWS Linux 2023 / Debian 12 | x86_64, arm64 | Kernel 6.x | Tier 1 (EKS / GKE Worker Nodes) |
| **Development Node** | Windows 11 Pro / Enterprise | x86_64 | Build 22631+ (WSL2 / Docker Desktop)| Tier 2 (Local Development) |
| **Development Node** | macOS Ventura / Sonoma / Sequoia| Apple Silicon (arm64) | macOS 13+ (OrbStack / Docker Desktop)| Tier 2 (Local Development) |

---

## 2. Container Engine & Orchestration Runtimes

- **Docker Engine**: Version 24.0.0 or higher (Docker Compose v2.20.0+).
- **Kubernetes**: v1.28, v1.29, or v1.30 (EKS, GKE, AKS, or localized K3s/Minikube clusters).
- **Helm**: Helm v3.12.0 or higher for chart deployments.
