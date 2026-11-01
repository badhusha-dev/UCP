# Application Catalog: specification coverage

Measures the repository against [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md) (specification v1.0, sections 2, 3, 4, 11 and 12).
Date 2026-11-01, at commit `bb1b7b0e`. Status labels are the specification's own (section 17).

**How this was measured.** Evidence is what exists in source: Flyway `CREATE TABLE` statements, `@Entity` classes, controller base paths and frontend workspaces, found by search. A table or endpoint existing is not proof that the feature works; where no test or run covers it, the row says so. "No table found" means a search of migrations and entities found nothing, not that the feature was ruled out by reading every file. Nothing here was exercised in a browser or against a live stack (no Docker daemon).

Frontend workspaces: `shell-app`, `shared-library`, `mfe-accounting`, `mfe-analytics`, `mfe-cart`, `mfe-crm`, `mfe-delivery`, `mfe-error-stack`, `mfe-inventory`, `mfe-loyalty`, `mfe-order`, `mfe-procurement`, `mfe-product`, `mfe-stock`, `mfe-super-admin`, `mfe-user`.

## Summary

| # | Application | Status against the specification | Largest gaps |
|---|---|---|---|
| 1 | Super Admin | PARTIALLY COMPLETED | Subscriptions, plans and billing; tenant lifecycle; legal entities and branches; SaaS operations |
| 2 | E-commerce Admin | PARTIALLY COMPLETED | Brands, attributes and bundles; branch and customer-group pricing; tax and currency configuration; forecasts |
| 3 | Storefront | PARTIALLY COMPLETED | Payment; storefront tenant resolution; B2B; marketplace; referrals and gift cards |
| 4 | Inventory Management | PARTIALLY COMPLETED | Atomic reservations; stock counts; serial numbers; pick, pack and dispatch tasks; two services own stock |
| 5 | Accounting and Finance | PARTIALLY COMPLETED | Trial balance; receivable and payable aging; bank accounts; tax codes and GST; fixed assets; budgets; DB-level immutability |
| 6 | Delivery and Logistics | PARTIALLY COMPLETED | Fleet and vehicles; delivery not created on fulfilment; migrations never applied to Postgres |
| 7 | Vendor / Restaurant | NOT IMPLEMENTED | Menus, POS, kitchen display, tables, recipes, marketplace vendors |
| 8 | CRM | PARTIALLY COMPLETED | Marketing automation and consent; customer intelligence; tenant scope (BLOCKED) |
| 9 | Tester / Quality Center | PARTIALLY COMPLETED | Defect lifecycle; release gates; tenant-or-platform decision (BLOCKED) |
| 10 | Customer Support | NOT IMPLEMENTED | Everything |

0 of 10 applications are COMPLETED. App 7 was rated PARTIALLY COMPLETED in earlier status reports, against an older scope that counted procurement supplier flows; against this specification's restaurant and marketplace scope nothing exists, so it is NOT IMPLEMENTED here.

## App 1: Super Admin

Frontend: `mfe-super-admin` (no tests). Backend: user-service.

| Feature group | Status | Evidence | Gap |
|---|---|---|---|
| A. Platform dashboard | NOT VERIFIED | `mfe-super-admin` screen exists; not run | Subscription revenue, API and storage usage: no data source exists |
| B. Tenant management | PARTIALLY COMPLETED | `tenants`, `tenant_memberships`, `/api/platform/tenants` | Suspend, reactivate and offboarding states (A-01); no legal entity, branch or department tables |
| C. Application registry | PARTIALLY COMPLETED | `application_registry`, `tenant_application_entitlements` | Versions, dependencies and per-feature entitlements not modelled |
| D. Subscription management | NOT IMPLEMENTED | No plan, subscription, invoice or usage table | Whole group |
| E. Security and governance | PARTIALLY COMPLETED | `access_roles`, `access_permissions`, `access_audit_log`, `tenant_access_audit_log`; account lockout (user-service V16) | Session management and revocation (B-21); login history and suspicious-activity views |
| F. SaaS operations | NOT IMPLEMENTED | No feature-flag, maintenance-mode or announcement code | Whole group |

## App 2: E-commerce Admin

Frontend: `mfe-product`, `mfe-order`, `mfe-user`. Backend: product-service, order-service, analytics-service.

