# Universal Commerce Platform (UCP) — Service Catalog

## 1. Inventory of Deployable Services

| Service Name | Category | Internal Port | External Port | Framework / Runtime | Database / State | Health Endpoint |
|---|---|---|---|---|---|---|
| **api-gateway** | Edge Ingress | 8080 | 8080 | Spring Boot 3.2.4 / Netty | None (Stateless) | `/actuator/health` |
| **discovery-server** | Governance | 8761 | 8761 | Spring Cloud Netflix Eureka | In-Memory Registry | `/actuator/health` |
| **config-server** | Governance | 8888 | 8888 | Spring Cloud Config Server | Git / File (`/config-repo`) | `/actuator/health` |
| **user-service** | Business | 8081 | 8081 | Spring Boot 3.2.4 / Tomcat | PostgreSQL (`user_db`) | `/actuator/health` |
| **product-service** | Business | 8082 | 8082 | Spring Boot 3.2.4 / Tomcat | PostgreSQL (`product_db`), Redis | `/actuator/health` |
| **order-service** | Business | 8083 | 8083 | Spring Boot 3.2.4 / Tomcat | PostgreSQL (`order_db`), Kafka | `/actuator/health` |
| **notification-service** | Business | 8084 | 8084 | Spring Boot 3.2.4 / Tomcat | PostgreSQL (`notification_db`), Kafka | `/actuator/health` |
| **shell-app** | Frontend Host | 80 | 3000 | Vite 8 + React 18.3.1 / Nginx | LocalStorage, Redux | `/index.html` |
| **mfe-product** | Microfrontend | 80 | 3001 | Vite 8 + React 18.3.1 / Nginx | Static Webpack Remote | `/assets/remoteEntry.js` |
| **mfe-order** | Microfrontend | 80 | 3002 | Vite 8 + React 18.3.1 / Nginx | Static Webpack Remote | `/assets/remoteEntry.js` |
| **mfe-user** | Microfrontend | 80 | 3003 | Vite 8 + React 18.3.1 / Nginx | Static Webpack Remote | `/assets/remoteEntry.js` |

---

## 2. Service Profiles and Responsibilities

