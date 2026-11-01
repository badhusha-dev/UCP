# Authentication & Authorization Architecture

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Authentication Mechanics & JWT Issuance

The platform implements stateless, token-based authentication using **JSON Web Tokens (JWT)**:
1. **User Login**: The client submits email and password to `POST /api/users/login`.
2. **Credential Verification**: `user-service` validates credentials against the database using `BCryptPasswordEncoder` with strength 10.
3. **JWT Minting**: Upon successful authentication, `user-service` generates an HMAC-SHA256 signed token.

### 1.1 Token Payload Structure
```json
{
  "sub": "admin@ecommerce.com",
  "userId": 1,
  "roles": ["ROLE_ADMIN"],
  "iat": 1729614000,
  "exp": 1729700400
}
```

---

## 2. Gateway Perimeter Validation

The Spring Cloud API Gateway acts as the policy enforcement point:
1. **Header Extraction**: Intercepts requests with header `Authorization: Bearer <token>`.
2. **Cryptographic Verification**: Validates signature using the shared `JWT_SECRET` key.
3. **Expiry Check**: Confirms `exp` timestamp is in the future.
4. **Context Forwarding**: Gateway decorates downstream HTTP request headers:
   - `X-User-Id: 1`
   - `X-User-Email: admin@ecommerce.com`
   - `X-User-Roles: ROLE_ADMIN`

---

## 3. Role-Based Access Control (RBAC)

The platform supports three distinct hierarchical roles:

| Role Name | Authority Scope | Examples of Accessible Endpoints |
| :--- | :--- | :--- |
| **`ROLE_USER`** | Customer self-service operations | `POST /api/orders/checkout`, `GET /api/orders/my-orders`, `GET/POST /api/users/addresses` |
| **`ROLE_MANAGER`** | Catalog and fulfillment operations | `POST /api/products`, `PUT /api/orders/{id}/status` |
| **`ROLE_ADMIN`** | Full platform administration | `GET/POST /api/notifications/dlq/**`, system configuration, user role mutations |

### Controller Protection
Microservices enforce method-level security using `@PreAuthorize`:
```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/dlq")
public ResponseEntity<List<DeadLetterEvent>> getDlqEvents() {
    return ResponseEntity.ok(dlqService.findAll());
}
```