| Feature group | Status | Evidence | Gap |
|---|---|---|---|
| A. Product management | PARTIALLY COMPLETED | `product`, `product_category`, `product_variant`, `product_audit_log` | No brand, attribute or bundle tables; import/export and approval workflow not found |
| B. Pricing and promotions | PARTIALLY COMPLETED | `product_prices`, `scheduled_prices`, `price_history`, `promotion_campaigns`, `promotion_rules`, `promotion_conditions`, `promotion_rewards`, `promotion_approvals`, `coupon_codes`, `coupon_redemptions` | Branch and customer-group price lists; storefront promo codes not validated server-side (B-14) |
| C. Order management | PARTIALLY COMPLETED | `order_status_history`, `order_fulfillments`, `order_exceptions`, `order_bulk_jobs`, `order_notes`, RMA under `/api/orders/admin/rma`; order-service 101 tests | Split orders and amendments not verified |
| D. Store management | PARTIALLY COMPLETED | `storefronts`, `storefront_domains`, `storefront_banners`, `storefront_theme_presets`, `storefront_versions`, `storefront_approval_history` | SEO and navigation not verified |
| E. Business configuration | PARTIALLY COMPLETED | `order_settings`, `pricing_settings`, `return_settings` | No tax, currency, business-hours or branch configuration |
| F. Sales intelligence | PARTIALLY COMPLETED | `/api/orders/admin/analytics/*` (overview, sales, products, customers, alerts); abandoned-cart reminders in cart-service; dashboard reports load failures (B-37) | Conversion analytics and forecasts |

## App 3: Storefront

Frontend: `shell-app`, `mfe-product`, `mfe-cart`, checkout in `mfe-order`. Backend: product-service, cart-service, order-service.

| Feature group | Status | Evidence | Gap |
|---|---|---|---|
| A. Storefront experience | PARTIALLY COMPLETED | `/api/products/public/storefront`, `product_review`, `customer_wishlist`, `recently_viewed` | Product comparison not found; search and filters not verified |
| B. Shopping | PARTIALLY COMPLETED | cart-service (guest carts by `X-Cart-Session`), order-service checkout | **No payment service** (B-01, PSP not chosen); storefront tenant resolution (B-25, K4) |
| C. Customer account | PARTIALLY COMPLETED | `users`, `customer_address`, `order_tracking_event`, returns, `refund_requests` | Password reset (F-06); what `customer_payment_methods` stores is not verified |
| D. Customer growth | PARTIALLY COMPLETED | loyalty-service accounts, tiers and transactions | Referrals, gift cards, store credit, memberships and product subscriptions not found |
| E. B2B commerce | NOT IMPLEMENTED | No business-account, customer price-list or quotation tables | Whole group |
| F. Marketplace | NOT IMPLEMENTED | No seller, vendor or commission tables | Whole group |

## App 4: Inventory Management

Frontend: `mfe-inventory`, `mfe-stock`. Backend: inventory-service, stock-service, procurement-service.

| Feature group | Status | Evidence | Gap |
|---|---|---|---|
| A. Inventory core | PARTIALLY COMPLETED | `inventory_item`, `stock_movement`, `stock_reservation`, `wms_adjustment`, `wms_transfer`; outbox for events | Reservation not atomic with order creation (A-04); no stock-count table; stock-service not tenant-scoped |
| B. Warehouse management | PARTIALLY COMPLETED | `wms_warehouse`, `wms_location`, `wms_stock` | Putaway, picking, packing and dispatch tasks not found; V4 never applied to Postgres (K11) |
| C. Product tracking | PARTIALLY COMPLETED | Batch and expiry columns, FIFO, FEFO and weighted-average valuation (`Valuation.java`, `wms_cost_layer`) | Serial numbers not found; barcode support not verified |
| D. Procurement integration | PARTIALLY COMPLETED | procurement-service purchase orders, goods receipts, supplier returns | Purchase requisitions; discrepancy handling not verified |
| E. Intelligence | PARTIALLY COMPLETED | `back_in_stock_alert`; inventory analytics (tenant-scoped since B-04) | Overstock, aging, dead stock, forecasting and replenishment |

Ownership problem: inventory-service and stock-service both own `stock_item`/`inventory_item`, `stock_movement`, `stock_reservation` and `back_in_stock_alert`; product-service also has `stock_reservation` and `back_in_stock_alert`. The specification gives stock to one Inventory service.

## App 5: Accounting and Finance

Frontend: `mfe-accounting` (no tenant login route, K17). Backend: accounting-service (`/api/accounting`, `/api/accounting/admin`, `/internal/accounting`; 27 tests, 5 Postgres-gated).

