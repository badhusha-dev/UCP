# Core Business Workflows

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Catalog Merchandising Workflow

```
[ Merchandiser ] ──> Input Product Details & Initial Stock
                             │
                             ▼
                    [ Validation Check ]
                    (Price > 0, Stock >= 0, Valid Category)
                             │
                             ▼
                 [ Persist in product_db ]
                             │
                             ▼
                 [ Evict / Update Redis Cache ]
                             │
                             ▼
        [ Product Instantly Discoverable on Storefront ]
```

---

## 2. Order Checkout & Settlement Workflow

```
[ Customer Cart ] ──> Submit Checkout Request
                              │
                              ▼
                [ Server Price Verification ]
                 (Prevent client price tampering)
                              │
                              ▼
                [ Atomic Stock Reservation ]
                 (Check stock >= requested qty)
                    │                    │
          (Sufficient Stock)       (Insufficient Stock)
                    │                    │
                    ▼                    ▼
          [ Persist Order : PLACED ]   [ Reject with 409 Conflict ]
                    │
                    ▼
          [ Stream Event to Kafka ]
                    │
                    ▼
        [ Asynchronous Notification ]
```

---

## 3. Order Fulfillment & Dispatch Workflow

```
[ Orders with Status: PLACED ] ──> Payment Verification & Capture
                                               │
                                               ▼
                                      [ Mark: CONFIRMED ]
                                               │
                                               ▼
                                     [ Warehouse Picking ]
                                               │
                                               ▼
                                      [ Courier Handoff ]
                                               │
                                               ▼
                                      [ Mark: SHIPPED ]
                                               │
                                               ▼
                                      [ Mark: DELIVERED ]
```


---

## 4. Accounting Settlement & Reversal Governance Workflow

```
[ Customer Checkout ] ──> Server-Side Validation & Stock Lock
                              │
                              ▼
            [ Post Immutable Ledger Debit Entry ]
            (Reference: ORDER #ID, Type: ORDER_PAYMENT)
                              │
                              ▼
            [ Real-Time Customer Balance Update ]
            (Net Balance = Total Credits - Total Debits)
                              │
            ┌─────────────────┴──────────────────┐
            │                                    │
            ▼                                    ▼
   [ Order Cancelled / Refund ]         [ Accounting Governance ]
            │                                    │
            ▼                                    ├─> [ Manual Adjustment ]
   [ Post Compensating Credit ]                  │   (Reason & Ticket Ref Mandatory)
   (Idempotent ORDER_REFUND)                     │
                                                └─> [ Transaction Reversal ]
                                                    (Original Marked REVERSED,
                                                     Offsetting Entry Created,
                                                     Admin Audit Trail Recorded)
```
