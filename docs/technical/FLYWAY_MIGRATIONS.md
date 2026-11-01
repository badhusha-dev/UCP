# Flyway Database Migrations Specification

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Migration Strategy & Versioning Conventions

The platform uses **Flyway** for deterministic, automated relational database versioning. Schema alterations are committed as SQL scripts in `src/main/resources/db/migration/` within each microservice module.

### Naming Standard
`V<Version>__<Description>.sql`
- `V`: Prefix for versioned migration.
- `<Version>`: Numeric version (e.g. `1`, `2`, `2.1`).
- `__`: Double underscore delimiter.
- `<Description>`: Snake_case summary of schema change.

---

## 2. Inventory of Implemented Migrations

| Microservice | Script Name | Changes Introduced |
| :--- | :--- | :--- |
| **`user-service`** | `V1__init_user_schema.sql` | Creates `users` and `addresses` tables, foreign keys, indexes, and initial admin seed. |
| **`product-service`**| `V1__init_product_schema.sql`| Creates `products` table, constraints (`stock >= 0`), indexes on category and name, and catalog seed data. |
| **`order-service`** | `V1__init_order_schema.sql` | Creates `orders` and `order_items` tables with cascade delete rules and check constraints. |
| **`notification-service`**| `V1__init_notification_schema.sql`| Baseline notification table for dispatch logging. |
| **`notification-service`**| `V2__add_idempotency_and_dlq.sql` | Adds `processed_events` table for deduplication and `dead_letter_events` table for poison-pill persistence. |

---

## 3. Configuration & Startup Mechanics

In `backend/config-repo/application.yml`:
```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
    validate-on-migrate: true
  jpa:
    hibernate:
      ddl-auto: validate
```

- When a service boots, Flyway inspects its dedicated `flyway_schema_history` table.
- Unapplied scripts execute inside an isolated transaction.
- If any migration fails, the transaction rolls back, startup halts immediately, and an error is logged.
- Setting `hibernate.ddl-auto` to `validate` ensures Hibernate never performs uncontrolled schema mutations.

---

## 4. Operational Monitoring

The migration status can be inspected at runtime via Spring Boot Actuator:
```bash
curl -s http://localhost:8081/actuator/flyway | jq .
```
Outputs installed migrations, checksums, execution execution times, and success status.
