# Event Schemas & Contracts

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Schema Specification: `OrderEvent`

Published on Kafka topic: `order-events`  
Serialization format: JSON (MIME type: `application/json`)

### 1.1 JSON Payload Schema
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "OrderEvent",
  "type": "object",
  "required": ["orderId", "userId", "eventType", "totalAmount", "status", "timestamp"],
  "properties": {
    "orderId": {
      "type": "integer",
      "description": "Unique identifier of the order"
    },
    "userId": {
      "type": "integer",
      "description": "Identifier of the customer"
    },
    "eventType": {
      "type": "string",
      "enum": ["ORDER_CREATED", "ORDER_CONFIRMED", "ORDER_SHIPPED", "ORDER_DELIVERED", "ORDER_CANCELLED"]
    },
    "totalAmount": {
      "type": "number",
      "description": "Total checkout price in USD"
    },
    "status": {
      "type": "string",
      "description": "Current lifecycle status"
    },
    "timestamp": {
      "type": "string",
      "format": "date-time",
      "description": "ISO-8601 creation timestamp"
    },
    "items": {
      "type": "array",
      "items": {
        "type": "object",
        "required": ["productId", "quantity", "price"],
        "properties": {
          "productId": { "type": "integer" },
          "quantity": { "type": "integer" },
          "price": { "type": "number" }
        }
      }
    }
  }
}
```

### 1.2 Concrete Example
```json
{
  "orderId": 79,
  "userId": 1,
  "eventType": "ORDER_CREATED",
  "totalAmount": 99.99,
  "status": "PLACED",
  "timestamp": "2026-10-22T16:31:40Z",
  "items": [
    {
      "productId": 1,
      "quantity": 1,
      "price": 99.99
    }
  ]
}
```

---

## 2. Evolution & Backwards Compatibility Rules

To ensure non-breaking changes across microservices:
1. **Additive Changes Only**: New fields must be marked optional or given default values.
2. **Never Remove Fields**: Deprecated fields must remain populated until all consumers are updated.
3. **Strict Partitioning**: `orderId` must always be passed as the Kafka record key to maintain order sequence.
