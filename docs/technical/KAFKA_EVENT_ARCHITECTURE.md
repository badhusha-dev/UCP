# Apache Kafka Event-Driven Architecture

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Overview & Event-Driven Topology

The Universal Commerce Platform employs **Apache Kafka 7.4.0** (Port 9092) with ZooKeeper coordination (Port 2181) as its primary asynchronous event bus. This decouples time-critical synchronous checkout from background tasks like notification dispatch and downstream analytics.

```
┌─────────────────┐                                  ┌──────────────────────┐
│  Order Service  │                                  │ Notification Service │
│ (Port 8083)     │                                  │ (Port 8084)          │
└────────┬────────┘                                  └──────────▲───────────┘
         │                                                      │
         │ Producer (acks=all)                                  │ Consumer Group:
         ▼                                                      │ notification-group
┌───────────────────────────────────────────────────────────────┴───────────┐
│                        Kafka Broker (Topic: order-events)                 │
│   Partition 0 [ Key: orderId=1 | ORDER_CREATED ]                          │
│   Partition 1 [ Key: orderId=2 | ORDER_CREATED ]                          │
│   Partition 2 [ Key: orderId=1 | ORDER_CONFIRMED ]                        │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Producer Reliability Configurations (`order-service`)

To prevent message loss and duplicate event emission at the broker layer, `order-service` configures:
```yaml
spring:
  kafka:
    producer:
      bootstrap-servers: kafka:9092
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 5
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
```
- **Partition Key**: The producer explicitly assigns the numerical `orderId` as the record key (`kafkaTemplate.send("order-events", String.valueOf(order.getId()), event)`). This guarantees that all lifecycle events for a specific order land on the exact same Kafka partition and are processed strictly in chronological order.

---

## 3. Consumer Deduplication & DLQ (`notification-service`)

1. **Idempotent Consumption**: Consumer queries `processed_events` using composite key `ORDER-{orderId}-{eventType}` before executing side-effects.
2. **Dead Letter Queue (DLQ)**: Configured using Spring Kafka's `@RetryableTopic` with backoff attempts (3 retries). Poison-pill records route to `@DltHandler`, persisting into `dead_letter_events`.

---

## 4. Kafka UI Administration

The platform bundles **Provectus Kafka UI** on port `8090` (`http://localhost:8090`):
- Visualizes cluster broker health, topics, partitions, consumer lag, and offsets.
- Inspects real-time payload contents for topic `order-events`.
