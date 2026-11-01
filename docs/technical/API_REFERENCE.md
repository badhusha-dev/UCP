# REST API Reference Specification

**Universal Commerce Platform**  
*Document Version:* 2.0.0  
*Base URL:* `http://localhost:8080` (API Gateway)

---

## 1. Authentication & User Management (`/api/users`)

### 1.1 Register User
- **Method / Path**: `POST /api/users/register`
- **Auth Required**: NO
- **Request Body**:
```json
{
  "name": "Jane Doe",
  "email": "jane@example.com",
  "password": "Password123!",
  "role": "ROLE_USER"
}
```
- **Response (201 Created)**:
```json
{
  "id": 12,
  "name": "Jane Doe",
  "email": "jane@example.com",
  "role": "ROLE_USER"
}
```

### 1.2 User Login
- **Method / Path**: `POST /api/users/login`
- **Auth Required**: NO
- **Request Body**:
```json
{
  "email": "admin@ecommerce.com",
  "password": "password123"
}
```
- **Response (200 OK)**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "userId": 1,
  "email": "admin@ecommerce.com",
  "roles": ["ROLE_ADMIN"]
}
```

### 1.3 Get Shipping Addresses
- **Method / Path**: `GET /api/users/addresses`
- **Auth Required**: YES (`Bearer <token>`)
- **Response (200 OK)**:
```json
[
  {
    "id": 1,
    "street": "123 Commerce Blvd",
    "city": "Seattle",
    "state": "WA",
    "zipCode": "98101",
    "country": "USA",
    "isDefault": true
  }
]
```

---

## 2. Product Catalog Management (`/api/products`)

### 2.1 List Products
- **Method / Path**: `GET /api/products?page=0&size=10&category=Electronics`
- **Auth Required**: NO
- **Response (200 OK)**:
```json
[
  {
    "id": 1,
    "name": "Noise Cancelling Headphones",
    "description": "High fidelity over-ear headphones with ANC.",
    "price": 99.99,
    "category": "Electronics",
    "stock": 48,
    "imageUrl": "/images/headphones.jpg"
  }
]
```

### 2.2 Get Product Details
- **Method / Path**: `GET /api/products/{id}`
- **Auth Required**: NO (Served from Redis L2 Cache)
- **Response (200 OK)**: Single product object.

---

## 3. Order Processing (`/api/orders`)

### 3.1 Checkout Cart
- **Method / Path**: `POST /api/orders/checkout`
- **Auth Required**: YES (`Bearer <token>`)
- **Request Body**:
```json
{
  "items": [
    {
      "productId": 1,
      "quantity": 1,
      "price": 99.99
    }
  ]
}
```
- **Response (200 OK / 201 Created)**:
```json
{
  "id": 79,
  "userId": 1,
  "status": "PLACED",
  "totalAmount": 99.99,
  "createdAt": "2026-10-22T16:31:40Z",
  "items": [
    {
      "productId": 1,
      "quantity": 1,
      "price": 99.99
    }
  ]
}
```

### 3.2 List Customer Orders
- **Method / Path**: `GET /api/orders/my-orders`
- **Auth Required**: YES (`Bearer <token>`)
- **Response (200 OK)**: Array of Order objects associated with authenticated user.

---

## 4. Notifications & DLQ Management (`/api/notifications`)

### 4.1 Inspect Dead Letter Queue
- **Method / Path**: `GET /api/notifications/dlq`
- **Auth Required**: YES (`ROLE_ADMIN`)
- **Response (200 OK)**:
```json
[
  {
    "id": 1,
    "orderId": 55,
    "eventType": "ORDER_CREATED",
    "payload": "{\"orderId\":55}",
    "errorMessage": "Simulation: downstream SMTP unreachable",
    "retryCount": 3,
    "createdAt": "2026-10-22T14:10:00Z"
  }
]
```

### 4.2 Replay Dead Letter Event
- **Method / Path**: `POST /api/notifications/dlq/{id}/replay`
- **Auth Required**: YES (`ROLE_ADMIN`)
- **Response (200 OK)**:
```json
{
  "message": "Dead letter event 1 successfully replayed.",
  "status": "REPROCESSED"
}
```

---

## 5. Accounting & Financial Subledger (`/api/accounting`)

### 5.1 Customer Account Balance
- **Method / Path**: `GET /api/accounting/balance`
- **Auth Required**: YES (`Bearer <token>`, any authenticated user)
- **Response (200 OK)**:
```json
{
  "userId": 1,
  "currency": "USD",
  "totalCredits": 50.0000,
  "totalDebits": 120.0000,
  "netBalance": -70.0000,
  "explanation": "Account net balance is calculated as Total Settled Credits minus Total Settled Debits. A positive balance indicates credit on file; a negative balance reflects net settled purchases and debits."
}
```

### 5.2 Customer Personal Transaction Ledger
- **Method / Path**: `GET /api/accounting/ledger`
- **Auth Required**: YES (`Bearer <token>`, customer tenant-isolated)
- **Query Parameters**:
  - `startDate` (ISO LocalDateTime, optional, e.g. `2026-10-01T00:00:00`)
  - `endDate` (ISO LocalDateTime, optional, e.g. `2026-10-31T23:59:59`)
  - `type` (optional enum: `ORDER_PAYMENT`, `ORDER_REFUND`, `MANUAL_CREDIT`, `MANUAL_DEBIT`, `CORRECTION`)
  - `status` (optional enum: `POSTED`, `REVERSED`)
  - `page` (int, default `0`)
  - `size` (int, default `15`)
  - `sortBy` (string, default `createdAt`)
  - `sortDirection` (string, default `DESC`)
- **Response (200 OK)**: Spring Data `Page<LedgerTransactionDto>`

### 5.3 Customer Ledger Entry Details
- **Method / Path**: `GET /api/accounting/ledger/{id}`
- **Auth Required**: YES (`Bearer <token>`)
- **Security Check**: Enforces tenant boundary. If transaction belongs to another customer and caller lacks `ROLE_ADMIN`, returns `403 Forbidden`.
- **Response (200 OK)**:
```json
{
  "id": 1,
  "userId": 1,
  "transactionType": "ORDER_PAYMENT",
  "entryType": "DEBIT",
  "amount": 120.0000,
  "currency": "USD",
  "status": "POSTED",
  "description": "Payment for Order #101",
  "referenceType": "ORDER",
  "referenceId": "101",
  "idempotencyKey": "ORDER_PAYMENT_101",
  "reversalOfId": null,
  "reason": "Customer checkout settlement",
  "createdBy": "SYSTEM",
  "createdAt": "2026-10-22T10:15:30Z"
}
```

### 5.4 Customer Account Overview & KPI Metrics
- **Method / Path**: `GET /api/accounting/overview`
- **Auth Required**: YES (`Bearer <token>`, customer tenant-isolated)
- **Response (200 OK)**:
```json
{
  "userId": 10,
  "currency": "USD",
  "availableStoreCredit": 25.00,
  "lifetimeSpend": 199.98,
  "lifetimeRefunds": 99.99,
  "totalCredits": 124.99,
  "totalDebits": 199.98,
  "netActivity": -74.99,
  "pendingTransactionsCount": 0,
  "pendingTransactionsAmount": 0.00,
  "storeCreditExplanation": "Available store balance accrued from customer credits and refund adjustments, redeemable during checkout.",
  "lifetimeSpendExplanation": "Sum of all settled, successful order purchase payments posted to your account.",
  "lifetimeRefundsExplanation": "Total monetary value credited back to your account across all historical order cancellations and returns.",
  "netActivityExplanation": "Calculated as Total Credits minus Total Debits.",
  "pendingExplanation": "Transactions currently awaiting payment gateway settlement or review."
}
```

### 5.5 Itemized Order Invoice & Receipt
- **Method / Path**: `GET /api/accounting/invoices/{orderId}`
- **Auth Required**: YES (`Bearer <token>`, owner or `ROLE_ADMIN` / `ROLE_MANAGER`)
- **Response (200 OK)**:
```json
{
  "invoiceNumber": "INV-84-20261022",
  "orderId": 84,
  "orderDate": "2026-10-22T13:51:28Z",
  "userId": 10,
  "items": [
    {
      "productId": 1,
      "productName": "Ergonomic Mechanical Keyboard",
      "quantity": 1,
      "unitPrice": 99.99,
      "lineTotal": 99.99
    }
  ],
  "subtotal": 99.99,
  "discountAmount": 0.00,
  "shippingFee": 0.00,
  "taxAmount": 0.00,
  "totalAmount": 99.99,
  "currency": "USD",
  "orderStatus": "PLACED",
  "paymentStatus": "PAID",
  "paymentMethodLabel": "Simulated Merchant Gateway - Tokenized Settlement",
  "paymentReference": "PAY_REF_84",
  "disclaimer": "This receipt is generated from internal platform order records for customer transaction verification."
}
```

### 5.6 Customer Account Statement CSV Export
- **Method / Path**: `GET /api/accounting/statements/export`
- **Auth Required**: YES (`Bearer <token>`)
- **Query Parameters**: `startDate` (optional), `endDate` (optional)
- **Response (200 OK)**: CSV stream (`Content-Disposition: attachment; filename=account_statement_...csv`) containing date, reference, description, entry type, amount, status, and running balance totals.

### 5.7 Customer Refund Requests Workflow
- **Submit Request**: `POST /api/accounting/refunds/request`
  - **Auth Required**: YES (`Bearer <token>`, must own order)
  - **Request Body**:
  ```json
  {
    "orderId": 84,
    "amount": 99.99,
    "reason": "Arrived with damaged keycap; requesting full return"
  }
  ```
  - **Response (201 Created)**: `RefundRequestDto` with status `REQUESTED`.
- **List Customer Requests**: `GET /api/accounting/refunds`
  - **Response (200 OK)**: List of customer's active and historical refund requests.

### 5.8 Saved Payment Methods (Tokenized Metadata)
- **Method / Path**: `GET /api/accounting/payment-methods`
- **Auth Required**: YES (`Bearer <token>`)
- **Security Invariant**: Never returns raw PANs or CVVs. Returns PCI-compliant tokenized metadata:
```json
[
  {
    "id": 1,
    "provider": "SIMULATED_GATEWAY",
    "cardBrand": "Visa",
    "last4": "4242",
    "expMonth": 12,
    "expYear": 2028,
    "isDefault": true,
    "label": "Visa ending in •••• 4242 (Tokenized)"
  }
]
```

### 5.9 Customer Accounting Preferences
- **Get Preferences**: `GET /api/accounting/preferences`
- **Update Preferences**: `PUT /api/accounting/preferences`
  - **Request Body**: `{"emailReceipts": true, "statementNotifications": false}`

---

### 5.10 Admin Accounting Dashboard
- **Method / Path**: `GET /api/accounting/admin/dashboard`
- **Auth Required**: YES (`ROLE_ADMIN` or `ROLE_MANAGER`)
- **Response (200 OK)**:
```json
{
  "currency": "USD",
  "grossSales": 199.98,
  "totalRefunds": 99.99,
  "netRecordedActivity": -74.99,
  "totalCredits": 124.99,
  "totalDebits": 199.98,
  "failedPaymentsCount": 0,
  "pendingTransactionsCount": 0,
  "pendingAmount": 0.00,
  "flaggedDiscrepanciesCount": 0,
  "unreconciledCount": 0,
  "disclaimer": "Notice: These figures represent internal platform transaction subledger totals recorded in order_db. They are not audited financial statements or GAAP/IFRS balance sheets."
}
```

### 5.11 Admin Cross-Customer Transaction Ledger
- **Method / Path**: `GET /api/accounting/admin/ledger`
- **Auth Required**: YES (`ROLE_ADMIN` or `ROLE_MANAGER` read access)
- **Query Parameters**: `userId`, `customerEmail`, `referenceId`, `type`, `status`, `reconciliationStatus`, `startDate`, `endDate`, `page`, `size`.
- **Response (200 OK)**: Spring Data `Page<LedgerTransactionDto>` across all customers.

### 5.12 Admin Master Ledger CSV Export
- **Method / Path**: `GET /api/accounting/admin/export`
- **Auth Required**: YES (`ROLE_ADMIN` or `ROLE_MANAGER`)
- **Behavior**: Streams master ledger CSV and writes an immutable audit log entry (`ADMIN_LEDGER_EXPORT_CSV`).

### 5.13 Admin Manual Accounting Adjustment
- **Method / Path**: `POST /api/accounting/admin/adjustments`
- **Auth Required**: YES (Strictly `ROLE_ADMIN`; `ROLE_MANAGER` rejected with 403 Forbidden)
- **Request Body**:
```json
{
  "userId": 10,
  "transactionType": "MANUAL_CREDIT",
  "amount": 25.00,
  "currency": "USD",
  "referenceType": "GOODWILL_GESTURE",
  "reference": "PROMO-OCT-2026",
  "reason": "Customer loyalty celebration bonus"
}
```
- **Response (201 Created)**: Created `LedgerTransactionDto`. Automatically registers audit trail record with administrator email and timestamp.

### 5.14 Admin Transaction Reversal
- **Method / Path**: `POST /api/accounting/admin/reversals/{transactionId}`
- **Auth Required**: YES (Strictly `ROLE_ADMIN`)
- **Request Body**:
```json
{
  "reason": "Duplicate charge verified with acquiring bank"
}
```
- **Behavior**: Immutable accounting invariant. The original posted record is never silently deleted or mutated. A linked compensating entry with opposite entry type (`CREDIT` <-> `DEBIT`) is posted, neutralizing the balance mathematically to zero while preserving the complete audit trail.
- **Response (200 OK)**: Compensating reversing `LedgerTransactionDto`.

### 5.15 Admin Refund Governance Workflow
- **List Refund Requests**: `GET /api/accounting/admin/refunds` (`ROLE_ADMIN` / `ROLE_MANAGER`)
- **Process Refund Request**: `POST /api/accounting/admin/refunds/{id}/process`
  - **Auth Required**: YES (Strictly `ROLE_ADMIN`)
  - **Request Body**:
  ```json
  {
    "approved": true,
    "decisionNotes": "Proof of damage accepted. Credited full amount to customer ledger."
  }
  ```
  - **Behavior**: Upon approval, transitions status to `PROCESSED`, sets Order status to `REFUNDED`, creates an immutable `ORDER_REFUND` credit entry in `ledger_transactions`, and registers an immutable audit trail record.

### 5.16 Admin Reconciliation Management
- **Method / Path**: `PUT /api/accounting/admin/reconciliation/{transactionId}`
- **Auth Required**: YES (Strictly `ROLE_ADMIN`)
- **Request Body**:
```json
{
  "status": "RECONCILED",
  "notes": "Settlement batch confirmed with banking gateway"
}
```
- **Statuses Supported**: `RECONCILED`, `PENDING_REVIEW`, `FLAGGED_DISCREPANCY`.
- **Response (200 OK)**: Updated `LedgerTransactionDto` with audit trail record registered.

### 5.17 Admin Immutable Audit Trail
- **Method / Path**: `GET /api/accounting/admin/audit-logs`
- **Auth Required**: YES (`ROLE_ADMIN` or `ROLE_MANAGER`)
- **Query Parameters**: `page`, `size`
- **Response (200 OK)**: Spring Data `Page<AccountingAuditLogDto>` with administrator ID, email, action, before/after snapshots, and timestamps.
