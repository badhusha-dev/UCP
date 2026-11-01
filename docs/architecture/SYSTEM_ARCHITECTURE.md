# Universal Commerce Platform (UCP) — System Architecture

## 1. Executive Summary & Architecture Overview

The Universal Commerce Platform (UCP) is an enterprise-grade, single-store e-commerce platform architected as a decoupled distributed system. It utilizes Spring Boot 3 microservices on the backend, React 18 microfrontends (Module Federation) on the frontend, PostgreSQL for relational storage, Redis for distributed caching, Apache Kafka for asynchronous domain events, and an enterprise observability stack (Prometheus, Grafana, Zipkin, ELK Stack).

```mermaid
graph TD
    subgraph Clients["Client Layer"]
        Browser["Desktop & Mobile Browsers"]
        AdminUser["Admin / Operations Staff"]
    end

    subgraph Edge["Edge & Ingress Routing"]
        NginxMFE["Nginx Web Servers (Ports 3000-3003)"]
        APIGateway["Spring Cloud API Gateway (Port 8080)"]
    end

    subgraph Microfrontends["Frontend Microfrontends (Module Federation)"]
        ShellApp["shell-app (Port 3000)"]
        ProductMFE["mfe-product (Port 3001)"]
        OrderMFE["mfe-order (Port 3002)"]
        UserMFE["mfe-user (Port 3003)"]
    end

    subgraph Microservices["Backend Business Microservices"]
        UserService["user-service (Port 8081)"]
        ProductService["product-service (Port 8082)"]
        OrderService["order-service & Analytics (Port 8083)"]
        NotificationService["notification-service (Port 8084)"]
    end

    subgraph PlatformControl["Platform & Config Mesh"]
        Eureka["Eureka Discovery (Port 8761)"]
        ConfigServer["Spring Cloud Config (Port 8888)"]
    end

    subgraph DataTier["Data & Event Infrastructure"]
        PostgresDB[("PostgreSQL 15 (Port 5432)")]
        RedisCache[("Redis 7 (Port 6379)")]
        KafkaCluster[("Apache Kafka 7.4 (Port 9092)")]
    end

    Browser --> NginxMFE
    AdminUser --> NginxMFE
    NginxMFE --> ShellApp
    ShellApp -.-> ProductMFE
    ShellApp -.-> OrderMFE
    ShellApp -.-> UserMFE
    ShellApp --> APIGateway
    ProductMFE --> APIGateway
    OrderMFE --> APIGateway
    UserMFE --> APIGateway

    APIGateway --> UserService
    APIGateway --> ProductService
    APIGateway --> OrderService
    APIGateway --> NotificationService

    Microservices -.-> Eureka
    Microservices -.-> ConfigServer

    UserService --> PostgresDB
    ProductService --> PostgresDB
    OrderService --> PostgresDB
    NotificationService --> PostgresDB

    ProductService --> RedisCache
    OrderService --> RedisCache

    ProductService --> KafkaCluster
    OrderService --> KafkaCluster
    KafkaCluster --> NotificationService
```

---

## 2. Container and Deployment Architecture

The entire platform is containerized using Docker and multi-stage builds. In local and staging environments, Docker Compose coordinates 22 containers across isolated networks. In production, Kubernetes manifests and Istio service mesh enforce Zero-Trust network policies and mTLS.

