# Role-Permission Authorization Matrix

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Comprehensive Permissions Matrix

| Platform Capability / Operation | Anonymous Guest | Registered User (`ROLE_USER`) | Fulfillment Mgr (`ROLE_MANAGER`) | System Admin (`ROLE_ADMIN`) |
| :--- | :---: | :---: | :---: | :---: |
| **Browse Product Catalog** | ALLOWED | ALLOWED | ALLOWED | ALLOWED |
| **Full-Text Product Search** | ALLOWED | ALLOWED | ALLOWED | ALLOWED |
| **Assemble Local Shopping Cart** | ALLOWED | ALLOWED | ALLOWED | ALLOWED |
| **Register Account / Login** | ALLOWED | ALLOWED | ALLOWED | ALLOWED |
| **Manage Personal Addresses** | DENIED | ALLOWED | ALLOWED | ALLOWED |
| **Place Order (Checkout)** | DENIED | ALLOWED | ALLOWED | ALLOWED |
| **View Personal Order History** | DENIED | ALLOWED | ALLOWED | ALLOWED |
| **Cancel Personal Order (PLACED)**| DENIED | ALLOWED | ALLOWED | ALLOWED |
| **Create / Edit Products** | DENIED | DENIED | ALLOWED | ALLOWED |
| **Adjust Inventory Stock Levels** | DENIED | DENIED | ALLOWED | ALLOWED |
| **Transition Order Status (Fulfill)**| DENIED | DENIED | ALLOWED | ALLOWED |
| **View All Customer Orders** | DENIED | DENIED | ALLOWED | ALLOWED |
| **Inspect Dead Letter Queue (DLQ)**| DENIED | DENIED | DENIED | ALLOWED |
| **Replay Dead Letter Events** | DENIED | DENIED | DENIED | ALLOWED |
| **Access Observability (Grafana/Kibana)**| DENIED | DENIED | DENIED | ALLOWED |
