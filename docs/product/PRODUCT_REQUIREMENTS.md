<!-- Product specification v1.0 supplied by the product owner on 2026-11-01, kept verbatim. Implementation status of each part: APPLICATION_CATALOG.md, RELEASE_STRATEGY.md and ../architecture/SERVICE_BOUNDARIES.md. -->

# UNIVERSAL COMMERCE PLATFORM (UCP)
# Complete ERP + Commerce SaaS Product Specification

**Document type:** Product Requirements + Solution Architecture + Implementation Roadmap  
**Product:** Universal Commerce Platform  
**Version:** 1.0  
**Date:** November 2026  
**Product model:** Multi-tenant SaaS  
**Target market:** Global businesses  
**Business model:** Modular ERP + Commerce with optional subscriptions and add-on modules

---

# 1. PRODUCT VISION

## 1.1 Vision

Build a unified business operating platform that allows businesses to manage their entire operation from one integrated system.

UCP must support:

- Retail businesses.
- E-commerce businesses.
- Restaurants and food businesses.
- Wholesalers and distributors.
- Multi-vendor marketplaces.
- Service-based businesses.
- Multi-branch businesses.
- Medium and large enterprises.

A business should be able to activate only the modules it needs and expand its subscription as its operations grow.

## 1.2 Product positioning

UCP combines capabilities typically found in:

- ERP platforms.
- E-commerce platforms.
- Retail POS systems.
- Restaurant management systems.
- Accounting software.
- Warehouse management systems.
- CRM platforms.
- HR and payroll software.
- Business intelligence systems.
- Customer support platforms.

UCP must not become a collection of disconnected applications. All modules must operate using common identity, tenant, organization, branch, customer, supplier, product and financial foundations.

## 1.3 Product principles

1. Multi-tenant by design.
2. Modular application architecture.
3. API-first backend.
4. Domain-owned data.
5. Secure by default.
6. Event-driven integration where appropriate.
7. Configurable business workflows.
8. Auditable financial transactions.
9. Mobile-responsive user experience.
10. Extensible through integrations and plugins.
11. Support retail, restaurant, B2B and B2C workflows.
12. AI-assisted operations with human approval for sensitive actions.

---

# 2. PRODUCT AND DEPLOYMENT MODEL

## 2.1 SaaS architecture

UCP will operate as a shared multi-tenant SaaS platform.

Example:

- Tenant A: Retail business.
- Tenant B: Restaurant chain.
- Tenant C: Online fashion store.
- Tenant D: Wholesale distributor.
- Tenant E: Multi-branch supermarket.

All tenants share platform infrastructure while maintaining strict data isolation.

## 2.2 Tenant organizational hierarchy

Recommended logical hierarchy:

Platform
- Tenant
  - Legal Entity
    - Business Unit
      - Branch
        - Department
          - Warehouse / Store / Restaurant Location

Important distinctions:

- Tenant represents the subscribing business or customer organization.
- Legal entity represents a registered company.
- Branch represents a physical or operational location.
- Warehouse represents a stock-holding location.
- Application represents an enabled UCP application.
- User membership determines which tenant a user belongs to.
- Application grants determine which applications a user can access.

A user may belong to multiple tenants and have different permissions in each tenant.

## 2.3 Subscription model

Support:

- Free trial.
- Starter.
- Professional.
- Business.
- Enterprise.
- Custom enterprise contracts.

Subscription configuration must support:

- Enabled modules.
- User limits.
- Branch limits.
- Warehouse limits.
- API request limits.
- Storage limits.
- Monthly transaction limits.
- AI usage limits.
- Subscription renewal.
- Upgrade and downgrade.
- Grace periods.
- Trial expiry.
- Billing history.
- Invoice generation.
- Payment status.

Entitlements must be checked by the backend. Hiding a frontend menu is not sufficient authorization.

---

# 3. APPLICATION ECOSYSTEM

Preserve the existing ten applications.

## APP 1: SUPER ADMIN

Purpose: Operate and govern the UCP SaaS platform.

### Feature catalog

**A. Platform Dashboard**
- Total tenants.
- Active tenants.
- Trial tenants.
- Suspended tenants.
- Subscription revenue.
- Subscription distribution.
- Platform usage.
- API consumption.
- Storage usage.
- Application usage.
- Platform service health.

**B. Tenant Management**
- Create tenant.
- Update tenant.
- Suspend and reactivate tenant.
- Tenant onboarding.
- Tenant verification.
- Tenant configuration.
- Legal entity configuration.
- Branch configuration.
- Tenant activity history.
- Tenant data export and offboarding.

**C. Application Registry**
- Register applications.
- Application metadata.
- Application versions.
- Application status.
- Application URLs.
- Application permissions.
- Application dependencies.
- Application feature entitlements.

**D. Subscription Management**
- Plan creation.
- Plan configuration.
- Add-on management.
- Subscription assignment.
- Subscription renewal.
- Billing history.
- Usage-based billing.
- Trial management.
- Payment reconciliation.

**E. Security and Governance**
- Platform roles.
- Global permissions.
- Administrative audit logs.
- Security events.
- Login history.
- Suspicious activity.
- Session management.
- Emergency tenant suspension.

**F. SaaS Operations**
- Platform configuration.
- Feature flags.
- Maintenance mode.
- System announcements.
- Service health.
- Tenant usage monitoring.

### Main frontend screens

- Super Admin Dashboard.
- Tenant Directory.
- Tenant Details.
- Subscription Plans.
- Subscription Management.
- Application Registry.
- Feature Entitlements.
- Platform Users.
- Platform Roles.
- Audit Center.
- Platform Settings.
- System Health.

---

## APP 2: E-COMMERCE ADMIN

Purpose: Provide the central business administration experience.

### Feature catalog

**A. Product Management**
- Product categories.
- Brands.
- Product attributes.
- Variants.
- SKU management.
- Product images and documents.
- Product bundles.
- Product status.
- Product import/export.
- Product approval workflow.

**B. Pricing and Promotions**
- Price lists.
- Branch-specific pricing.
- Customer-group pricing.
- Scheduled pricing.
- Discount campaigns.
- Coupon management.
- Volume discounts.
- Bundle pricing.
- Promotion stacking rules.
- Tax-inclusive and tax-exclusive prices.

**C. Order Management**
- Order search.
- Order details.
- Order timeline.
- Order cancellation.
- Order amendments.
- Split orders.
- Partial fulfillment.
- Return initiation.
- Exception management.
- Bulk operations.

