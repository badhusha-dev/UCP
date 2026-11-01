# Production Readiness Checklist

`[x]` = verified with evidence in this repo · `[~]` = partial · `[ ]` = not done / not verified.

## Security
- [x] Gateway strips spoofed identity/tenant/service headers (`GatewayHeaderSanitizationTest`)
- [x] Public-endpoint matching is exact/prefix, not substring (`GatewayTenantBoundaryTest`)
- [x] Tenant tokens cannot reach non-tenant services (`GatewayTenantBoundaryTest`; live probes on the redeployed gateway 2026-11-01)
- [~] Service-to-service secret: compose requires it; code still has a public default
- [ ] Every service verifies the JWT itself (7 of 15 do; accounting admin added 2026-11-01)
- [ ] Asymmetric JWT signing, issuer/audience checked everywhere
- [ ] MFA / lockout / refresh-token rotation
- [ ] DAST / dependency scan results reviewed (OWASP job exists in CI, results not reviewed)
- [ ] No PCI claim: no card data is handled because no payment integration exists

## Multi-tenancy
- [~] 6 of 15 services tenant-scoped (accounting admin added 2026-11-01)
- [ ] Tenant resolution at checkout/storefront
- [ ] Tenant id on Kafka events

## Data
- [ ] All migrations applied and validated on Postgres (delivery V1/V2, inventory V4 outstanding)
- [ ] Backup + restore drill
- [~] Accounting immutability (application-level compensating entries only)

## Quality
- [x] Backend unit/integration suites green offline (see TEST_EXECUTION_REPORT)
- [x] Frontend type-check clean (15 apps) and node tests green (76)
- [~] CI covers all modules (edited, not yet run remotely)
- [ ] UI render/browser tests
- [~] Authenticated end-to-end on a running stack (Accounting 21/21 and E-commerce Admin read-only 12/12 live checks with seeded test tenants, 2026-11-01)

## Operations
- [ ] k8s manifests for all services
- [ ] Load test results
- [ ] Runbooks exercised
