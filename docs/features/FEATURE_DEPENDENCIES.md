# Feature Inter-Dependency Map

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Feature Dependency Topology

```
[ FT-01 Shell App ] ─── requires ───> [ FT-02 Product MFE ]
                     ─── requires ───> [ FT-03 Order MFE ]
                     ─── requires ───> [ FT-04 User MFE ]

[ FT-13 Checkout ]   ─── requires ───> [ FT-06 JWT Authentication ]
                     ─── requires ───> [ FT-12 Atomic Stock Decrement ]
                     ─── triggers ───> [ FT-15 Kafka Event Publishing ]

[ FT-16 Deduplication ] ── requires ──> [ FT-15 Kafka Event Publishing ]
[ FT-18 DLQ Replay ]    ── requires ──> [ FT-17 Non-Blocking DLQ ]
```

---

## 2. Infrastructure Prerequisite Matrix

| Feature | Database Prerequisite | Broker / Cache Prerequisite | Registry / Config Prerequisite |
| :--- | :--- | :--- | :--- |
| **Catalog & Search (`FT-09`)** | `product_db` (Postgres) | `redis` (Port 6379) | `discovery-server`, `config-server` |
| **User Profile (`FT-08`)** | `user_db` (Postgres) | None | `discovery-server`, `config-server` |
| **Order Placement (`FT-13`)** | `order_db` (Postgres) | `kafka` (Port 9092) | `discovery-server`, `config-server` |
| **Notification DLQ (`FT-18`)** | `notification_db` (Postgres)| `kafka` (Port 9092) | `discovery-server`, `config-server` |