**D. Store Management**
- Store configuration.
- Store branding.
- Domain mapping.
- Homepage builder.
- Landing pages.
- Navigation.
- Banner management.
- SEO configuration.
- Content versioning.

**E. Business Configuration**
- Business profile.
- Branch settings.
- Tax configuration.
- Currency configuration.
- Business hours.
- Order policies.
- Invoice configuration.
- Shipping configuration.

**F. Sales Intelligence**
- Revenue dashboard.
- Sales trends.
- Product performance.
- Customer performance.
- Conversion analytics.
- Abandoned carts.
- Sales forecasts.

### Main frontend screens

- Admin Dashboard.
- Product Catalog.
- Product Editor.
- Category Management.
- Pricing Management.
- Promotion Management.
- Order Control Tower.
- Storefront Studio.
- Business Settings.
- Sales Analytics.

---

## APP 3: STOREFRONT

Purpose: Customer-facing commerce experience.

### Feature catalog

**A. Storefront Experience**
- Home page.
- Product listing.
- Product details.
- Category browsing.
- Advanced search.
- Filters and sorting.
- Product comparison.
- Wishlist.
- Product reviews.
- Recently viewed products.

**B. Shopping**
- Shopping cart.
- Persistent cart.
- Guest checkout.
- Registered customer checkout.
- Multiple addresses.
- Shipping selection.
- Tax calculation.
- Coupon application.
- Payment selection.
- Order confirmation.

**C. Customer Account**
- Registration.
- Login.
- Profile.
- Address book.
- Order history.
- Order tracking.
- Returns.
- Refund status.
- Saved payment references through payment providers.

**D. Customer Growth**
- Loyalty points.
- Referral program.
- Gift cards.
- Store credits.
- Memberships.
- Product subscriptions.
- Personalized recommendations.

**E. B2B Commerce**
- Business account registration.
- Customer-specific price lists.
- Bulk ordering.
- Minimum order quantity.
- Purchase orders.
- Credit terms, subject to approval.
- Repeat ordering.
- Quotation requests.

**F. Marketplace Capabilities**
- Multiple sellers.
- Seller storefronts.
- Seller catalog.
- Seller availability.
- Seller commission.
- Marketplace order allocation.

---

## APP 4: INVENTORY MANAGEMENT

Purpose: Manage stock, warehouses and inventory operations.

### Feature catalog

**A. Inventory Core**
- Stock balances.
- Stock movements.
- Stock adjustments.
- Stock reservations.
- Stock release.
- Stock transfers.
- Stock counts.
- Reconciliation.
- Reorder thresholds.

**B. Warehouse Management**
- Multiple warehouses.
- Warehouse zones.
- Bins and shelves.
- Putaway.
- Picking.
- Packing.
- Dispatch.
- Warehouse transfers.

**C. Product Tracking**
- Batch numbers.
- Serial numbers.
- Expiry dates.
- Manufacturing dates.
- FIFO.
- FEFO.
- Weighted average costing.
- Barcode and QR support.

**D. Procurement Integration**
- Purchase requisitions.
- Purchase order receiving.
- Goods receipt.
- Supplier returns.
- Purchase discrepancies.

**E. Intelligence**
- Low-stock alerts.
- Overstock alerts.
- Stock aging.
- Dead-stock analysis.
- Demand forecasting.
- Replenishment suggestions.

### Main frontend screens

- Inventory Dashboard.
- Stock Overview.
- Warehouse Management.
- Stock Movements.
- Stock Transfers.
- Stock Adjustments.
- Stock Counts.
- Reservation Management.
- Barcode Scanner.
- Replenishment.

---

## APP 5: ACCOUNTING AND FINANCE

Purpose: Provide full accounting and financial control.

This is a core ERP domain and must not be treated as a simple invoice-management feature.

### Feature catalog

**A. General Ledger**
- Chart of accounts.
- Account groups.
- Journal entries.
- Journal approval.
- Double-entry accounting.
- General ledger.
- Trial balance.
- Accounting periods.
- Period closing.
- Opening balances.

**B. Accounts Receivable**
- Customer invoices.
- Credit notes.
- Customer payments.
- Payment allocation.
- Outstanding balances.
- Aging reports.
- Customer statements.
- Receivables reconciliation.

**C. Accounts Payable**
- Supplier invoices.
- Debit notes.
- Supplier payments.
- Payment allocation.
- Supplier statements.
- Outstanding balances.
- Payables aging.
- Payment approvals.

**D. Cash and Banking**
- Bank accounts.
- Cash accounts.
- Bank transactions.
- Bank reconciliation.
- Cash flow statements.
- Petty cash.
- Payment vouchers.
- Receipt vouchers.

**E. Tax Management**
- Tax codes.
- Tax rates.
- Tax groups.
- Tax rules.
- Tax reports.
- GST configuration for India.
- Tax invoice numbering.
- Credit and debit note handling.
- Tax period closing.

**F. Fixed Assets**
- Asset registration.
- Asset categories.
- Asset acquisition.
- Depreciation schedules.
- Asset transfers.
- Asset disposal.
- Asset maintenance integration.

**G. Budgeting**
- Budget creation.
- Department budgets.
- Branch budgets.
- Expense budgets.
- Budget vs actual.
- Variance analysis.

**H. Financial Reporting**
- Profit and loss.
- Balance sheet.
- Cash flow.
- Trial balance.
- General ledger.
- Accounts receivable aging.
- Accounts payable aging.
- Tax reports.
- Branch profitability.
- Consolidated financial statements.

**I. Financial Controls**
- Journal approval.
- Segregation of duties.
- Posting locks.
- Audit history.
- Reversal entries.
- Period controls.

### Accounting invariants

- Every posted journal must balance.
- Posted transactions must not be silently edited or deleted.
- Corrections must use reversals or adjustment entries.
- Financial entries must retain source transaction references.
- Each legal entity must have its own accounting configuration.
- Currency and exchange-rate treatment must be explicit.
- Accounting must consume confirmed business events, not untrusted frontend calculations.

---

## APP 6: DELIVERY AND LOGISTICS

Purpose: Manage fulfillment, shipment and delivery operations.

### Feature catalog

