# Implemented Features Overview

**Universal Commerce Platform**  
*Document Version:* 2.0.0  
*Status:* PRODUCTION VERIFIED

---

## 1. Verified Feature Baseline

This documentation suite details exclusively the **verified, operational capabilities** active in the Universal Commerce Platform v2.0.0 release. Every feature listed in this section has been tested and confirmed functional through automated unit tests, integration tests, or live cluster smoke tests.

---

## 2. Core Operational Capabilities

1. **Decoupled Storefront Shell & Microfrontends**: Dynamic loading of remote modules (`mfe-product`, `mfe-order`, `mfe-user`) hosted in NGINX containers using Vite Module Federation.
2. **Product Catalog & Redis L2 Cache**: Category browsing, full-text fuzzy search, stock level indicators, and sub-millisecond in-memory cache reads.
3. **Cart & Atomic Checkout**: Cross-MFE persistent cart state, anti-tampering server-side price calculation, and atomic check-and-decrement stock reservation.
4. **Order State Machine & Asynchronous Events**: Lifecycle states (`PLACED`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`) with Kafka event dispatch.
5. **Consumer Idempotency & DLQ Replay**: Notification consumer with duplicate message prevention, poison-pill dead letter isolation, and RESTful replay endpoints.
6. **Unified Observability Fabric**: Distributed tracing via Zipkin, metrics via Prometheus/Grafana, and centralized log aggregation via Elasticsearch/Logstash/Kibana.
7. **Expanded Accounting & Financial Subledger Subsystem**:
   - **Customer Self-Service Ledger & Overview**:
     - Real-time Account Overview displaying **Available Store Credit**, **Lifetime Spend**, **Lifetime Refunds**, **Total Credits**, **Total Debits**, **Net Activity**, and **Pending Transactions**, with explicit explanatory notes on calculation formulas.
     - Paginated transaction history with multi-criteria filtering (transaction type, status, order reference, date range).
     - **Itemized Order Invoices & Receipts**: Printable receipts with line items, SKU, unit prices, subtotal, shipping, calculated taxes, total paid, and tokenized payment gateway reference.
     - **Customer-Initiated Refund Workflow**: Customers can submit structured refund requests for their own orders with mandatory reasons, and track review status (`REQUESTED`, `APPROVED`, `REJECTED`, `PROCESSED`).
     - **Account Statement CSV Export**: Date-range customer statement downloads with opening/closing totals and verified ledger entries.
     - **Tokenized Payment Methods**: PCI DSS compliant display of securely tokenized payment cards (brand, last 4 digits only; zero card numbers or security CVVs stored on platform servers).
     - **Accounting Preferences**: Customer opt-in/opt-out toggles for email receipts and monthly ledger statement alerts.
   - **Financial Safety & Immutability Invariants**:
     - Fixed-precision decimal values (`BigDecimal` in Java, `NUMERIC(19, 4)` in PostgreSQL). Floating-point arithmetic strictly forbidden.
     - Explicit currency stamping (`USD`) on every transaction.
     - **Immutable Posted Ledger**: Historical posted transactions are permanently immutable and cannot be silently edited or deleted.
     - **Compensating Reversals**: Corrections and adjustments post new linked compensating entries with opposite nature (`CREDIT` <-> `DEBIT`), canceling out balances mathematically to zero.
     - **Idempotent Settlement**: Automatic payment debit on order placement and refund credit on order cancellation using unique idempotency keys.
   - **Administrator Accounting & Financial Governance**:
     - **Executive Accounting Dashboard**: Platform gross sales, total refunds, net recorded activity, store credits issued, pending settlements, unreconciled counts, and discrepancy flags, accompanied by platform subledger disclaimer notice.
     - **Cross-Customer Master Ledger**: Multi-filter administrative ledger lookup across all customer accounts.
     - **Admin Master Ledger CSV Export**: Filtered export with mandatory immutable audit logging (`ADMIN_LEDGER_EXPORT_CSV`).
     - **Refund Governance**: Staff review of customer return claims, with approval triggering automatic linked subledger refund credit and order state transition to `REFUNDED`.
     - **Reconciliation & Discrepancy Flagging**: Records can be flagged as `RECONCILED`, `PENDING_REVIEW`, or `FLAGGED_DISCREPANCY` with auditor notes.
     - **Manual Balance Adjustments**: Auditable manual credits, debits, or corrections requiring mandatory business justification and ticket references.
     - **Role Separation**: `ROLE_MANAGER` is granted read-only oversight to executive dashboard and customer ledgers, while mutating financial actions (adjustments, reversals, refund approvals) are strictly restricted to `ROLE_ADMIN` (403 Forbidden enforcement).
     - **Immutable Audit Trail**: Dedicated audit table tracking administrator ID, email, action, timestamp, business reason, and before/after JSON state snapshots.
     - **Zero-Trust Access Control & Tenant Isolation**: Gateway-level claim extraction, RBAC protection on admin endpoints, and strict cross-tenant verification preventing unauthorized data access.