| Feature group | Status | Evidence | Gap |
|---|---|---|---|
| A. General ledger | PARTIALLY COMPLETED | `accounts`, `journal_entries`, `journal_lines`, `journal_drafts` (submit, approve, reject), journal reverse, `accounting_periods` (close, reopen) | No trial-balance endpoint; opening balances not verified |
| B. Accounts receivable | PARTIALLY COMPLETED | `invoices`, `invoice_line_items`, invoice status, customer statement export | Customer credit notes, payment allocation, aging |
| C. Accounts payable | PARTIALLY COMPLETED | `/internal/accounting/supplier-payables`, `supplier-credit-notes`; procurement `supplier_invoices` | Payment approvals, supplier statements, aging |
| D. Cash and banking | PARTIALLY COMPLETED | Per-transaction reconciliation, cash-flow report | No bank-account or bank-transaction tables; petty cash and vouchers |
| E. Tax management | PARTIALLY COMPLETED | Tax summary report | No tax codes, rates, groups or GST configuration |
| F. Fixed assets | NOT IMPLEMENTED | No asset tables | Whole group |
| G. Budgeting | NOT IMPLEMENTED | No budget tables | Whole group |
| H. Financial reporting | PARTIALLY COMPLETED | Profit and loss, balance sheet, cash flow, tax summary, trends, `financial_report_snapshots` | Trial balance, aging, branch profitability, consolidation |
| I. Financial controls | PARTIALLY COMPLETED | Draft approval, reversals, period close, `accounting_audit_log`, tenant scope (V19) | Database trigger against editing posted lines (B-29, needs Postgres); legal-entity segregation (no legal entities) |

Accounting holds its own copies of order tables (`orders`, `order_items`, `order_return`, `coupon`, `customer_cart`) in `accounting_db`. Whether they are event-fed projections was not re-verified in this pass.

## App 6: Delivery and Logistics

Frontend: `mfe-delivery` (single file, no tests). Backend: delivery-service (37 tests, own outbox).

| Feature group | Status | Evidence | Gap |
|---|---|---|---|
| Deliveries, dispatch, zones, routes | PARTIALLY COMPLETED | `delivery`, `delivery_assignment`, `delivery_trip`, `delivery_trip_stop`, `delivery_zone`, `delivery_status_history` | Delivery not created when an order is fulfilled (A-06) |
| Drivers and settlements | PARTIALLY COMPLETED | `delivery_partner_profile`, `delivery_partner_zone`, `delivery_partner_earning` | No fleet or vehicle tables |
| Proof, OTP, failures | PARTIALLY COMPLETED | `delivery_proof`, `delivery_otp`, `delivery_incident`, `delivery_location_update` | Return pickup lives in order-service (`reverse_pickup_requests`) |
| Partner integration, costs | PARTIALLY COMPLETED | `delivery_partner_capability`, `delivery_pricing_policy` | No external provider integration |
| Driver mobile and offline sync | NOT VERIFIED | `delivery_idempotency` supports safe retries | No mobile client in the repository |

Delivery V1 and V2 have never been applied to Postgres (K11).

## App 7: Vendor / Restaurant Management

| Feature group | Status | Evidence |
|---|---|---|
| A. Menu management | NOT IMPLEMENTED | No menu, modifier or availability tables |
| B. Restaurant POS | NOT IMPLEMENTED | No POS, split-bill or cashier-shift code |
| C. Kitchen management | NOT IMPLEMENTED | Order-service has restaurant-related order states only |
| D. Table management | NOT IMPLEMENTED | No table, floor-plan or reservation tables |
| E. Recipes and ingredients | NOT IMPLEMENTED | No recipe tables |
| F. Vendor management | NOT IMPLEMENTED | procurement-service suppliers are purchasing suppliers, not marketplace vendors |
| G. Branch operations | NOT IMPLEMENTED | No branch entity anywhere |

## App 8: CRM

Frontend: `mfe-crm`, `mfe-loyalty`. Backend: crm-service (2 tests), loyalty-service.

| Feature group | Status | Evidence | Gap |
|---|---|---|---|
| A. Customer management | PARTIALLY COMPLETED | `crm_contacts`, `crm_companies`, `crm_activities`, `crm_custom_fields`, `crm_segments` | Not tenant-scoped: the slice is BLOCKED on Postgres tests (F-02); `StaffAccessFilter` refuses SUPER_ADMIN (B-36) |
| B. Sales CRM | PARTIALLY COMPLETED | `crm_opportunities`, `crm_pipeline_stages`, `crm_tasks`, `crm_saved_views` | Targets and representatives |
| C. Marketing automation | NOT IMPLEMENTED | No campaign or consent tables in crm-service (product-service promotion campaigns are pricing) | Whole group |
| D. Loyalty | PARTIALLY COMPLETED | loyalty-service accounts, tier rules, transactions; outbox events | Referrals, gift vouchers; user-service also holds `customer_loyalty` and `loyalty_transaction` |
| E. Customer intelligence | NOT IMPLEMENTED | Customer analytics exist in analytics-service, not CRM | Lifetime value, churn, cohorts, affinity |

