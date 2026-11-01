# Entity-Relationship Diagrams (ERD)

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. User Service ERD (`user_db`)

```mermaid
erDiagram
    USERS ||--o{ ADDRESSES : "has many"
    USERS {
        bigserial id PK
        varchar email UK
        varchar password
        varchar name
        varchar role
        timestamptz created_at
    }
    ADDRESSES {
        bigserial id PK
        bigint user_id FK
        varchar street
        varchar city
        varchar state
        varchar zip_code
        varchar country
        boolean is_default
    }
```

---

## 2. Product Service ERD (`product_db`)

```mermaid
erDiagram
    PRODUCTS {
        bigserial id PK
        varchar name
        text description
        numeric price
        integer stock
        varchar category
        varchar image_url
        timestamptz created_at
    }
```

---

## 3. Order Service ERD (`order_db`)

```mermaid
erDiagram
    ORDERS ||--|{ ORDER_ITEMS : "contains"
    ORDERS {
        bigserial id PK
        bigint user_id
        numeric total_amount
        varchar status
        timestamptz created_at
    }
    ORDER_ITEMS {
        bigserial id PK
        bigint order_id FK
        bigint product_id
        integer quantity
        numeric price
    }
```

---

## 4. Notification Service ERD (`notification_db`)

```mermaid
erDiagram
    PROCESSED_EVENTS {
        varchar event_id PK
        timestamptz processed_at
    }
    DEAD_LETTER_EVENTS {
        bigserial id PK
        bigint order_id
        varchar event_type
        text payload
        text error_message
        integer retry_count
        timestamptz created_at
    }
```