### 2.1 API Gateway (`api-gateway`)
- **Source Path:** [`backend/api-gateway`](file:///d:/practical/ecommerce-platform/backend/api-gateway)
- **Language/Build:** Java 17, Maven (`api-gateway-1.0.0-SNAPSHOT.jar`)
- **Key Responsibilities:**
  - Edge reverse proxy routing requests to downstream microservices via Eureka load-balanced `lb://` addresses.
  - Global CORS handling for origins `http://localhost:3000` through `http://localhost:3003`.
  - OWASP security headers injection (`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Strict-Transport-Security`, `Permissions-Policy`).
  - Spring Cloud CircuitBreakers (Resilience4j) with graceful fallback endpoints (`/fallback/user`, `/fallback/product`, `/fallback/order`, `/fallback/notification`).
  - Swagger UI / OpenAPI 3 aggregation across all microservices at `/swagger-ui.html`.

### 2.2 User Service (`user-service`)
- **Source Path:** [`backend/user-service`](file:///d:/practical/ecommerce-platform/backend/user-service)
- **Language/Build:** Java 17, Maven (`user-service-1.0.0-SNAPSHOT.jar`)
- **Database:** `jdbc:postgresql://postgres:5432/user_db` (Flyway migrations `V1`, `V2`)
- **Key Responsibilities:**
  - User registration, login authentication, and JWT token issuance with HMAC-SHA256 signing.
  - Role management (`ADMIN`, `MANAGER`, `USER`).
  - Customer shipping/billing address book management.
  - Customer loyalty tiering (`GOLD`, `SILVER`, `BRONZE`) and points ledger.
  - User profile management and administrative user directory (`/api/users/all`).

### 2.3 Product Service (`product-service`)
- **Source Path:** [`backend/product-service`](file:///d:/practical/ecommerce-platform/backend/product-service)
- **Language/Build:** Java 17, Maven (`product-service-1.0.0-SNAPSHOT.jar`)
- **Database:** `jdbc:postgresql://postgres:5432/product_db` (Flyway migrations `V1`, `V2`), Redis
- **Key Responsibilities:**
  - Product catalog CRUD, multi-criteria filtering, price sorting, search indexing.
  - Category taxonomy management.
  - Inventory tracking, stock deduction, and atomic reservation holds.
  - Low-stock and out-of-stock anomaly detection (`/api/products/inventory/low-stock`).
  - Redis cache-aside caching for frequently browsed catalog items.

### 2.4 Order & Analytics Service (`order-service`)
- **Source Path:** [`backend/order-service`](file:///d:/practical/ecommerce-platform/backend/order-service)
- **Language/Build:** Java 17, Maven (`order-service-1.0.0-SNAPSHOT.jar`)
- **Database:** `jdbc:postgresql://postgres:5432/order_db` (Flyway migrations `V1`, `V2`, `V3`), Kafka
- **Key Responsibilities:**
  - Order creation, checkout coordination, idempotency validation, snapshotting prices and taxes.
  - Order status state transitions (`PLACED`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CANCELLED`).
  - Returns and refund workflow governance (`order_returns`).
  - Double-entry accounting ledger (`ledger_transactions`).
  - **Executive Business Analytics Engine:** Gross Merchandise Value (GMV), Net Sales, Average Order Value (AOV), Cohort Retention, Anomaly Alerts, and RFC-4180 streaming CSV exports.

### 2.5 Notification Service (`notification-service`)
- **Source Path:** [`backend/notification-service`](file:///d:/practical/ecommerce-platform/backend/notification-service)
- **Language/Build:** Java 17, Maven (`notification-service-1.0.0-SNAPSHOT.jar`)
- **Database:** `jdbc:postgresql://postgres:5432/notification_db` (Flyway migrations `V1`, `V2`), Kafka
- **Key Responsibilities:**
  - Asynchronous event consumer for order lifecycle and payment state changes.
  - Notification template rendering for order confirmation, shipment dispatch, and password alerts.
  - Simulated and SMTP delivery adapters with exponential backoff retries.
  - Unread notification feed and customer notification center (`/api/notifications/user/{userId}`).

---

## 3. Frontend Microfrontends Catalog

### 3.1 Shell Application (`shell-app`)
- **Path:** [`frontend/shell-app`](file:///d:/practical/ecommerce-platform/frontend/shell-app)
- **Port:** 3000
- **Responsibilities:**
  - Host application providing universal layout, top navigation bar, announcements, and footer.
  - Global JWT session management, automatic login persistence, and account switching.
  - Role-based routing guards (`ProtectedRoute`, `RoleGuard`).
  - Notifications bell with real-time polling and badge counter.

### 3.2 Product Microfrontend (`mfe-product`)
- **Path:** [`frontend/mfe-product`](file:///d:/practical/ecommerce-platform/frontend/mfe-product)
- **Port:** 3001
- **Exposed Remote:** `./App` (consumed as `product_mfe/App`)
- **Responsibilities:** Storefront catalog browsing, product detail view, search, category filter chips, and stock badges.

### 3.3 Order & Analytics Microfrontend (`mfe-order`)
- **Path:** [`frontend/mfe-order`](file:///d:/practical/ecommerce-platform/frontend/mfe-order)
- **Port:** 3002
- **Exposed Remote:** `./App` (consumed as `order_mfe/App`)
- **Responsibilities:** Reactive shopping cart drawer, checkout workflow, customer ledger, order history, and the complete **Executive Business Analytics Dashboard** (`/orders/analytics`).

### 3.4 User Microfrontend (`mfe-user`)
- **Path:** [`frontend/mfe-user`](file:///d:/practical/ecommerce-platform/frontend/mfe-user)
- **Port:** 3003
- **Exposed Remote:** `./App` (consumed as `user_mfe/App`)
- **Responsibilities:** Customer profile management, address book CRUD, loyalty points balance view, and administrator user management directory.