- Delivery orders.
- Dispatch planning.
- Driver management.
- Fleet management.
- Vehicle assignment.
- Route planning.
- Delivery zones.
- Shipment tracking.
- Delivery status timeline.
- Proof of delivery.
- OTP verification.
- Photo and signature capture.
- Failed delivery handling.
- Return pickup.
- Delivery partner integration.
- Delivery cost management.
- Driver settlements.
- Delivery analytics.

### Driver mobile experience

- Driver login.
- Assigned deliveries.
- Navigation handoff.
- Delivery status updates.
- Customer contact.
- OTP verification.
- Proof of delivery.
- Offline status capture with safe synchronization.

---

## APP 7: VENDOR / RESTAURANT MANAGEMENT

Purpose: Support restaurants, food businesses, marketplace vendors and branch operators.

### Restaurant capabilities

**A. Menu Management**
- Menus.
- Categories.
- Products.
- Modifiers.
- Add-ons.
- Variants.
- Meal combinations.
- Pricing.
- Availability schedules.

**B. Restaurant POS**
- Table orders.
- Takeaway orders.
- Delivery orders.
- Split bills.
- Discounts.
- Receipts.
- Cashier shifts.
- Cash reconciliation.

**C. Kitchen Management**
- Kitchen Display System.
- Preparation queues.
- Kitchen stations.
- Preparation timers.
- Order status.
- Item availability.
- Delayed order alerts.

**D. Table Management**
- Floor plans.
- Tables.
- Reservations.
- Table status.
- Table transfers.
- Waitlist.

**E. Recipe and Ingredient Management**
- Recipes.
- Ingredient quantities.
- Recipe costing.
- Ingredient consumption.
- Wastage.
- Food cost percentage.
- Stock deductions.

**F. Vendor Management**
- Vendor onboarding.
- Vendor catalog.
- Vendor orders.
- Commission configuration.
- Settlement statements.
- Vendor performance.
- Vendor disputes.

**G. Branch Operations**
- Branch menus.
- Branch pricing.
- Branch stock.
- Branch opening hours.
- Branch employees.
- Branch reports.

---

## APP 8: CRM

Purpose: Manage customer relationships, sales and retention.

### Feature catalog

**A. Customer Management**
- Customer profiles.
- Customer history.
- Customer addresses.
- Customer preferences.
- Customer activity.
- Customer notes.
- Customer segmentation.

**B. Sales CRM**
- Leads.
- Opportunities.
- Sales pipeline.
- Follow-up tasks.
- Sales representatives.
- Sales targets.
- Sales activity.

**C. Marketing Automation**
- Email campaigns.
- SMS campaigns.
- WhatsApp integrations.
- Campaign scheduling.
- Audience selection.
- Consent management.
- Campaign analytics.

**D. Loyalty**
- Loyalty programs.
- Points.
- Tiers.
- Rewards.
- Referral tracking.
- Gift vouchers.
- Redemption rules.

**E. Customer Intelligence**
- Customer lifetime value.
- Repeat purchase rate.
- Churn indicators.
- Customer cohorts.
- Product affinity.
- Customer satisfaction.

---

## APP 9: TESTER / QUALITY CENTER

Purpose: Manage software quality, testing and release confidence for the UCP platform.

### Feature catalog

- Test project management.
- Test suites.
- Test cases.
- Test execution.
- Test plans.
- Bug management.
- Defect lifecycle.
- API test automation.
- UI test automation.
- Regression suites.
- Performance testing.
- Security test tracking.
- Release quality gates.
- Test reports.
- Build history.
- CI/CD integration.

Tester permissions must not automatically provide access to tenant business data or production financial records.

---

## APP 10: CUSTOMER SUPPORT

Purpose: Centralize customer service and issue resolution.

### Feature catalog

- Support tickets.
- Ticket categories.
- Priority management.
- Assignment rules.
- SLA policies.
- Escalation.
- Email integration.
- Live chat.
- WhatsApp integration.
- Customer history.
- Knowledge base.
- FAQs.
- Customer feedback.
- Support analytics.
- AI-assisted response suggestions.

Support agents must only access customer information needed for their authorized support tasks.

---

# 4. ADDITIONAL ERP MODULES

Do not create unnecessary new frontend applications. Add these as modules inside existing applications, with dedicated backend ownership where appropriate.

## 4.1 HR AND PAYROLL

Recommended application placement: Super Admin for platform employees; E-commerce Admin or a future Business Operations workspace for tenant HR.

Feature catalog:

- Employee profiles.
- Employee documents.
- Departments.
- Designations.
- Reporting hierarchy.
- Employment contracts.
- Attendance.
- Shifts.
- Leave management.
- Holidays.
- Timesheets.
- Payroll periods.
- Salary structures.
- Allowances.
- Deductions.
- Tax deductions.
- Payslips.
- Payroll approval.
- Payroll disbursement integration.
- Reimbursements.
- Employee self-service.
- HR reports.

Important:

Payroll is sensitive. Salary information must be permission-restricted. Payroll calculation must be versioned and auditable.

Country-specific payroll rules must be configurable through jurisdiction-specific modules. Do not assume one calculation model works globally.

## 4.2 PROCUREMENT AND SUPPLIER MANAGEMENT

Recommended placement: Inventory and E-commerce Admin.

Feature catalog:

- Supplier onboarding.
- Supplier verification.
- Supplier categories.
- Supplier contracts.
- Purchase requisitions.
- Approval workflows.
- RFQs.
- Supplier quotations.
- Quotation comparison.
- Purchase orders.
- Goods receipt.
- Supplier invoices.
- Supplier returns.
- Supplier ratings.
- Supplier payment status.

## 4.3 PROJECT AND TASK MANAGEMENT

Recommended placement: A Business Operations workspace or E-commerce Admin.

Feature catalog:

- Projects.
- Teams.
- Tasks.
- Task dependencies.
- Milestones.
- Time tracking.
- Timesheets.
- Project budgets.
- Approvals.
- Resource allocation.
- Project reports.

## 4.4 ASSET MANAGEMENT

Recommended placement: Accounting and Finance.

Feature catalog:

- Asset registry.
- Asset categories.
- Asset acquisition.
- Depreciation.
- Asset assignment.
- Asset transfers.
- Maintenance.
- Warranty.
- Disposal.
- Asset reconciliation.

## 4.5 DOCUMENT MANAGEMENT

Shared platform capability:

- Document upload.
- Versioning.
- Metadata.
- Document categories.
- Access policies.
- Approval status.
- Retention rules.
- Secure download.
- Audit trail.

Use object storage for files. Do not store large documents directly in relational database records.

