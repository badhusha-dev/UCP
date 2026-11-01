# Container Hardening & Image Security

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Container Hardening Standards

1. **Minimal Base Images**: All backend microservices use `eclipse-temurin:17-jre-alpine` or `21-jre-alpine`, and frontends use `nginx:alpine`. Minimal footprints reduce the attack surface by eliminating unnecessary system utilities (`curl`, `bash`, compiler tools).
2. **Non-Root Execution**: Container processes run under unprivileged system users rather than `root`:
   - In Dockerfiles:
     ```dockerfile
     RUN addgroup -S appgroup && adduser -S appuser -G appgroup
     USER appuser
     ```
3. **No Hardcoded Secrets**: Dockerfiles never copy `.env` files or hardcoded credentials into image layers.
4. **Resource Constraints**: Docker Compose and Kubernetes define explicit CPU and memory limits to prevent "noisy neighbor" resource exhaustion.
