# Enhancement Proposals (RFCs)

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## RFC-001: Stripe Payment Intents & Webhook Settlement Engine

- **Status**: PROPOSED (Target: v2.1.0)
- **Author**: Lead Architect & Backend Engineering Team
- **Proposal Summary**:
  - Replace current simulated checkout flow with Stripe Payment Intents API.
  - Client MFE requests a client secret from `POST /api/orders/payment-intent`.
  - Stripe Elements renders secure card input in `mfe-order`.
  - Upon customer authorization, Stripe dispatches a webhook `payment_intent.succeeded` to the API Gateway.
  - `order-service` verifies cryptographic webhook signature and transitions order from `PENDING_PAYMENT` to `CONFIRMED`.

---

## RFC-002: Dynamic Discount and Promotions Engine

- **Status**: PROPOSED (Target: v2.2.0)
- **Author**: Merchandising Product Team
- **Proposal Summary**:
  - Introduce new microservice: `promotion-service` (Port 8085).
  - Supports rule-based discounting: percentage discounts, fixed cart deductions, coupon code validation, and category exclusions.
  - Exposes validation endpoint: `POST /api/promotions/apply`.
  - Emits telemetry on coupon redemption rates to Prometheus.
