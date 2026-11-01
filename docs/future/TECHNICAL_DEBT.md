# Technical Debt Registry & Remediation Plan

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Technical Debt Inventory

| Debt ID | Component | Technical Debt Description | Severity | Remediation Plan | Target Horizon |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **DEBT-01** | Kafka / ZooKeeper | Apache Kafka 7.4.0 currently requires ZooKeeper container (port 2181), which introduces ephemeral znode state risk on abrupt reboots. | High | Migrate Kafka to KRaft (Kafka Raft Metadata) mode, removing ZooKeeper dependency completely. | v2.1.0 |
| **DEBT-02** | Inventory Reservation | Order checkout synchronously invokes Product Service to decrement stock via RestTemplate. Downstream slowness could tie up gateway threads. | Medium | Implement Transactional Outbox pattern with Debezium and asynchronous Saga choreography for stock locks. | v2.2.0 |
| **DEBT-03** | Monorepo Maven POM | Single parent `pom.xml` links all microservice modules, making independent service build versioning interdependent. | Low | Decouple microservices into separate repositories or distinct BOM parent imports. | v2.2.0 |
| **DEBT-04** | Client-Side Cart Storage | Guest shopping cart stored solely in browser `localStorage`. If user switches devices, cart does not migrate until checkout. | Medium | Implement Redis-backed server cart session synchronized on add-to-cart events. | v2.1.0 |
| **DEBT-05** | Direct DB Migrations | Flyway executes automatically on Spring Boot application boot. In large K8s clusters, multiple replicas might contest migration locks. | Low | Separate Flyway executions into dedicated Kubernetes `Job` resources that run prior to deployment rollouts. | v2.1.0 |
