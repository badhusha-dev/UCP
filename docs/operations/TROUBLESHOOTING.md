# Troubleshooting & Diagnostics Guide

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Top Diagnostic Scenarios & Quick Fixes

### Problem 1: Frontend Microfrontend Shows White Screen / Minified React Error #31
- **Root Cause**: React version mismatch across federated remotes (React 19 vs React 18).
- **Remediation**:
  1. Inspect root `frontend/package.json`.
  2. Verify `overrides` section pins `"react": "18.3.1"` and `"react-dom": "18.3.1"`.
  3. Rebuild frontend containers: `docker compose build shell-app mfe-product mfe-order mfe-user`.

---

### Problem 2: Kafka Fails to Start with `NodeExistsException: /brokers/ids/1`
- **Root Cause**: Abrupt host termination left an ephemeral ZooKeeper znode unexpired.
- **Remediation**:
  ```bash
  docker compose stop kafka
  docker compose restart zookeeper
  sleep 5
  docker compose start kafka
  ```

---

### Problem 3: Order Checkout Fails with HTTP 500 / NullPointerException
- **Root Cause**: Client submitted `CartItemDto` without `price` field (`BigDecimal.multiply(price, quantity)` throws NPE).
- **Remediation**:
  - Ensure client payload includes `price` for every line item:
    ```json
    { "items": [ { "productId": 1, "quantity": 1, "price": 99.99 } ] }
    ```

---

### Problem 4: API Gateway Returns HTTP 503 "Service Unavailable"
- **Root Cause**: Downstream microservice (`order-service` or `product-service`) is down, causing Resilience4j circuit breaker to trip to `OPEN` state.
- **Remediation**:
  1. Check service container logs: `docker compose logs -f order-service`.
  2. Verify service is registered in Eureka: `http://localhost:8761`.
  3. Once restarted, the circuit automatically transitions to `HALF_OPEN` and resets to `CLOSED`.