```mermaid
flowchart TD
    subgraph HostPorts["Host Ingress Sockets"]
        P3000["Host :3000 (Shell)"]
        P3001["Host :3001 (Product MFE)"]
        P3002["Host :3002 (Order MFE)"]
        P3003["Host :3003 (User MFE)"]
        P8080["Host :8080 (API Gateway)"]
        P8761["Host :8761 (Eureka)"]
        P8888["Host :8888 (Config)"]
        P3030["Host :3030 (Grafana)"]
        P5601["Host :5601 (Kibana)"]
    end

    subgraph DockerBridge["Bridge Network: ecommerce-network"]
        subgraph FrontendContainers["Web / Nginx Containers"]
            C_Shell["ecommerce-platform-shell-app-1"]
            C_Product["ecommerce-platform-mfe-product-1"]
            C_Order["ecommerce-platform-mfe-order-1"]
            C_User["ecommerce-platform-mfe-user-1"]
        end

        subgraph CoreServices["Spring Boot Microservice Pods"]
            C_GW["api-gateway"]
            C_US["user-service"]
            C_PS["product-service"]
            C_OS["order-service"]
            C_NS["notification-service"]
            C_Config["config-server"]
            C_Eureka["discovery-server"]
        end

        subgraph InfraContainers["Databases & Middleware"]
            C_Postgres["postgres:15-alpine (user_db, product_db, order_db, notification_db)"]
            C_Redis["redis:7-alpine"]
            C_Kafka["cp-kafka:7.4.0"]
            C_ZK["cp-zookeeper:7.4.0"]
        end
    end

    P3000 --> C_Shell
    P3001 --> C_Product
    P3002 --> C_Order
    P3003 --> C_User
    P8080 --> C_GW
    P8761 --> C_Eureka
    P8888 --> C_Config

    C_Shell --> C_GW
    C_GW --> C_US
    C_GW --> C_PS
    C_GW --> C_OS
    C_GW --> C_NS

    C_US --> C_Postgres
    C_PS --> C_Postgres
    C_OS --> C_Postgres
    C_NS --> C_Postgres

    C_PS --> C_Redis
    C_OS --> C_Redis
    C_OS --> C_Kafka
    C_Kafka --> C_NS
    C_Kafka --> C_ZK
```

---

## 3. Frontend Microfrontend Architecture (Module Federation)

The frontend is partitioned into independent Vite + TypeScript applications orchestrated via `@originjs/vite-plugin-federation` and pinned to React 18.3.1.

```mermaid
flowchart LR
    subgraph Shell["Shell Application (Host: Port 3000)"]
        AppShell["App Router & Navigation Shell"]
        GlobalTheme["Material-UI Marketplace Theme"]
        ReduxStore["Global Redux Toolkit Store"]
        AuthBoundary["ProtectedRoute & RoleGuard"]
    end

    subgraph Remotes["Remote Microfrontends (Vite Webpack Federation)"]
        RemoteProduct["product_mfe (Port 3001)
        /products/*
        Catalog, Grid, Search, Filters"]

        RemoteOrder["order_mfe (Port 3002)
        /orders/*, /orders/analytics, /orders/ledger
        Cart, Checkout, Admin Analytics, Accounting"]

        RemoteUser["user_mfe (Port 3003)
        /profile/*, /profile/admin
        Profile Desk, User Admin, Address Book"]
    end

    subgraph SharedLib["@ecommerce/shared-library (Workspace npm)"]
        SharedAuth["AuthService & authSlice (JWT persistence)"]
        SharedCart["cartSlice (Shopping Cart Reducer)"]
        SharedGuards["ProtectedRoute & RoleGuard components"]
    end

    AppShell --> RemoteProduct
    AppShell --> RemoteOrder
    AppShell --> RemoteUser
    AppShell --> SharedLib
    RemoteProduct --> SharedLib
    RemoteOrder --> SharedLib
    RemoteUser --> SharedLib
```

---

## 4. Backend Service Dependencies & Discovery

All services bootstrap by querying `config-server` on port 8888, register their dynamic network locations with `discovery-server` (Netflix Eureka) on port 8761, and expose CircuitBreakers via Resilience4j.

