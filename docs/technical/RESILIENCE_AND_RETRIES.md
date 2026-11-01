# Resilience4j & Fault Tolerance Architecture

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Resilience Architecture Overview

The platform uses **Resilience4j 2.2.0** to protect distributed services from cascading failures and network latency spikes.

```
Incoming Request ──> [ Circuit Breaker ] ──> [ Bulkhead ] ──> [ Time Limiter ] ──> Downstream Service
                              │
                    (Trip on High Failure Rate)
                              │
                              ▼
                      [ Fallback Handler ]
```

---

## 2. Circuit Breaker Configurations (`backend/config-repo/application.yml`)

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50.0
        slowCallRateThreshold: 50.0
        slowCallDurationThreshold: 2000ms
        waitDurationInOpenState: 10000ms
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
  timelimiter:
    configs:
      default:
        timeoutDuration: 3000ms
```

### State Behavior
1. **CLOSED**: Normal operation. Calls pass to downstream dependencies.
2. **OPEN**: If 50% of the last 10 calls fail or take longer than 2000ms, the breaker trips to OPEN. All subsequent requests fail fast immediately to the fallback handler.
3. **HALF_OPEN**: After 10 seconds (`waitDurationInOpenState`), 3 trial calls are permitted. If they succeed, the breaker returns to CLOSED; otherwise, it returns to OPEN.

---

## 3. Fallback Strategies

| Service Context | Failure Trigger | Fallback Response | Customer Experience |
| :--- | :--- | :--- | :--- |
| **Gateway -> Order Service** | Order service down / timeout | HTTP 503 JSON with retry message | Friendly error message prompting retry in 30 seconds. |
| **Gateway -> Product Service** | Product service slow / degraded | Fallback returns cached catalog snapshot | Customers can browse top categories; checkout warns of maintenance. |
| **Order -> Product Decrement** | Product service unreachable | Order marked `PENDING_INVENTORY` | Order captured; async inventory verification scheduled. |
| **Notification Kafka Consumer** | SMTP provider failure / timeout | Retry 3 times, route to DLQ | Customer checkout unaffected; operations can replay from DLQ. |
