# Business Acceptance Criteria (Given-When-Then)

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Feature: Customer Authentication

### Scenario 1.1: Successful Customer Login
- **Given** a registered user exists with email `admin@ecommerce.com` and password `password123`.
- **When** the user submits credentials to `POST /api/users/login`.
- **Then** the system returns HTTP 200 with a valid JWT Bearer token containing `userId: 1` and role `ROLE_ADMIN`.

### Scenario 1.2: Invalid Password
- **Given** a user exists with email `admin@ecommerce.com`.
- **When** the user submits an incorrect password to `POST /api/users/login`.
- **Then** the system returns HTTP 401 Unauthorized with message "Invalid email or password".

---

## 2. Feature: Product Catalog & Search

### Scenario 2.1: Category Filtering
- **Given** products exist in categories `Electronics`, `Fashion`, and `Home`.
- **When** a shopper requests `GET /api/products?category=Electronics`.
- **Then** the system returns only products belonging to `Electronics` with HTTP 200 OK.

---

## 3. Feature: Order Checkout & Stock Reservation

### Scenario 3.1: Successful Checkout with Sufficient Stock
- **Given** Product #1 has `stock = 10` and price `$99.99`.
- **When** an authenticated user posts a checkout request for 1 unit of Product #1.
- **Then** an order is created with status `PLACED`, Product #1 stock becomes `9`, an `ORDER_CREATED` event is streamed to Kafka, and HTTP 200/201 is returned.

### Scenario 3.2: Checkout Rejection Due to Insufficient Stock
- **Given** Product #1 has `stock = 0`.
- **When** an authenticated user attempts to checkout 1 unit of Product #1.
- **Then** the transaction is rejected with HTTP 409 Conflict, no order is created, and stock remains `0`.
