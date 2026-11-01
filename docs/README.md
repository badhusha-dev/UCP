# Documentation map

[Specification section 20](product/PRODUCT_REQUIREMENTS.md) defines a documentation structure. Most of it already exists under other folder names, and the specification asks for existing documents to be updated rather than duplicated, so this map points each required document at the file that holds it.

When documents disagree, `status/` is current (2026-11-01). Older reports in `completion/`, `quality/`, `handover/`, `delivery/` and `assessment/` record earlier audits and are not updated.

| Required document | Where it lives |
|---|---|
| product/PRODUCT_VISION.md | [product/PRODUCT_REQUIREMENTS.md](product/PRODUCT_REQUIREMENTS.md), section 1 |
| product/PRODUCT_REQUIREMENTS.md | [product/PRODUCT_REQUIREMENTS.md](product/PRODUCT_REQUIREMENTS.md) (specification v1.0, verbatim) |
| product/APPLICATION_CATALOG.md | [product/APPLICATION_CATALOG.md](product/APPLICATION_CATALOG.md) (coverage of every application and module) |
| product/ADVANCED_FEATURE_CATALOG.md | [product/ADVANCED_FEATURE_CATALOG.md](product/ADVANCED_FEATURE_CATALOG.md) |
| product/RELEASE_STRATEGY.md | [product/RELEASE_STRATEGY.md](product/RELEASE_STRATEGY.md) |
| architecture/SYSTEM_ARCHITECTURE.md | [architecture/SYSTEM_ARCHITECTURE.md](architecture/SYSTEM_ARCHITECTURE.md) (also [technical/SYSTEM_ARCHITECTURE.md](technical/SYSTEM_ARCHITECTURE.md)) |
| architecture/SERVICE_BOUNDARIES.md | [architecture/SERVICE_BOUNDARIES.md](architecture/SERVICE_BOUNDARIES.md); service list in [architecture/SERVICE_CATALOG.md](architecture/SERVICE_CATALOG.md) |
| architecture/DATABASE_OWNERSHIP.md | [architecture/DATABASE_OWNERSHIP.md](architecture/DATABASE_OWNERSHIP.md) |
| architecture/API_STANDARDS.md | Specification section 7; current contracts in [architecture/API_CONTRACTS.md](architecture/API_CONTRACTS.md) and [technical/API_REFERENCE.md](technical/API_REFERENCE.md); gaps in [architecture/SERVICE_BOUNDARIES.md](architecture/SERVICE_BOUNDARIES.md#api-conventions-section-7) |
| architecture/EVENT_CATALOG.md | [architecture/EVENT_CATALOG.md](architecture/EVENT_CATALOG.md); outbox in [technical/TRANSACTIONAL_OUTBOX.md](technical/TRANSACTIONAL_OUTBOX.md) |
| architecture/SECURITY_ARCHITECTURE.md | [architecture/SECURITY_ARCHITECTURE.md](architecture/SECURITY_ARCHITECTURE.md); findings in [completion/SECURITY_AUDIT.md](completion/SECURITY_AUDIT.md) |
| architecture/MULTI_TENANCY.md | [completion/MULTI_TENANCY_AUDIT.md](completion/MULTI_TENANCY_AUDIT.md) and [saas/](saas/) |
| modules/commerce/ | [order-control-tower/](order-control-tower/), [pricing-promotions/](pricing-promotions/), [returns-refunds/](returns-refunds/), [storefront-studio/](storefront-studio/) |
| modules/inventory/, modules/procurement/ | [inventory-procurement/](inventory-procurement/) |
| modules/analytics/ | `quality/ADMIN_ANALYTICS_*.md` |
| modules/accounting/, delivery/, crm/, support/ | No module folder yet; status in [product/APPLICATION_CATALOG.md](product/APPLICATION_CATALOG.md) |
| modules/restaurant/, hr/, payroll/, assets/, projects/, ai/ | Not implemented; no folder |
| implementation/IMPLEMENTATION_ROADMAP.md | [status/COMPLETION_ROADMAP.md](status/COMPLETION_ROADMAP.md) (current); [architecture/IMPLEMENTATION_ROADMAP.md](architecture/IMPLEMENTATION_ROADMAP.md) is older |
| implementation/ACCEPTANCE_CRITERIA.md | Specification section 15 and [business/ACCEPTANCE_CRITERIA.md](business/ACCEPTANCE_CRITERIA.md); per-item criteria in [status/COMPLETION_ROADMAP.md](status/COMPLETION_ROADMAP.md) |
| implementation/IMPLEMENTATION_PROGRESS.md | [status/IMPLEMENTATION_PROGRESS.md](status/IMPLEMENTATION_PROGRESS.md) (current); [completion/IMPLEMENTATION_PROGRESS.md](completion/IMPLEMENTATION_PROGRESS.md) is older |
| status/PROJECT_CURRENT_STATUS.md | [status/PROJECT_CURRENT_STATUS.md](status/PROJECT_CURRENT_STATUS.md) |
| status/CHANGELOG_CURRENT.md | [status/CHANGELOG_CURRENT.md](status/CHANGELOG_CURRENT.md) |
| status/INCOMPLETE_FEATURES.md | [status/INCOMPLETE_FEATURES.md](status/INCOMPLETE_FEATURES.md) |
| status/BUGS_AND_TECHNICAL_DEBT.md | [status/BUGS_AND_TECHNICAL_DEBT.md](status/BUGS_AND_TECHNICAL_DEBT.md) |
| status/TEST_AND_VERIFICATION_REPORT.md | [status/TEST_AND_VERIFICATION_REPORT.md](status/TEST_AND_VERIFICATION_REPORT.md) |

Moving files into the exact folder names would break links in the older reports, so it has not been done.
