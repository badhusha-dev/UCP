# Stakeholders & User Roles Specification

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Key Business Stakeholders

- **VP of Digital Commerce**: Accountable for top-line gross merchandise value (GMV), customer acquisition, and platform availability.
- **Product Managers**: Responsible for catalog feature discovery, cart usability, and checkout funnel optimization.
- **Fulfillment & Warehouse Managers**: Responsible for dispatching orders, maintaining accurate stock counts, and handling returns.
- **Customer Support Leads**: Responsible for order dispute resolution, refund processing, and tracking shipment inquiries.
- **Lead SRE & Security Officers**: Accountable for system uptime, infrastructure cost efficiency, data security, and regulatory compliance.

---

## 2. Platform User Roles & Responsibilities

| Role | Classification | Core Responsibilities & System Capabilities |
| :--- | :--- | :--- |
| **Guest Shopper** | Public / Anonymous | Can browse catalog, search products, filter categories, and assemble a client-side shopping cart. |
| **Registered Customer (`ROLE_USER`)** | Authenticated | Can place orders, manage saved shipping addresses, view past orders, and receive order status notifications. |
| **Fulfillment Manager (`ROLE_MANAGER`)**| Internal Staff | Can manage product inventory, adjust catalog pricing, and advance order states (`PLACED` -> `CONFIRMED` -> `SHIPPED`). |
| **System Administrator (`ROLE_ADMIN`)** | Platform Admin | Full system oversight; manages security roles, configures payment/broker infrastructure, inspects and replays DLQ messages. |
