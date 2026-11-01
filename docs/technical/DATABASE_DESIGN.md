# Database Design & Schemas

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Database-Per-Service Topology

The platform enforces strict logical isolation across four PostgreSQL databases:

```
[user_db]            [product_db]          [order_db]           [notification_db]
  ├── users            ├── products          ├── orders           ├── notifications
  └── addresses        └── categories        └── order_items      ├── processed_events
                                                                  └── dead_letter_events
```

---

## 2. Service Schemas & DDL Specifications

### 2.1 User Service (`user_db`)
- **`users` Table**:
  - `id` (BIGSERIAL PRIMARY KEY)
  - `email` (VARCHAR(150) UNIQUE NOT NULL)
  - `password` (VARCHAR(255) NOT NULL - BCrypt Hash)
  - `name` (VARCHAR(100) NOT NULL)
  - `role` (VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER')
  - `created_at` (TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)
- **`addresses` Table**:
  - `id` (BIGSERIAL PRIMARY KEY)
  - `user_id` (BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE)
  - `street` (VARCHAR(255) NOT NULL)
  - `city` (VARCHAR(100) NOT NULL)
  - `state` (VARCHAR(100) NOT NULL)
  - `zip_code` (VARCHAR(20) NOT NULL)
  - `country` (VARCHAR(100) NOT NULL)
  - `is_default` (BOOLEAN DEFAULT FALSE)

### 2.2 Product Service (`product_db`)
- **`products` Table**:
  - `id` (BIGSERIAL PRIMARY KEY)
  - `name` (VARCHAR(200) NOT NULL)
  - `description` (TEXT)
  - `price` (NUMERIC(10, 2) NOT NULL)
  - `stock` (INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0))
  - `category` (VARCHAR(100) NOT NULL)
  - `image_url` (VARCHAR(500))
  - `created_at` (TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)
  - *Indexes*: `idx_products_category`, `idx_products_name`

### 2.3 Order Service (`order_db`)
- **`orders` Table**:
  - `id` (BIGSERIAL PRIMARY KEY)
  - `user_id` (BIGINT NOT NULL)
  - `total_amount` (NUMERIC(10, 2) NOT NULL)
  - `status` (VARCHAR(50) NOT NULL) — `PLACED`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`
  - `created_at` (TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)
- **`order_items` Table**:
  - `id` (BIGSERIAL PRIMARY KEY)
  - `order_id` (BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE)
  - `product_id` (BIGINT NOT NULL)
  - `quantity` (INTEGER NOT NULL CHECK (quantity > 0))
  - `price` (NUMERIC(10, 2) NOT NULL)

### 2.4 Notification Service (`notification_db`)
- **`processed_events` Table**:
  - `event_id` (VARCHAR(150) PRIMARY KEY) — Format: `ORDER-{orderId}-{eventType}`
  - `processed_at` (TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)
- **`dead_letter_events` Table**:
  - `id` (BIGSERIAL PRIMARY KEY)
  - `order_id` (BIGINT)
  - `event_type` (VARCHAR(100))
  - `payload` (TEXT NOT NULL)
  - `error_message` (TEXT)
  - `retry_count` (INTEGER DEFAULT 0)
  - `created_at` (TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)