## App 9: Tester / Quality Center

Frontend: Quality Center in `shell-app`, `mfe-error-stack`. Backend: qa-service (16 tests), error-stack-service.

| Feature group | Status | Evidence | Gap |
|---|---|---|---|
| Test projects, suites, cases, runs | PARTIALLY COMPLETED | `qa_features`, `qa_feature_tests`, `qa_test_suites`, `qa_test_runs`, `qa_test_results`, `qa_active_run_lock` | Runner needs the repository mounted (A-09) |
| Bug management and defect lifecycle | NOT IMPLEMENTED | error-stack groups runtime errors (`ErrorGroup`, `ErrorRecord`); no defect workflow | Whole group |
| Automation, regression, performance, security tracking | NOT VERIFIED | Runner exists; suites not inspected | Performance and security tracking not found |
| Release gates, CI integration | NOT IMPLEMENTED | CI is GitHub Actions, not linked to qa-service | Whole group |
| Tenant or platform scope | BLOCKED | Product decision outstanding | — |

## App 10: Customer Support

NOT IMPLEMENTED. No module, table, endpoint or screen for tickets, SLA, knowledge base or chat. Scope decision outstanding (roadmap 2.1).

## Additional ERP modules (specification section 4)

| Module | Status | Evidence |
|---|---|---|
| 4.1 HR and payroll | NOT IMPLEMENTED | No employee, attendance, leave or payroll tables |
| 4.2 Procurement and suppliers | PARTIALLY COMPLETED | `proc_supplier`, `proc_supplier_item`, `proc_po`, `proc_receipt`, `proc_return`, `supplier_invoices`; a second, legacy schema (`suppliers`, `purchase_orders`, `goods_receipts`) in the same service. Requisitions, RFQs, quotations, contracts and ratings not found. Legacy API not tenant-scoped. |
| 4.3 Projects and tasks | NOT IMPLEMENTED | No project or timesheet tables |
| 4.4 Asset management | NOT IMPLEMENTED | No asset tables |
| 4.5 Document management | NOT IMPLEMENTED as a shared capability | Module-specific only: `storefront_documents`, `return_attachments` |
| 4.6 Workflow automation | NOT IMPLEMENTED as a shared capability | Module-specific approvals only: journal drafts, promotion approvals, storefront approvals |
| 4.7 Business intelligence | PARTIALLY COMPLETED | analytics-service (`analytics_db`), inventory analytics, accounting reports. No saved or scheduled reports. |

## Shared capabilities (specification sections 2.3, 11 and 12)

| Capability | Status | Evidence |
|---|---|---|
| Subscription plans, limits, billing (2.3) | NOT IMPLEMENTED | Entitlement and grant tables exist; no plans, limits, invoices or usage records |
| Backend entitlement checks (2.3) | PARTIALLY COMPLETED | Application tokens require an entitlement and a grant; per-plan limits do not exist |
| AI business assistant (11.1) | NOT IMPLEMENTED | product-service `chatWithAiAssistant` is keyword matching over `findAllByActiveTrue()` (every tenant's active products), with no model, quota or audit |
| AI commerce and operations (11.2, 11.3) | NOT IMPLEMENTED | No forecasting or generation code |
| AI governance (11.4) | NOT IMPLEMENTED | — |
| Integration framework (12.1) | NOT IMPLEMENTED | Delivery partner capabilities only |
| Webhooks (12.2) | NOT IMPLEMENTED | No webhook code |
| Plugins (12.3) | NOT IMPLEMENTED | — |

## Related documents

- Service and convention coverage (sections 5 to 10): [../architecture/SERVICE_BOUNDARIES.md](../architecture/SERVICE_BOUNDARIES.md)
- Release and phase coverage (sections 15, 16 and 18): [RELEASE_STRATEGY.md](RELEASE_STRATEGY.md)
- Defects and debt by ID: [../status/BUGS_AND_TECHNICAL_DEBT.md](../status/BUGS_AND_TECHNICAL_DEBT.md); features by ID: [../status/INCOMPLETE_FEATURES.md](../status/INCOMPLETE_FEATURES.md)
