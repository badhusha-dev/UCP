# Environment Variables Reference

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Master Environment Variables Specification

| Variable Name | Purpose | Required | Default Value | Sensitive | Consuming Service(s) | Restart Required |
| :--- | :--- | :---: | :--- | :---: | :--- | :---: |
| **`ENVIRONMENT`** | Specifies environment tier (`dev`, `stage`, `prod`). | YES | `production` | NO | All Services | YES |
| **`PLATFORM_HOST`** | Host domain or IP for CORS and link generation. | YES | `localhost` | NO | API Gateway, MFEs | YES |
| **`POSTGRES_USER`** | Master PostgreSQL administrative user. | YES | `postgres` | NO | Postgres, Microservices | YES |
| **`POSTGRES_PASSWORD`** | Master PostgreSQL administrative password. | YES | [Auto-generated] | **YES** | Postgres, Microservices | YES |
| **`POSTGRES_PORT`** | Host port mapped to PostgreSQL. | NO | `5432` | NO | Docker Host Mapping | YES |
| **`USER_DB_NAME`** | Database name for User Service. | YES | `user_db` | NO | User Service | YES |
| **`PRODUCT_DB_NAME`**| Database name for Product Service. | YES | `product_db` | NO | Product Service | YES |
| **`ORDER_DB_NAME`** | Database name for Order Service. | YES | `order_db` | NO | Order Service | YES |
| **`NOTIFICATION_DB_NAME`**| Database name for Notification Service.| YES | `notification_db`| NO | Notification Service | YES |
| **`REDIS_PASSWORD`** | Authentication password for Redis cache. | NO | [Auto-generated] | **YES** | Redis, Product Service | YES |
| **`REDIS_PORT`** | Host port mapped to Redis. | NO | `6379` | NO | Docker Host Mapping | YES |
| **`KAFKA_PORT`** | Host port mapped to Kafka broker. | NO | `9092` | NO | Docker Host Mapping | YES |
| **`JWT_SECRET`** | HMAC-SHA256 256-bit signing key for tokens. | YES | [Auto-generated] | **YES** | User Service, Gateway | YES |
| **`JWT_EXPIRATION_MS`**| Validity lifetime for tokens in ms (86400000 = 24h).| NO | `86400000` | NO | User Service | YES |
| **`GRAFANA_ADMIN_USER`**| Administrator username for Grafana. | YES | `admin` | NO | Grafana | YES |
| **`GRAFANA_ADMIN_PASSWORD`**| Administrator password for Grafana. | YES | [Auto-generated] | **YES** | Grafana | YES |
| **`JAVA_OPTS`** | JVM heap memory arguments for Spring Boot containers.| NO | `-Xms256m -Xmx512m`| NO | Microservices | YES |
| **`ES_JAVA_OPTS`** | JVM heap memory arguments for Elasticsearch. | NO | `-Xms1g -Xmx1g` | NO | Elasticsearch | YES |
