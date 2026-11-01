# Implemented Feature Technical Boundaries & Constraints

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Catalog & Search Boundaries
- **Categories**: Fixed taxonomy of `Electronics`, `Fashion`, and `Home`. Dynamically added categories require backend schema updates in the current release.
- **Search Capabilities**: Powered by PostgreSQL indexing with substring and case-insensitive matching (`ILIKE`). Dedicated real-time Elasticsearch search integration is architected for v2.2.

---

## 2. Order & Payment Boundaries
- **Payment Processing**: The current release verifies order totals and marks orders as `PLACED` immediately without an external payment gateway redirect. Stripe and PayPal webhook integrations are scheduled for v2.1.
- **Currency**: Supports USD transactions exclusively.

---

## 3. Asynchronous Messaging Boundaries
- **Email Dispatch**: `notification-service` simulates external SMTP dispatches by logging records to `notification_db`. Third-party transactional email providers (SendGrid, AWS SES) are slated for v2.1.
- **Partitioning**: The `order-events` Kafka topic is provisioned with 4 partitions, providing linear scaling up to 4 concurrent consumer instances.
