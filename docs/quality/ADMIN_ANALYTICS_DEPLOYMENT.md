# Universal Commerce Platform — Analytics Deployment & Configuration Guide

**Platform:** Universal Commerce Platform (UCP)  
**Document Status:** Release Ready  
**Date:** October 23, 2026  

---

## 1. Prerequisites & Environment Requirements

| Component | Minimum Version | Production Standard | Notes |
| :--- | :--- | :--- | :--- |
| **Java JDK** | 17 LTS | Eclipse Temurin 17 / 21 | Spring Boot 3.2.4 compatibility |
| **Node.js** | 18 LTS | Node.js 20+ | Vite 8 + Module Federation |
| **PostgreSQL** | 14 | PostgreSQL 15 | Native JSONB and time functions |
| **Redis** | 6.2 | Redis 7 | Optional caching for aggregates |
| **Kafka** | 3.0 | Apache Kafka 3.4+ | Asynchronous commerce event stream |

---

## 2. Environment Variables & Configurations

### 2.1 Backend Services (`order-service`, `product-service`)
```env
# Database Credentials
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ecommerce_db
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres

# Flyway Database Migration
SPRING_FLYWAY_ENABLED=true
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true

# Redis Cache (Optional)
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379

# JWT Token Validation
JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
```

### 2.2 Frontend Microfrontends (`shell-app`, `mfe-order`)
```env
# Gateway Reverse Proxy Base
VITE_API_GATEWAY_URL=http://localhost:8080

# Microfrontend Remote Origins
VITE_ORDER_MFE_URL=http://localhost:3002
VITE_PRODUCT_MFE_URL=http://localhost:3001
VITE_USER_MFE_URL=http://localhost:3003
```

---

## 3. Database Migrations Execution
Database migrations run automatically on startup via Flyway:
1. `product-service`: `V4__product_cost_and_reorder_threshold.sql` adds `cost_price` and `reorder_threshold` with default and backfill values.
2. `order-service`: `V5__order_analytics_indexes.sql` creates b-tree performance indexes on orders, items, and foreign keys.

To run migrations manually:
```bash
mvn flyway:migrate -pl product-service
mvn flyway:migrate -pl order-service
```

---

## 4. Operational Health & Telemetry Endpoints
- **Order Analytics Overview:** `GET /api/orders/admin/analytics/overview` (Staff role required)
- **Daily Sales Trends:** `GET /api/orders/admin/analytics/sales`
- **Product & Category Performance:** `GET /api/orders/admin/analytics/products`
- **Customer Intelligence:** `GET /api/orders/admin/analytics/customers`
- **Inventory Intelligence:** `GET /api/products/admin/analytics/inventory`
- **Bottlenecks & Alerts:** `GET /api/orders/admin/analytics/alerts`
- **Spreadsheet CSV Export:** `GET /api/orders/admin/analytics/reports/export`
- **Prometheus Metrics:** `GET /actuator/prometheus` (Includes JVM, HikariCP, and HTTP request metrics)