## 4.6 WORKFLOW AUTOMATION

Shared platform capability:

- Workflow definitions.
- Trigger conditions.
- Approval steps.
- Role-based routing.
- Timeouts.
- Escalations.
- Notifications.
- Workflow history.
- Retry handling.

Never allow arbitrary code execution through user-configurable workflows.

## 4.7 BUSINESS INTELLIGENCE

Shared platform capability:

- Executive dashboards.
- Sales analytics.
- Inventory analytics.
- Financial analytics.
- Customer analytics.
- Employee analytics.
- Branch comparisons.
- Profitability.
- Custom reports.
- Scheduled reports.

Analytics must use authorized, tenant-scoped data and should not overload transactional databases.

---

# 5. TECHNICAL ARCHITECTURE

## 5.1 Technology stack

Retain the current architecture where practical.

### Backend

- Java 17 or newer compatible with the existing repository.
- Spring Boot 3.x.
- Spring Cloud.
- Spring Security.
- Spring Data JPA.
- Hibernate.
- PostgreSQL.
- Flyway.
- Apache Kafka.
- Redis.
- Maven.
- OpenAPI.

### Frontend

- React 18.
- TypeScript.
- Vite.
- Redux Toolkit.
- React Router.
- Material UI.
- Module Federation.
- Shared component library.
- Nginx.

### Infrastructure

- Docker.
- Docker Compose for local development.
- Kubernetes for scalable deployment.
- Prometheus.
- Grafana.
- OpenTelemetry.
- Distributed tracing.
- Centralized logging.
- Object storage.
- CI/CD pipelines.

Do not upgrade major framework versions without compatibility assessment.

## 5.2 Logical service boundaries

The following is the target domain ownership model. Reuse existing services where their responsibility is already appropriate. Do not create duplicate services simply to match these names.

### Platform services

1. API Gateway
2. Identity and User Service
3. Tenant Service
4. Subscription and Entitlement Service
5. Application Registry Service
6. Notification Service
7. Audit Service
8. Configuration Service
9. Document Service
10. Workflow Service

### Commerce services

11. Catalog Service
12. Pricing and Promotion Service
13. Cart Service
14. Order Service
15. Payment Service
16. Return and Refund Service
17. Storefront Content Service
18. Marketplace / Vendor Service

### Operations services

19. Inventory Service
20. Warehouse Service, if inventory complexity justifies separation
21. Procurement Service
22. Supplier Service
23. Delivery Service
24. Restaurant / POS Service

### Enterprise services

25. Accounting Service
26. HR Service
27. Payroll Service
28. Asset Service
29. Project Management Service
30. CRM Service
31. Customer Support Service
32. Analytics / Reporting Service
33. AI Orchestration Service
34. Integration / Webhook Service

Some of these may initially be modules within existing services. Extract them only when scale, ownership or release independence justifies it.

---

# 6. DATABASE OWNERSHIP

Each service owns its schema or database boundary. Cross-service relationships must use identifiers and APIs/events, not cross-service foreign keys.

| Service | Main tables / entities |
|---|---|
| Identity | users, credentials, sessions, memberships |
| Tenant | tenants, legal_entities, branches, departments |
| Subscription | plans, subscriptions, invoices, entitlements, usage_records |
| Application Registry | applications, app_versions, app_features |
| Catalog | products, variants, categories, attributes, brands |
| Pricing | price_lists, prices, campaigns, coupons, promotion_rules |
| Cart | carts, cart_items, cart_sessions |
| Order | orders, order_items, order_status_history, order_addresses |
| Payment | payment_intents, payment_transactions, payment_attempts, refunds |
| Inventory | inventory_items, stock_balances, reservations, movements, stock_counts |
| Warehouse | warehouses, locations, bins, picking_tasks |
| Procurement | purchase_requisitions, purchase_orders, goods_receipts |
| Supplier | suppliers, supplier_contacts, supplier_contracts |
| Delivery | shipments, deliveries, routes, drivers, vehicles |
| Restaurant | menus, menu_items, modifiers, tables, kitchen_orders, recipes |
| Accounting | chart_of_accounts, journals, journal_lines, invoices, payments, tax_codes |
| HR | employees, departments, contracts, attendance, leave_requests |
| Payroll | payroll_periods, salary_components, payroll_runs, payslips |
| CRM | customers, leads, opportunities, campaigns, loyalty_accounts |
| Support | tickets, conversations, assignments, sla_policies |
| Asset | assets, depreciation_schedules, maintenance_records |
| Project | projects, tasks, milestones, timesheets |
| Document | documents, document_versions, document_access |
| Workflow | workflow_definitions, workflow_instances, approval_tasks |
| Notification | notification_templates, notifications, delivery_attempts |
| Audit | audit_events, security_events |
| Analytics | report_definitions, aggregates, scheduled_reports |

## 6.1 Tenant data isolation

Tenant-owned tables must carry tenant identity or inherit it through a securely enforced ownership relationship.

Recommended rules:

- Use tenant_id on tenant-owned business records.
- Use composite uniqueness such as tenant_id + business_key.
- Scope every repository query.
- Use PostgreSQL Row-Level Security as an additional defense where suitable.
- Do not rely on frontend-supplied tenant_id.
- Derive tenant context from authenticated membership and validated authorization.
- Use tenant-aware cache keys.
- Include tenant context in event envelopes.
- Ensure document access checks include tenant ownership.

Platform-level tables such as global country lists and supported currencies may be shared.

## 6.2 Migration strategy

- Never rewrite an applied Flyway migration.
- Use expand-and-contract migrations.
- Backfill legacy tenant ownership deterministically.
- Validate orphaned records.
- Add tenant-aware constraints after backfill.
- Add indexes for tenant-scoped query patterns.
- Test upgrade from the current database version.
- Test rollback strategy where feasible.

---

# 7. API ARCHITECTURE

## 7.1 API conventions

Base path:

`/api/v1`

Use REST for synchronous business operations and Kafka for asynchronous domain integration.

Standards:

- OpenAPI specifications.
- Consistent error response.
- Pagination.
- Filtering.
- Sorting.
- Idempotency keys for financial and retryable operations.
- Request correlation IDs.
- Explicit authorization.
- API versioning.
- Rate limiting.
- Validation.
- Audit logging.

Do not trust client-supplied user roles, prices, payment status, tenant identity or accounting values.

## 7.2 Main API groups

