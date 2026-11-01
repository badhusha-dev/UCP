# Product Roadmap & Release Horizons

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Release Milestones & Horizons

```
[ v2.0.0 (Current) ] ──> Enterprise Core, MFEs, Kafka DLQ, Observability, Server Installer
         │
         ▼
[ v2.1.0 (Q1) ]     ──> Stripe / PayPal Webhooks, SendGrid Email Dispatch, Redis Server Cart
         │
         ▼
[ v2.2.0 (Q2) ]     ──> Elasticsearch CDC Catalog, Multi-Currency (EUR, GBP), Dynamic Promotions
         │
         ▼
[ v2.3.0 (Q3) ]     ──> Multi-Warehouse Fulfillment, Carrier Webhooks, Customer Reviews & Ratings
         │
         ▼
[ v3.0.0 (Q4) ]     ──> AI Vector Search, GraphQL BFF, React Native Mobile Storefront
```

---

## 2. Release Objectives & Deliverables

### Release v2.1.0 — Commercial Integrations & Communications
- **Stripe & PayPal Payment Intents**: Replaces synchronous simulation with asynchronous payment capture, customer card tokenization, and 3D-Secure authentication.
- **Transactional Mail Relays**: Outbound email notification driver for SendGrid and AWS SES with dynamic HTML templating.
- **Server-Side Redis Cart Sync**: Guest-to-user shopping cart persistence and synchronization across multiple devices.

### Release v2.2.0 — Search & Global Expansion
- **Elasticsearch Catalog Sync**: Real-time Kafka-Connect / Debezium CDC pipeline replicating PostgreSQL catalog changes into Elasticsearch indices.
- **Multi-Currency & VAT**: Automated exchange rate fetching from OpenExchangeRates; localized VAT/sales tax calculation.
- **Promotions Engine**: Coupon code redemption, buy-one-get-one (BOGO) logic, and percentage-based discounts.