```mermaid
flowchart TD
    Config["config-server (:8888)
    Serves application.yml & service profiles"]
    Eureka["discovery-server (:8761)
    Heartbeats, dynamic registry, client load balancing"]

    Gateway["api-gateway (:8080)
    Spring Cloud Gateway, CircuitBreaker, JWT extraction"]

    User["user-service (:8081)
    Auth, Roles, Profile, Addresses, Loyalty"]
    Product["product-service (:8082)
    Products, Categories, SKUs, Inventory Desk"]
    Order["order-service (:8083)
    Orders, Cart Checkout, Executive Analytics, Ledgers"]
    Notification["notification-service (:8084)
    Email/SMS dispatch, Template engine, DLQ retry"]

    Gateway -.->|Fetch routes| Config
    User -.->|Fetch config| Config
    Product -.->|Fetch config| Config
    Order -.->|Fetch config| Config
    Notification -.->|Fetch config| Config

    Gateway -->|Resolve targets| Eureka
    User -->|Register & Heartbeat| Eureka
    Product -->|Register & Heartbeat| Eureka
    Order -->|Register & Heartbeat| Eureka
    Notification -->|Register & Heartbeat| Eureka

    Gateway -->|Proxy /api/users/**| User
    Gateway -->|Proxy /api/products/**| Product
    Gateway -->|Proxy /api/orders/** & /api/accounting/**| Order
    Gateway -->|Proxy /api/notifications/**| Notification
```

---

## 5. Order Placement Sequence

The checkout workflow coordinates stock reservations, payment authorization (simulated sandbox or real provider), transactional database commit, and asynchronous event streaming.

```mermaid
sequenceDiagram
    autonumber
    actor Customer as Shopper (Browser)
    participant Shell as Shell / Order MFE
    participant GW as API Gateway
    participant OS as Order Service
    participant PS as Product Service
    participant DB as PostgreSQL (order_db)
    participant Kafka as Apache Kafka (order-events)
    participant NS as Notification Service

    Customer->>Shell: Clicks "Complete Purchase"
    Shell->>GW: POST /api/orders (OrderRequestDto + JWT)
    GW->>OS: Validate Token & Route Request
    OS->>PS: POST /api/products/inventory/reserve (Check & Hold Stock)
    alt Stock Available
        PS-->>OS: 200 OK (Reservation Confirmed)
        OS->>OS: Calculate Final Tax, Shipping & Discount
        OS->>DB: BEGIN Transaction -> INSERT order, order_items, ledger_entry
        DB-->>OS: COMMIT Transaction (Order Created: PLACED)
        OS->>Kafka: Publish "order-created" event (OrderId, UserId, Amount)
        OS-->>GW: 201 Created (OrderResponseDto)
        GW-->>Shell: 201 Created
        Shell-->>Customer: Display Order Confirmation & Tracking Number
        Kafka->>NS: Consume "order-created"
        NS->>NS: Render Order Confirmation Template
        NS->>Customer: Send Order Confirmation Email/SMS (Simulated/SMTP)
    else Out of Stock
        PS-->>OS: 409 Conflict (Insufficient Stock)
        OS-->>GW: 409 Conflict
        GW-->>Shell: 409 Conflict
        Shell-->>Customer: Show "Item sold out during checkout" alert
    end
```

---

## 6. Inventory Reservation and Release Workflow

To prevent overselling under high concurrency, stock checks and reservations are isolated and atomic.

```mermaid
sequenceDiagram
    autonumber
    participant Cart as Checkout Process
    participant PS as Product Service (Inventory)
    participant DB as PostgreSQL (product_db)
    participant Redis as Redis Stock Cache
    participant Timer as Expiry Scheduler / Reaper

    Cart->>PS: Reserve Stock (ProductId, Qty=2)
    PS->>DB: SELECT stock FROM products WHERE id=? FOR UPDATE
    alt stock >= 2
        PS->>DB: UPDATE products SET stock = stock - 2 WHERE id=?
        PS->>DB: INSERT INTO inventory_reservations (order_ref, qty, expires_at)
        PS->>Redis: Invalidate product stock cache
        PS-->>Cart: Reservation Succeeded (Hold valid for 15 mins)
    else stock < 2
        PS-->>Cart: Insufficient Stock Error
    end

    alt Checkout Completed Within 15 Min
        Cart->>PS: Confirm Consumption
        PS->>DB: UPDATE inventory_reservations SET status='COMMITTED'
    else Checkout Abandoned / Cancelled / Expired
        Timer->>PS: Scan Expired Reservations
        PS->>DB: UPDATE products SET stock = stock + 2 WHERE id=?
        PS->>DB: UPDATE inventory_reservations SET status='RELEASED'
        PS->>Redis: Invalidate product stock cache
    end
```