### Platform

- `/api/v1/auth/**`
- `/api/v1/users/**`
- `/api/v1/tenants/**`
- `/api/v1/memberships/**`
- `/api/v1/applications/**`
- `/api/v1/entitlements/**`
- `/api/v1/subscriptions/**`
- `/api/v1/audit/**`

### Commerce

- `/api/v1/products/**`
- `/api/v1/categories/**`
- `/api/v1/pricing/**`
- `/api/v1/promotions/**`
- `/api/v1/carts/**`
- `/api/v1/orders/**`
- `/api/v1/payments/**`
- `/api/v1/refunds/**`
- `/api/v1/storefront/**`
- `/api/v1/vendors/**`

### Operations

- `/api/v1/inventory/**`
- `/api/v1/warehouses/**`
- `/api/v1/procurement/**`
- `/api/v1/suppliers/**`
- `/api/v1/deliveries/**`
- `/api/v1/restaurant/**`
- `/api/v1/pos/**`

### ERP

- `/api/v1/accounting/**`
- `/api/v1/ledger/**`
- `/api/v1/receivables/**`
- `/api/v1/payables/**`
- `/api/v1/tax/**`
- `/api/v1/budgets/**`
- `/api/v1/employees/**`
- `/api/v1/attendance/**`
- `/api/v1/leave/**`
- `/api/v1/payroll/**`
- `/api/v1/assets/**`
- `/api/v1/projects/**`

### Customer and support

- `/api/v1/crm/**`
- `/api/v1/customers/**`
- `/api/v1/campaigns/**`
- `/api/v1/loyalty/**`
- `/api/v1/support/**`
- `/api/v1/tickets/**`

### Shared services

- `/api/v1/documents/**`
- `/api/v1/workflows/**`
- `/api/v1/notifications/**`
- `/api/v1/reports/**`
- `/api/v1/analytics/**`
- `/api/v1/integrations/**`
- `/api/v1/webhooks/**`
- `/api/v1/ai/**`

These are API contracts to implement progressively, not a requirement to expose all endpoints immediately.

## 7.3 Representative API operations

### Order

- POST `/api/v1/orders`
- GET `/api/v1/orders/{orderId}`
- GET `/api/v1/orders`
- POST `/api/v1/orders/{orderId}/confirm`
- POST `/api/v1/orders/{orderId}/cancel`
- POST `/api/v1/orders/{orderId}/fulfill`

### Payment

- POST `/api/v1/payments/intents`
- POST `/api/v1/payments/{paymentId}/confirm`
- GET `/api/v1/payments/{paymentId}`
- POST `/api/v1/payments/{paymentId}/refunds`

### Inventory

- GET `/api/v1/inventory/stock`
- POST `/api/v1/inventory/reservations`
- POST `/api/v1/inventory/reservations/{id}/release`
- POST `/api/v1/inventory/adjustments`
- POST `/api/v1/inventory/transfers`

### Accounting

- POST `/api/v1/accounting/journals`
- POST `/api/v1/accounting/journals/{id}/post`
- GET `/api/v1/accounting/trial-balance`
- GET `/api/v1/accounting/profit-loss`
- GET `/api/v1/accounting/balance-sheet`
- POST `/api/v1/accounting/reconciliations`

### HR

- POST `/api/v1/employees`
- GET `/api/v1/employees`
- POST `/api/v1/attendance/records`
- POST `/api/v1/leave/requests`
- POST `/api/v1/payroll/runs`
- POST `/api/v1/payroll/runs/{id}/approve`

Every endpoint must enforce tenant, branch, role and application entitlements as applicable.

---

# 8. EVENT-DRIVEN ARCHITECTURE

## 8.1 Event envelope

All business events should follow a common envelope:

- event_id
- event_type
- event_version
- occurred_at
- tenant_id
- legal_entity_id, where relevant
- aggregate_id
- aggregate_version
- correlation_id
- causation_id
- producer
- payload

Do not include secrets, payment credentials, passwords or unnecessary personal data.

## 8.2 Main domain events

### Identity and platform

- UserRegistered
- TenantCreated
- TenantMembershipGranted
- ApplicationEntitlementChanged
- SubscriptionActivated
- SubscriptionSuspended

### Commerce

- ProductCreated
- ProductPublished
- PriceChanged
- CartCheckedOut
- OrderPlaced
- OrderConfirmed
- OrderCancelled
- PaymentInitiated
- PaymentSucceeded
- PaymentFailed
- RefundRequested
- RefundSucceeded

### Inventory

- StockReserved
- StockReservationFailed
- StockReleased
- StockAdjusted
- StockTransferred
- GoodsReceived

### Delivery

- ShipmentCreated
- DeliveryAssigned
- DeliveryDispatched
- DeliveryCompleted
- DeliveryFailed
- ReturnPickupCreated

### Accounting

- JournalPosted
- CustomerInvoiceIssued
- SupplierInvoiceRecorded
- CustomerPaymentReceived
- SupplierPaymentMade
- AccountingPeriodClosed

### HR

- EmployeeCreated
- AttendanceRecorded
- LeaveApproved
- PayrollCalculated
- PayrollApproved
- PayrollPaid

### CRM and support

- CustomerCreated
- CustomerSegmentChanged
- CampaignExecuted
- SupportTicketCreated
- SupportTicketResolved

## 8.3 Event reliability

Implement:

- Transactional Outbox Pattern.
- Idempotent consumers.
- Retry with backoff.
- Dead-letter topics.
- Event schema versioning.
- Event correlation.
- Consumer monitoring.
- Replay controls.
- Duplicate event handling.

Events are not proof of financial settlement unless they originate from the authoritative payment or accounting process.

## 8.4 Example commerce event flow

1. Customer checks out.
2. Order Service validates the cart and obtains authoritative prices.
3. Order Service creates a pending order.
4. Inventory Service reserves available stock.
5. Payment Service initiates payment.
6. Payment provider confirms through a verified callback.
7. Payment Service records the result idempotently.
8. Order Service confirms the order.
9. Inventory Service commits stock deduction at the appropriate fulfillment stage.
10. Delivery Service receives a fulfillment request.
11. Accounting Service records the appropriate financial entries.
12. Notification Service informs the customer.

Failure handling must support reservation expiry, payment failure, cancellation and compensation.

---

# 9. ACCOUNTING AND FINANCIAL INTEGRATION

Accounting must be an authoritative financial ledger, not a reporting copy of operational data.

