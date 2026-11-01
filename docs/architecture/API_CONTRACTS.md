# Universal Commerce Platform (UCP) — API Contracts

## 1. Global API Standards

- **Protocols & Formats:** REST over HTTP/1.1 and HTTP/2, JSON payloads (`Content-Type: application/json; charset=UTF-8`). CSV streaming (`Content-Type: text/csv`).
- **Security & Context:** Authenticated endpoints require `Authorization: Bearer <JWT_TOKEN>`. The API Gateway validates tokens and injects trusted internal headers:
  - `X-User-Id`: Numeric ID of the authenticated user.
  - `X-User-Email`: Email of the authenticated user.
  - `X-User-Role`: User role (`ADMIN`, `MANAGER`, `USER`).
- **Pagination Standard:** List endpoints return Spring Data `Page<T>`:
  ```json
  {
    "content": [ ... ],
    "pageable": { "pageNumber": 0, "pageSize": 20 },
    "totalElements": 142,
    "totalPages": 8,
    "last": false
  }
  ```
- **Error Response Standard:**
  ```json
  {
    "timestamp": "2026-10-23T15:30:00Z",
    "status": 403,
    "error": "Forbidden",
    "message": "Access denied: Staff authorization (Manager or Admin) required.",
    "path": "/api/orders/admin/analytics/overview"
  }
  ```

---

## 2. API Endpoint Catalog

### 2.1 User Service (`/api/users/**`)

#### `POST /api/users/login`
- **Access:** Public
- **Request Body:**
  ```json
  { "email": "admin@ecommerce.com", "password": "password123" }
  ```
- **Response (200 OK):**
  ```json
  { "token": "eyJhbGciOiJIUzI1NiJ9..." }
  ```

#### `POST /api/users/register`
- **Access:** Public
- **Request Body:**
  ```json
  { "name": "Jane Doe", "email": "jane@example.com", "password": "password123" }
  ```
- **Response (201 Created):**
  ```json
  { "id": 15, "name": "Jane Doe", "email": "jane@example.com", "role": "USER" }
  ```

#### `GET /api/users/all`
- **Access:** Admin only (`X-User-Role: ADMIN`)
- **Response (200 OK):** List of user profiles with roles and account creation timestamps.

---

### 2.2 Product Service (`/api/products/**`)

#### `GET /api/products`
- **Access:** Public
- **Query Parameters:** `page` (default 0), `size` (default 20), `category` (optional), `sort` (optional)
- **Response (200 OK):** Paginated array of products.

#### `POST /api/products/inventory/reserve`
- **Access:** Internal / Service (`X-User-Role: USER, MANAGER, ADMIN`)
- **Request Body:**
  ```json
  { "productId": 1, "quantity": 2, "orderReference": "ORD-98214" }
  ```
- **Response (200 OK):**
  ```json
  { "status": "RESERVED", "reservationId": 482, "expiresAt": "2026-10-23T15:45:00Z" }
  ```

#### `GET /api/products/inventory/low-stock`
- **Access:** Manager / Admin only (`X-User-Role: MANAGER, ADMIN`)
- **Response (200 OK):** List of products where `stock <= reorder_threshold`.

---

### 2.3 Order & Analytics Service (`/api/orders/**`)

#### `POST /api/orders`
- **Access:** Authenticated Customer (`X-User-Role: USER, MANAGER, ADMIN`)
- **Request Body:**
  ```json
  {
    "items": [
      { "productId": 1, "productName": "Wireless Headphones", "price": 125.00, "quantity": 2 }
    ],
    "couponCode": "SAVE20",
    "shippingAddress": "100 Silicon Way, Suite 400, San Francisco, CA"
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "id": 1042,
    "status": "PLACED",
    "totalAmount": 200.00,
    "discountAmount": 50.00,
    "createdAt": "2026-10-23T15:30:00Z"
  }
  ```

#### `GET /api/orders/admin/analytics/overview`
- **Access:** Manager / Admin only (`X-User-Role: MANAGER, ADMIN`)
- **Query Parameters:**
  - `timeRange`: `TODAY`, `YESTERDAY`, `LAST_7_DAYS`, `LAST_30_DAYS` (default), `CUSTOM`
  - `startDate`: ISO 8601 (required if `timeRange=CUSTOM`)
  - `endDate`: ISO 8601 (required if `timeRange=CUSTOM`)
- **Response (200 OK):**
  ```json
  {
    "grossMerchandiseValue": 18450.00,
    "netRevenue": 16920.00,
    "totalOrders": 124,
    "averageOrderValue": 136.45,
    "unitsSold": 289,
    "refundTotal": 1530.00,
    "cancellationRate": 4.8,
    "comparisonDeltaPercent": 14.2
  }
  ```

#### `GET /api/orders/admin/analytics/sales`
- **Access:** Manager / Admin only
- **Response (200 OK):** Daily time series data points containing date, revenue, orders count, and status distributions.

#### `GET /api/orders/admin/analytics/products`
- **Access:** Manager / Admin only
- **Response (200 OK):** Top selling products, units sold, revenue generated, and category performance breakdown.

#### `GET /api/orders/admin/analytics/customers`
- **Access:** Manager / Admin only
- **Response (200 OK):** New vs returning customer breakdown, repeat purchase rate, and customer cohort lifetime value.

#### `GET /api/orders/admin/analytics/reports/export`
- **Access:** Manager / Admin only
- **Query Parameters:** `startDate`, `endDate`, `status`
- **Response (200 OK):** Streamed RFC-4180 CSV file with `Content-Disposition: attachment; filename="ucp_orders_report_*.csv"`.

---

### 2.4 Accounting Service (`/api/accounting/**`)

#### `GET /api/accounting/admin/dashboard`
- **Access:** Manager / Admin only
- **Response (200 OK):** Total debits, total credits, net cash flow, reconciled ratio, and pending discrepancies.

#### `GET /api/accounting/admin/ledger`
- **Access:** Manager / Admin only
- **Query Parameters:** `page`, `size`, `userId`, `startDate`, `endDate`, `type`, `status`
- **Response (200 OK):** Paginated list of double-entry ledger transactions.
