# Known Business Limitations

**Universal Commerce Platform**  
*Document Version:* 2.0.0  
*Release Tier:* Production 2.0.0-enterprise

---

## 1. Inventory & Fulfillment Boundaries

1. **Single Currency Model**: All transactions, prices, and totals are computed strictly in US Dollars (USD). Multi-currency real-time exchange conversion is planned for v2.2.
2. **Single Warehouse Topology**: Inventory stock counts represent a unified aggregate warehouse. Geographically distributed multi-facility fulfillment routing is scheduled for v2.2.

---

## 2. Payment & Third-Party Integrations

1. **Simulated Payment Settlement**: In the current v2.0.0 release, payment authorization is simulated synchronously during checkout submission (`status: PLACED`). Webhook integrations for external payment gateways (Stripe, Adyen, PayPal) are scheduled for v2.1.
2. **Transactional Email Dispatch**: Email notifications are simulated via Kafka events and logged in `notification_db`. Direct live SMTP relay (AWS SES / SendGrid) driver integration is scheduled for v2.1.

---

## 3. Merchandising & Promotions

1. **Direct Pricing**: Products support fixed unit pricing with stock indicators. Dynamic coupon code validation, percentage discounts, and tiered promotional bundling are queued in the product backlog for v2.1.

---

## 4. Accounting Subsystem Boundaries

1. **Customer Subledger vs Full Double-Entry General Ledger**: The accounting module implements an immutable transaction subledger and net balance calculation engine (`Total Credits - Total Debits`) with cryptographic idempotency, reconciliation tracking, and immutable audit logs. It is explicitly an account ledger / customer balance tracking system and does not claim to be a full enterprise double-entry general ledger with multi-account chart of accounts, debit/credit account balancing (e.g. Accounts Receivable vs Revenue Accounts), or ERP GL sync (SAP/NetSuite).
2. **Platform Subledger Totals vs Audited Financial Statements**: Dashboard and ledger KPI metrics reflect internally recorded transactions in `order_db` and are provided for operational oversight and platform customer accounting. They do not constitute audited financial statements or GAAP/IFRS statutory balance sheets.
3. **Automated Tax Jurisdictions**: Sales tax calculation uses local standard checkout calculations and is not connected to multi-jurisdiction external tax calculation engines (e.g., Avalara AvaTax, Vertex).
4. **External Acquiring Bank Settlement**: Order payments and refunds reflect internal platform ledger postings and customer balance adjustments; direct merchant acquiring card capture or bank ACH pull requires external payment service provider (PSP) webhooks.
5. **Tokenized Card Metadata & PCI-DSS Scope**: Full primary account numbers (PANs) and security CVVs are strictly prohibited from touching platform servers or databases. The platform displays only tokenized metadata (brand, expiration, masked last 4 digits) provided by tokenized payment gateways.
