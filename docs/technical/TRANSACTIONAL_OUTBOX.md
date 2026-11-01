# Transactional Outbox (`ucp-outbox`)

Shared module in `backend/outbox`. A service writes an event to its own `outbox_events` table inside the same database transaction as the business change. A scheduled publisher sends committed events to Kafka afterwards.

**Why:** publishing from the request thread lost events whenever Kafka was down, and slowed requests while the producer blocked (K5). With the outbox, the state change and its event commit or roll back together, and a broker outage only delays delivery.

## Adopted by

| Service | Topics | Format | Consumers |
|---|---|---|---|
| order-service | `order-events` (key: order id) | TEMPLATE (StringSerializer) | accounting, analytics, crm, notification |
| inventory-service | `inventory.events` (key: product id) | RAW | product-service |
| stock-service | `inventory.events` (key: product id) | RAW | product-service |
| cart-service | `cart.events` (key: cart id) | TEMPLATE (JsonSerializer) | none found |
| loyalty-service | `loyalty.tier.upgraded`, `loyalty.points.earned`, `loyalty.points.redeemed` (no key) | TEMPLATE (JsonSerializer) | crm-service |

All adopted on 2026-11-01. Event bytes on the wire did not change for any topic (see payload formats below).

Not adopted, by design:

- **delivery-service** has its own Postgres-only outbox (`DeliveryOutboxPublisher`). It is reliable already and can move onto this module later.
- **notification-service** `DeadLetterService` republishes stored dead letters when an operator asks. The event is already persisted, so an outbox adds nothing.

## Payload formats

Each row records how its payload is sent, so that moving a service to the outbox never changes what consumers receive.

| `payload_format` | Written with | Sent with | Use when |
|---|---|---|---|
| `TEMPLATE` (default) | `OutboxWriter.append` | The service's own `KafkaTemplate`, so its configured value serializer applies | The service already sent a `String` payload |
| `RAW` | `OutboxWriter.appendRaw` | A string-serializer template built from the service's `spring.kafka` settings | The service sent an object through `JsonSerializer`. Serialise it first with `JacksonUtils.enhancedObjectMapper()`, the mapper `JsonSerializer` uses |

`InventoryProducerServiceTest` and `StockProducerServiceTest` assert that the RAW JSON is byte-for-byte what `JsonSerializer` produced. RAW sends carry no `__TypeId__` header; product-service, the only consumer of `inventory.events`, reads the raw bytes and ignores headers.

## Delivery semantics

- **At least once.** An event can be sent twice: for example, when an instance crashes after Kafka accepted the event but before it was marked published. Consumers must be idempotent.
- **Order** follows `created_at` within a batch. There is no global ordering guarantee across retries.
- **Retries** back off exponentially: 1 s, 2 s, 4 s and so on, capped at 5 minutes. Retries never stop; a stuck event stays visible in the table.
- **Multiple instances** are safe. An event is claimed with a conditional update, so only one instance sends it at a time. A claim held by a crashed instance expires after `lock-duration`.
- **Retention:** published rows are deleted after `retention` (default 7 days).

## Adopting it in a service

1. Add the dependency:

   ```xml
   <dependency>
       <groupId>com.ecommerce</groupId>
       <artifactId>ucp-outbox</artifactId>
       <version>${project.version}</version>
   </dependency>
   ```

2. Add a Flyway migration with the DDL from `backend/outbox/src/main/resources/ucp-outbox/schema.sql` (see `cart-service` `V2__outbox_events.sql`).
3. Inject `OutboxWriter` and call it from a `@Transactional` method, in place of `KafkaTemplate.send`: `append(topic, key, eventType, payload)` if the service sent a `String`, or `appendRaw(...)` with `JacksonUtils.enhancedObjectMapper()` JSON if it sent an object through `JsonSerializer`.
4. In the service's test `application.yml`, if tests build the schema from entities:

   ```yaml
   ucp:
     outbox:
       initialize-schema: true
       publisher:
         enabled: false
   ```

5. Assert in a test that the expected row is written to `outbox_events`.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `ucp.outbox.publisher.enabled` | `true` | Run the scheduled publisher (requires a `KafkaTemplate` bean) |
| `ucp.outbox.poll-interval-ms` | `2000` | Delay between publisher runs |
| `ucp.outbox.batch-size` | `50` | Events per run |
| `ucp.outbox.send-timeout` | `PT10S` | Wait for Kafka to acknowledge one send |
| `ucp.outbox.lock-duration` | `PT60S` | How long a claim protects an in-flight event |
| `ucp.outbox.retention` | `P7D` | Age at which published rows are deleted |
| `ucp.outbox.initialize-schema` | `false` | Create the table at startup (tests only; production uses Flyway) |

## Operations

- Unpublished backlog: `SELECT status, COUNT(*) FROM outbox_events GROUP BY status;` (`OutboxRepository.countUnpublished()` exposes the same count).
- A send failure logs a WARN with the event id, type, topic and attempt number.

## Not yet done

- Event envelope (catalog §5.3: `eventId`, `version`, `tenantId` and so on). Payloads are still each service's original format, so existing consumers are unaffected. `tenant_id` is in the table but not yet filled.
- A metrics gauge for backlog size and age.
- Moving delivery-service onto the module.
- An idempotency review of the consumers (accounting, analytics, crm, notification, product). Delivery is now at least once, where direct sends were at most once.
- Verification against a live broker and Postgres. Tests use H2 and a mocked `KafkaTemplate`.
