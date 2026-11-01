# Quality Gates & CI/CD Governance

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Automated Pipeline Quality Gate Thresholds

Every code commit and pull request must clear six sequential quality gates:

1. **Compilation Gate**:
   - Zero compiler errors or warnings treated as errors (`mvn compile`, TypeScript strict check).
2. **Unit & Contract Test Gate**:
   - 100% of unit tests and OpenAPI contract tests must pass.
3. **Security Vulnerability Gate**:
   - 0 Critical and 0 High CVE vulnerabilities via OWASP Dependency-Check.
4. **Static Code Analysis Gate**:
   - Zero high-priority code smells, bugs, or unhandled exceptions via SonarQube / oxlint.
5. **Container & Compose Validation Gate**:
   - `docker compose config --quiet` must return exit code `0`.
6. **Deployment Smoke Test Gate**:
   - Automated cluster spin-up must pass `scripts/smoke-test.sh` before promoting to staging or production.
