# Production Security Checklist

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Pre-Deployment Security Verification Checklist

- [x] **No Hardcoded Credentials**: Source code scanned with zero plaintext passwords, API keys, or private certificates committed.
- [x] **Strong Secrets Enforced**: `JWT_SECRET`, `POSTGRES_PASSWORD`, and `GRAFANA_ADMIN_PASSWORD` generated with cryptographically strong entropy via `install.sh --generate-secrets`.
- [x] **Database Isolation**: PostgreSQL port `5432` and Redis port `6379` restricted to internal container network; blocked from public WAN.
- [x] **Spring Actuator Hardened**: Management endpoints secured behind API Gateway; sensitive actuator paths (`/actuator/env`, `/actuator/heapdump`) disabled in production profiles.
- [x] **SQL Injection Mitigation**: 100% of database interactions executed via Spring Data JPA / Hibernate parameterized queries.
- [x] **Cross-Site Scripting (XSS)**: React 18 auto-escapes rendered variables in all microfrontends.
- [x] **CORS Policies Enforced**: API Gateway restricts allowed origins to known frontend domains.
- [x] **Flyway Migrations Validated**: Schema auto-updates disabled (`ddl-auto: validate`) to prevent accidental DDL mutations.
