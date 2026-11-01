# Universal Commerce Platform (UCP) — Feature-to-Service Matrix

## 1. Complete Feature Implementation Matrix

| Feature | Primary Service | Controller Endpoint | Database Table(s) | Frontend Component | Verification Status |
|---|---|---|---|---|---|
| **1. Authentication & JWT** | `user-service` | `POST /api/users/login`<br>`POST /api/users/register` | `users` | `LoginPage.tsx`<br>`AuthService.ts` | **VERIFIED & ACTIVE** |
| **2. Profile & Addresses** | `user-service` | `GET /api/users/{id}`<br>`GET /api/users/{id}/addresses` | `customer_address`<br>`customer_loyalty` | `ProfilePage.tsx` (`mfe-user`) | **VERIFIED & ACTIVE** |
| **3. User Administration** | `user-service` | `GET /api/users/all`<br>`PUT /api/users/{id}/role` | `users` | `AdminUserManagement.tsx` (`mfe-user`) | **VERIFIED & ACTIVE** |
| **4. Product Catalog & Search** | `product-service` | `GET /api/products`<br>`GET /api/products/{id}` | `products`<br>`categories` | `ProductCatalog.tsx` (`mfe-product`) | **VERIFIED & ACTIVE** |
| **5. Product Admin CRUD** | `product-service` | `POST /api/products`<br>`PUT /api/products/{id}` | `products` | `AdminProductDesk.tsx` (`mfe-product`) | **VERIFIED & ACTIVE** |
| **6. Inventory & Stock Holds** | `product-service` | `POST /api/products/inventory/reserve`<br>`GET /api/products/inventory/low-stock` | `products`<br>`inventory_reservations` | `InventoryAnalyticsTab.tsx` | **VERIFIED & ACTIVE** |
| **7. Cart Management** | `shell-app` / `mfe-order` | Client Redux + Server validation | Client Redux `cartSlice` | `CartDrawer.tsx`<br>`OrderPage.tsx` | **VERIFIED & ACTIVE** |
| **8. Checkout & Order Creation** | `order-service` | `POST /api/orders` | `orders`<br>`order_items` | `CheckoutForm.tsx` (`mfe-order`) | **VERIFIED & ACTIVE** |
| **9. Order Lifecycle & Status** | `order-service` | `PUT /api/orders/{id}/status`<br>`GET /api/orders/user/{id}` | `orders`<br>`order_status_history` | `OrderPage.tsx`<br>`AdminOrderList.tsx` | **VERIFIED & ACTIVE** |
| **10. Returns & Refunds** | `order-service` | `POST /api/orders/{id}/returns`<br>`PUT /api/accounting/admin/refunds/{id}` | `order_returns`<br>`ledger_transactions` | `AdminLedgerPage.tsx` | **VERIFIED & ACTIVE** |
| **11. Accounting Ledgers** | `order-service` | `GET /api/accounting/admin/ledger`<br>`GET /api/accounting/customer/ledger` | `ledger_transactions` | `AdminLedgerPage.tsx`<br>`CustomerLedgerPage.tsx` | **VERIFIED & ACTIVE** |
| **12. Notifications Center** | `notification-service` | `GET /api/notifications/user/{userId}`<br>`PUT /api/notifications/{id}/read` | `notifications`<br>`notification_templates` | `NotificationBell.tsx` (`shell-app`) | **VERIFIED & ACTIVE** |
| **13. Executive Overview Analytics** | `order-service` | `GET /api/orders/admin/analytics/overview` | `orders`<br>`order_items` | `ExecutiveOverviewTab.tsx` (`mfe-order`) | **VERIFIED & ACTIVE** |
| **14. Sales & Revenue Analytics** | `order-service` | `GET /api/orders/admin/analytics/sales` | `orders` | `SalesAnalyticsTab.tsx` (`mfe-order`) | **VERIFIED & ACTIVE** |
| **15. Product Performance KPIs** | `order-service` | `GET /api/orders/admin/analytics/products` | `order_items` | `ProductPerformanceTab.tsx` (`mfe-order`) | **VERIFIED & ACTIVE** |
| **16. Customer Intelligence** | `order-service` | `GET /api/orders/admin/analytics/customers` | `orders` | `CustomerIntelligenceTab.tsx` (`mfe-order`) | **VERIFIED & ACTIVE** |
| **17. Filtered Orders Drill-Down** | `order-service` | `GET /api/orders/admin/analytics/orders` | `orders` | `OrdersDrilldownTab.tsx` (`mfe-order`) | **VERIFIED & ACTIVE** |
| **18. Operational Anomaly Alerts** | `order-service` | `GET /api/orders/admin/analytics/alerts` | `orders`<br>`order_returns` | `OperationalAlertsTab.tsx` (`mfe-order`) | **VERIFIED & ACTIVE** |
| **19. CSV Reports & Export Engine** | `order-service` | `GET /api/orders/admin/analytics/reports/export` | `orders`<br>`order_items` | `AdminAnalyticsDashboard.tsx` | **VERIFIED & ACTIVE** |
| **20. Distributed Tracing & Metrics**| `api-gateway` + all services | `/actuator/prometheus`<br>`/actuator/health` | Micrometer / Prometheus | Grafana Dashboards (:3030) | **VERIFIED & ACTIVE** |

---

## 2. Feature Responsibility & Boundary Rules

1. **Price & Stock Authority:**
   Prices and stock levels are never determined by the frontend. The frontend calculates estimates for cart subtotal UI feedback, but `order-service` validates all unit prices against trusted records and verifies stock reservations with `product-service` prior to completing order transactions.
2. **Double-Entry Accounting:**
   Financial activities (sales, taxes, shipping, coupon credits, refund debits) are posted to immutable rows in `ledger_transactions` with debit/credit balance verification.
3. **Metric Calculations:**
   Analytics metrics are derived via PostgreSQL database aggregations (`SUM`, `COUNT`, `AVG`, `GROUP BY`) executed in `order-service` and `product-service` without executing cross-database joins across service boundaries.
