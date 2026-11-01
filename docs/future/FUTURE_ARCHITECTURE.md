# Future Architecture & Target State (v3.0)

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Target State Architectural Blueprint

In v3.0, the platform evolves from transactional microservices to an **Event-Sourced, CQRS-driven, Multi-Region Architecture**:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   Global Edge (Cloudflare / Fastly CDN)                │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                  GraphQL Federated BFF (Apollo Router)                 │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │ Commands (Write)               │ Queries (Read)
                    ▼                                ▼
       ┌────────────────────────────┐   ┌────────────────────────────┐
       │   Command Microservices    │   │  Read Projections (CQRS)   │
       │ (User, Product, Order Svc) │   │ (Elasticsearch / Redis OM) │
       └────────────┬───────────────┘   └────────────▲───────────────┘
                    │ Write Events                   │
                    ▼                                │ CDC Pipeline
       ┌────────────────────────────┐                │ (Debezium)
       │    Apache Kafka Cluster    ├────────────────┘
       │  (Multi-Region Event Bus)  │
       └────────────────────────────┘
```

---

## 2. Key Evolutionary Shifts

1. **CQRS & Event Sourcing**: Separates write operations (placing orders, modifying stock) from read queries (browsing catalog, viewing past orders), allowing read models to be replicated and cached globally.
2. **GraphQL Federation BFF**: Replaces REST aggregation with a unified supergraph schema, allowing frontend clients to fetch exact data trees in a single round-trip.
3. **Kafka Connect & Debezium CDC**: Eliminates manual dual-writing to Redis/Elasticsearch. Changes committed to PostgreSQL are captured via database WAL logs and streamed to Kafka automatically.