## 9.1 Example financial posting

For a confirmed sale, the accounting rules must define:

- Revenue recognition.
- Tax liability.
- Receivable or payment clearing.
- Discounts.
- Shipping revenue or charges.
- Cost of goods sold.
- Inventory asset reduction.
- Refund treatment.

The exact journal depends on payment settlement, tax rules, delivery and recognition policies.

## 9.2 Integration rules

- Payment Service owns payment transactions.
- Order Service owns order state.
- Accounting Service owns journals and balances.
- Inventory Service owns stock quantities and movements.
- Accounting consumes confirmed events and applies configured posting rules.
- Refund accounting occurs only after the relevant refund status is confirmed.
- Reconciliation must identify missing, duplicated or inconsistent postings.

## 9.3 Accounting controls

- Balanced journals.
- Posting approval.
- Reversal entries.
- Period locks.
- Source transaction references.
- Audit trail.
- Legal entity segregation.
- Currency precision.
- Exchange-rate history.
- Tax calculation versioning.

---

# 10. SECURITY AND PERMISSIONS

## 10.1 Authorization model

Use a combination of:

- Platform roles.
- Tenant memberships.
- Application entitlements.
- User application grants.
- Tenant roles.
- Branch scopes.
- Resource-level permissions.

Identity alone does not grant application access.

## 10.2 Example roles

### Platform roles

- PLATFORM_OWNER
- PLATFORM_ADMIN
- PLATFORM_SUPPORT
- PLATFORM_FINANCE
- PLATFORM_AUDITOR

### Tenant roles

- TENANT_OWNER
- TENANT_ADMIN
- BUSINESS_MANAGER
- BRANCH_MANAGER
- DEPARTMENT_MANAGER
- EMPLOYEE
- AUDITOR

### Functional roles

- SALES_MANAGER
- SALES_EXECUTIVE
- INVENTORY_MANAGER
- WAREHOUSE_OPERATOR
- ACCOUNTANT
- FINANCE_MANAGER
- HR_MANAGER
- PAYROLL_ADMIN
- PROCUREMENT_MANAGER
- SUPPLIER_USER
- DELIVERY_MANAGER
- DRIVER
- RESTAURANT_MANAGER
- CHEF
- CASHIER
- CRM_MANAGER
- SUPPORT_AGENT
- SUPPORT_MANAGER
- QA_ENGINEER

Roles are templates. Actual authorization must be based on explicit permissions and scopes.

## 10.3 Permission examples

- `tenant.manage`
- `subscription.view`
- `subscription.manage`
- `product.create`
- `product.publish`
- `order.view`
- `order.cancel`
- `payment.view`
- `payment.refund`
- `inventory.adjust`
- `inventory.transfer`
- `accounting.journal.create`
- `accounting.journal.post`
- `accounting.report.view`
- `payroll.view`
- `payroll.approve`
- `employee.manage`
- `support.ticket.assign`
- `audit.view`

## 10.4 Security requirements

- Secure password storage.
- Token expiration.
- Refresh-token rotation and revocation.
- Issuer and audience validation.
- Gateway header sanitization.
- Downstream authorization.
- Service-to-service authentication.
- Tenant context validation.
- Rate limiting.
- Input validation.
- CSRF protection where applicable.
- Secure secrets management.
- Audit logging.
- Encryption in transit.
- Sensitive data protection.
- Access review.
- Backup protection.

Never trust X-User-Role or X-Tenant-ID headers from public callers.

---

# 11. AI CAPABILITIES

AI is a shared platform capability and must be introduced with security and cost controls.

## 11.1 AI Business Assistant

Provide a conversational assistant for authorized business operations.

Examples:

- What were yesterday's sales?
- Which products are running low?
- Which branch has the highest sales?
- Show overdue customer invoices.
- Summarize this month's expenses.
- Which orders are delayed?
- Summarize open support tickets.

The assistant must use permission-aware tools and tenant-scoped queries.

## 11.2 AI-assisted commerce

- Product description generation.
- Product categorization suggestions.
- Image tagging.
- SEO suggestions.
- Search relevance improvements.
- Product recommendations.
- Customer segmentation suggestions.
- Campaign content generation.

## 11.3 AI-assisted operations

- Demand forecasting.
- Inventory replenishment suggestions.
- Sales forecasting.
- Delivery demand estimation.
- Customer churn indicators.
- Anomaly detection.
- Expense classification suggestions.
- Support response drafts.

## 11.4 AI governance

- AI feature entitlements.
- Usage quotas.
- Cost tracking.
- Model configuration.
- Prompt versioning.
- Output audit metadata.
- Sensitive data filtering.
- Human approval for high-impact actions.
- Explainable recommendations where practical.
- Data retention controls.

AI must not independently post financial journals, approve payroll, execute refunds, change permissions or transfer funds.

---

# 12. INTEGRATIONS AND EXTENSIBILITY

## 12.1 Integration framework

Provide a central integration capability for:

- Payment gateways.
- Shipping providers.
- Email providers.
- SMS providers.
- WhatsApp providers.
- Tax systems.
- Accounting exports.
- E-commerce marketplaces.
- POS hardware.
- Barcode scanners.
- Banking integrations.
- Identity providers.

## 12.2 Webhooks

Support:

- Webhook registration.
- Event selection.
- Signing secrets.
- Signature verification.
- Retry.
- Delivery history.
- Failure monitoring.
- Manual replay.

## 12.3 Plugin architecture

Allow optional modules to be activated per tenant.

Each plugin must declare:

- Plugin ID.
- Version.
- Dependencies.
- Required permissions.
- API contracts.
- Entitlements.
- Configuration schema.
- Event subscriptions.

Plugins must execute within controlled boundaries. Do not permit arbitrary code or SQL from tenant configuration.

---

# 13. REPORTING AND BUSINESS INTELLIGENCE

Create a shared analytics capability.

Dashboards:

- Executive Overview.
- Sales Dashboard.
- Financial Dashboard.
- Inventory Dashboard.
- Procurement Dashboard.
- Restaurant Dashboard.
- Delivery Dashboard.
- CRM Dashboard.
- HR Dashboard.
- Support Dashboard.

Reporting requirements:

- Tenant isolation.
- Branch filtering.
- Legal entity filtering.
- Date ranges.
- Currency awareness.
- Export controls.
- Scheduled reports.
- Saved report definitions.
- Role-based access.

