# Error Handling & Fault Representation Standard

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Unified Error Response Format (RFC 7807)

All microservices implement a `@RestControllerAdvice` global exception handler that transforms exceptions into a uniform JSON structure:

```json
{
  "timestamp": "2026-10-22T16:31:40.512Z",
  "status": 409,
  "error": "Conflict",
  "message": "Insufficient stock for product ID 1. Requested: 10, Available: 4",
  "path": "/api/orders/checkout",
  "traceId": "4c49615d474c4fce",
  "validationErrors": null
}
```

---

## 2. Standard HTTP Status Code Conventions

| Status Code | Reason Phrase | Typical Scenario in Platform |
| :--- | :--- | :--- |
| **400** | Bad Request | Malformed JSON syntax, invalid enum value, illegal order state transition. |
| **401** | Unauthorized | Missing or expired JWT Bearer token on secured endpoint. |
| **403** | Forbidden | Valid JWT token, but user lacks required authority (`ROLE_ADMIN` required for DLQ replay). |
| **404** | Not Found | Requested entity (Product, Order, Address) does not exist in database. |
| **409** | Conflict | Duplicate user email registration, insufficient product inventory during checkout. |
| **422** | Unprocessable Entity | Bean Validation failure (`@NotNull`, `@Min(1)`); detailed errors returned in `validationErrors`. |
| **500** | Internal Server Error | Unhandled runtime exception (database connection failure, null pointer). |
| **503** | Service Unavailable | Resilience4j circuit breaker open or downstream dependency timed out. |

---

## 3. Frontend Error Boundaries

Each microfrontend (`mfe-product`, `mfe-order`, `mfe-user`) is wrapped in a React `ErrorBoundary` within the `shell-app`:
- If an uncaught rendering error occurs in a remote component, only that remote falls back to a graceful error banner ("Component temporarily unavailable").
- The remainder of the storefront shell and unaffected microfrontends remain fully interactive.