---

## 7. Payment, Refund, and Accounting Lifecycle

Transactions are recorded in a double-entry style accounting ledger inside `order_db`.

```mermaid
stateDiagram-v2
    [*] --> PENDING_PAYMENT : Order Created
    PENDING_PAYMENT --> AUTHORIZED : Payment Gateway Sandbox Approved
    PENDING_PAYMENT --> FAILED : Card Declined / Timeout
    FAILED --> [*] : Release Reserved Stock

    AUTHORIZED --> CAPTURED : Fulfillment Initiated
    CAPTURED --> DELIVERED : Shipment Completed

    DELIVERED --> REFUND_REQUESTED : Customer Files Return
    REFUND_REQUESTED --> REFUNDED : Admin Reviews & Approves
    REFUND_REQUESTED --> REJECTED : Admin Denies (Breaches Policy)

    REFUNDED --> [*] : Ledger Credit Dispatched
```

---

## 8. Analytics Data Flow

Executive analytics, daily time series, cohort intelligence, and CSV export streaming run server-side database aggregations without cross-database join violations.

```mermaid
flowchart TD
    subgraph OperationalStores["Operational Databases (PostgreSQL 15)"]
        OrderDB[("order_db
        orders, order_items,
        order_returns, ledger_transactions")]
        ProductDB[("product_db
        products, inventory_movement")]
        UserDB[("user_db
        users, customer_loyalty")]
    end

    subgraph AnalyticsEngine["Analytics Engine (order-service & product-service)"]
        AdminAnalyticsController["AdminAnalyticsController (:8083)
        /api/orders/admin/analytics/*"]
        InventoryAnalyticsController["InventoryAnalyticsController (:8082)
        /api/products/admin/analytics/inventory"]
        AggregationService["AdminAnalyticsService
        SQL SUM, COUNT, AVG, GROUP BY Date"]
        CSVStreamingWriter["RFC-4180 Streaming CSV Generator
        Formula injection sanitized"]
    end

    subgraph Presentation["Admin Dashboard (mfe-order)"]
        KPIWidgets["Executive KPI Cards (GMV, Net Sales, AOV, Orders)"]
        DailySalesChart["Revenue & Volume Time-Series"]
        ProductRankings["Top 10 Selling SKUs & Category Distribution"]
        CohortAnalysis["New vs Returning Customer Ratios"]
        CSVDownloadButton["1-Click CSV Report Download"]
    end

    OrderDB --> AggregationService
    AggregationService --> AdminAnalyticsController
    AggregationService --> CSVStreamingWriter

    AdminAnalyticsController --> KPIWidgets
    AdminAnalyticsController --> DailySalesChart
    AdminAnalyticsController --> ProductRankings
    AdminAnalyticsController --> CohortAnalysis
    CSVStreamingWriter --> CSVDownloadButton
```

---

## 9. Kafka Event Flow and Failure Recovery

Kafka decouples asynchronous operations with dead-letter queues (DLQ) and exponential backoff retry policies.

```mermaid
flowchart LR
    subgraph Producers["Event Producers"]
        OS_Prod["Order Service
        (order-created, order-status-changed)"]
        PS_Prod["Product Service
        (low-stock-alert)"]
    end

    subgraph KafkaBroker["Apache Kafka (Port 9092)"]
        TopicOrders["Topic: ecommerce-order-events"]
        TopicStock["Topic: ecommerce-inventory-events"]
        TopicDLQ["Topic: ecommerce-dead-letter-queue"]
    end

    subgraph Consumers["Event Consumers"]
        NS_Cons["Notification Service
        Consumer Group: notification-group"]
        Analytics_Cons["Analytics Aggregator
        Consumer Group: analytics-group"]
    end

    OS_Prod --> TopicOrders
    PS_Prod --> TopicStock

    TopicOrders -->|Consume| NS_Cons
    TopicStock -->|Consume| NS_Cons
    TopicOrders -->|Consume| Analytics_Cons

    NS_Cons -.->|On 3 Failed Retries| TopicDLQ
```