Prefer read replicas, event-driven projections or analytical storage for expensive reports instead of running every report against transactional databases.

---

# 14. NON-FUNCTIONAL REQUIREMENTS

## Security
- Tenant isolation.
- Least privilege.
- Auditable actions.
- Secure secrets.
- Encryption.
- Vulnerability management.

## Performance
Initial design targets, to be validated by load testing:

- Common read APIs: p95 below 500 ms.
- Common write APIs: p95 below 800 ms.
- Dashboard loading: below 3 seconds for normal workloads.
- Background jobs must not block request threads.

These are targets, not guarantees.

## Reliability
- Health checks.
- Graceful degradation.
- Retry policies.
- Dead-letter handling.
- Backup and restore.
- Disaster recovery planning.

## Scalability
- Stateless application services where practical.
- Horizontal scaling.
- Tenant-aware indexing.
- Kafka partition strategy.
- Redis usage limits.
- Object storage for documents.

## Accessibility and usability
- Responsive layouts.
- Keyboard navigation.
- Accessible labels.
- Consistent design system.
- Localization.
- Time-zone support.
- Currency and number formatting.

---

# 15. IMPLEMENTATION ROADMAP

The project must be delivered incrementally.

## PHASE 0 – Repository Stabilization and Baseline

Priority: P0

Tasks:
- Audit the current repository.
- Establish actual implementation status.
- Resolve build failures.
- Verify all ten applications.
- Identify duplicate services.
- Review existing database migrations.
- Document API contracts.
- Identify security issues.
- Establish automated CI checks.

Deliverables:
- Current architecture report.
- Feature gap report.
- Technical debt report.
- Baseline test report.
- Updated progress tracker.

Acceptance criteria:
- Repository inventory is complete.
- Existing tests and builds have actual recorded results.
- No feature is declared complete without evidence.
- Critical defects have owners and priorities.

## PHASE 1 – SaaS Foundation and Security

Priority: P0

Tasks:
- Tenant lifecycle.
- Legal entities and branches.
- User memberships.
- Application registry.
- Subscription plans.
- Application entitlements.
- User application grants.
- Tenant-aware roles.
- Secure JWT validation.
- Gateway and downstream authorization.
- Tenant-aware migrations.
- Audit events.

Acceptance criteria:
- Tenant A cannot access Tenant B data.
- Direct service requests cannot bypass authorization.
- Tenant app access requires a valid entitlement and user grant.
- Tenant context cannot be spoofed.
- Existing data is migrated safely.
- Security regression tests pass.

## PHASE 2 – Core Commerce

Priority: P0

Tasks:
- Catalog.
- Pricing.
- Promotions.
- Cart.
- Order.
- Payment.
- Inventory reservations.
- Returns and refunds.
- Storefront integration.
- Customer notifications.

Acceptance criteria:
- Customer can complete an end-to-end purchase.
- Server calculates authoritative prices.
- Duplicate checkout requests do not create duplicate orders.
- Payment callbacks are verified and idempotent.
- Inventory reservations are concurrency-safe.
- Failed payments do not create falsely confirmed orders.
- Refund status matches provider confirmation.
- Core workflows have automated integration tests.

## PHASE 3 – Inventory and Procurement

Priority: P1

Tasks:
- Multi-warehouse.
- Stock transfers.
- Stock counts.
- Barcode support.
- Supplier management.
- Purchase requisitions.
- Purchase orders.
- Goods receipt.
- Supplier returns.
- Replenishment.

Acceptance criteria:
- Stock movements are auditable.
- Concurrent reservations do not oversell.
- Purchase receipts update inventory correctly.
- Supplier documents reconcile with received quantities.
- Tenant and branch isolation tests pass.

## PHASE 4 – Full Accounting and Finance

Priority: P1

Tasks:
- Chart of accounts.
- Double-entry ledger.
- General ledger.
- Receivables.
- Payables.
- Bank reconciliation.
- GST and configurable taxes.
- Fixed assets.
- Budgets.
- Financial statements.
- Posting rules for commerce events.

Acceptance criteria:
- Every posted journal balances.
- Posted entries cannot be silently edited or deleted.
- Source transactions can be traced to ledger entries.
- Sales, payments, refunds and inventory accounting reconcile.
- Financial reports agree with ledger balances.
- Legal entity and period restrictions work.

## PHASE 5 – Retail, Restaurant and Delivery

Priority: P1

Tasks:
- POS.
- Cashier shifts.
- Branch operations.
- Restaurant menus.
- Modifiers.
- KDS.
- Tables and reservations.
- Recipes and ingredient costing.
- Driver management.
- Dispatch.
- Route integration.
- Proof of delivery.

Acceptance criteria:
- Retail POS sales update orders, payments and inventory correctly.
- Restaurant orders reach the correct kitchen station.
- Ingredient consumption follows configured recipes.
- Delivery status transitions are validated.
- Failed delivery and return pickup workflows work.

## PHASE 6 – CRM and Customer Support

Priority: P1

Tasks:
- Customer profiles.
- Leads and opportunities.
- Customer segmentation.
- Campaigns.
- Loyalty.
- Referral.
- Support tickets.
- SLA.
- Knowledge base.
- Omnichannel integration.

Acceptance criteria:
- Customer activity is consolidated without duplicating customer identity.
- Consent and communication preferences are enforced.
- Loyalty points are recorded and reconciled.
- Support agents see only authorized customer data.
- SLA escalation and assignment rules are tested.

## PHASE 7 – HR and Payroll

Priority: P2

Tasks:
- Employee records.
- Organization structure.
- Attendance.
- Shifts.
- Leave.
- Payroll.
- Salary components.
- Payslips.
- Reimbursements.
- HR reporting.

Acceptance criteria:
- Payroll runs are reproducible and versioned.
- Salary access is restricted.
- Payroll requires authorized approval.
- Attendance and leave affect payroll according to configured rules.
- Payroll output reconciles with payment and accounting records.
- Country-specific statutory rules are tested before enabling a jurisdiction.

## PHASE 8 – Assets and Projects

Priority: P2

Tasks:
- Asset registry.
- Depreciation.
- Asset maintenance.
- Project management.
- Tasks.
- Timesheets.
- Project budgets.

Acceptance criteria:
- Asset values reconcile with accounting.
- Depreciation schedules are reproducible.
- Project cost reports use approved timesheets and expenses.
- Tenant access controls apply throughout.

## PHASE 9 – Analytics and AI

Priority: P2

Tasks:
- Executive analytics.
- Financial analytics.
- Sales forecasting.
- Inventory forecasting.
- Customer intelligence.
- AI assistant.
- Product content assistance.
- Support response assistance.
- AI governance and quotas.

Acceptance criteria:
- AI tools enforce the caller's permissions.
- AI cannot access other tenants' information.
- AI-generated recommendations are distinguishable from verified facts.
- High-impact operations require human confirmation.
- AI usage is measured against tenant entitlements.
- Forecasts expose their data period and limitations.

## PHASE 10 – Integrations and Enterprise Readiness

Priority: P2

Tasks:
- Webhooks.
- Integration marketplace.
- Plugin architecture.
- Enterprise identity federation.
- Advanced audit.
- Advanced monitoring.
- Backup and disaster recovery.
- Load testing.
- Security testing.
- Operational runbooks.

Acceptance criteria:
- Webhooks are signed and retry safely.
- Plugin permissions are enforced.
- Restore exercises are successful.
- Monitoring detects service failures.
- Load tests meet agreed performance targets.
- Security review has no unresolved critical findings.

---

# 16. DEPENDENCY ORDER

The following dependencies must be respected:

1. Identity and tenant management before tenant-specific business modules.
2. Application registry and entitlements before subscription-controlled access.
3. Catalog and pricing before cart and checkout.
4. Order and payment contracts before full fulfillment integration.
5. Inventory reservation before reliable order confirmation.
6. Procurement and suppliers before advanced replenishment.
7. Accounting foundation before full financial reporting.
8. POS and restaurant menu management before kitchen workflows.
9. Customer identity before CRM consolidation and loyalty.
10. HR foundation before payroll.
11. Financial ledger before asset depreciation integration.
12. Stable domain events before analytics and AI.
13. Secure integrations before marketplace and plugins.

---

# 17. GLOBAL DEFINITION OF DONE

A feature is complete only when all applicable conditions are satisfied:

- Business requirements are documented.
- UI is implemented and usable.
- API is implemented and documented.
- Database changes are versioned.
- Tenant isolation is verified.
- Permissions are enforced.
- Validation is implemented.
- Error handling is implemented.
- Audit requirements are satisfied.
- Events and integrations work.
- Unit tests pass.
- Integration tests pass.
- Relevant end-to-end tests pass.
- Logs and metrics exist.
- Documentation is updated.
- No unresolved critical defect remains.

Use these status labels:

- COMPLETED
- PARTIALLY COMPLETED
- NOT IMPLEMENTED
- IMPLEMENTED BUT BROKEN
- NOT VERIFIED
- BLOCKED

Do not infer completion from a folder, menu, entity or placeholder screen.

---

# 18. PRODUCT RELEASE STRATEGY

## MVP release

The first usable commercial release should include:

- Secure tenant onboarding.
- User membership and application grants.
- Subscription and entitlement foundation.
- Product catalog.
- Pricing.
- Storefront.
- Cart and checkout.
- Payment integration.
- Orders.
- Inventory reservations.
- Basic delivery.
- Basic accounting integration.
- Customer management.
- Notifications.
- Essential operational dashboards.

## Business release

Add:

- Procurement.
- Full accounting.
- Retail POS.
- Restaurant operations.
- Advanced delivery.
- CRM.
- Support.
- Multi-branch operations.

## Enterprise release

Add:

- Full HR and payroll.
- Asset management.
- Project management.
- Advanced analytics.
- AI assistant.
- Workflow automation.
- Integration marketplace.
- Advanced compliance and governance.
- Enterprise deployment and disaster recovery.

A feature being planned for a release does not mean it is implemented.

---

# 19. PRODUCT SUCCESS METRICS

Track:

- Tenant activation rate.
- Trial-to-paid conversion.
- Monthly recurring revenue.
- Subscription retention.
- Active users per tenant.
- Application adoption.
- Orders processed.
- Payment success rate.
- Inventory accuracy.
- Fulfillment time.
- Refund rate.
- Customer repeat purchase rate.
- Support resolution time.
- Accounting reconciliation exceptions.
- Payroll processing accuracy.
- Platform availability.
- API latency.
- AI usage and cost per tenant.

---

# 20. PROJECT DOCUMENTATION STRUCTURE

Maintain the following documentation:

docs/
- product/
  - PRODUCT_VISION.md
  - PRODUCT_REQUIREMENTS.md
  - APPLICATION_CATALOG.md
  - ADVANCED_FEATURE_CATALOG.md
  - RELEASE_STRATEGY.md
- architecture/
  - SYSTEM_ARCHITECTURE.md
  - SERVICE_BOUNDARIES.md
  - DATABASE_OWNERSHIP.md
  - API_STANDARDS.md
  - EVENT_CATALOG.md
  - SECURITY_ARCHITECTURE.md
  - MULTI_TENANCY.md
- modules/
  - commerce/
  - inventory/
  - accounting/
  - procurement/
  - delivery/
  - restaurant/
  - crm/
  - hr/
  - payroll/
  - assets/
  - projects/
  - support/
  - analytics/
  - ai/
- implementation/
  - IMPLEMENTATION_ROADMAP.md
  - ACCEPTANCE_CRITERIA.md
  - IMPLEMENTATION_PROGRESS.md
- status/
  - PROJECT_CURRENT_STATUS.md
  - CHANGELOG_CURRENT.md
  - INCOMPLETE_FEATURES.md
  - BUGS_AND_TECHNICAL_DEBT.md
  - TEST_AND_VERIFICATION_REPORT.md

Update existing documentation rather than creating duplicates where equivalent documents already exist.

---

# FINAL PRODUCT OBJECTIVE

UCP must become a modular, secure and extensible business operating platform.

A customer should be able to:

1. Register a business.
2. Create legal entities and branches.
3. Subscribe to required modules.
4. Invite employees and assign permissions.
5. Manage products, suppliers and inventory.
6. Sell online or through physical POS.
7. Operate restaurants and manage delivery.
8. Process payments and refunds.
9. Manage accounting and financial reports.
10. Manage employees and payroll.
11. Track customers, sales and support.
12. Monitor the business through analytics.
13. Automate routine work.
14. Use AI to make better operational decisions.
15. Expand to additional modules without migrating to another platform.

**Final design principle:** One UCP platform, one secure identity foundation, modular applications, independently owned business domains, and a consistent tenant-aware experience.
