# Inventory Management & Fulfillment Rules

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Inventory Reservation Policy

1. **Immediate Reservation on Checkout**: When a customer completes checkout, the requested inventory is immediately decremented from available stock. This prevents subsequent customers from purchasing the same items.
2. **Atomic Execution**: Stock deduction occurs within a single PostgreSQL transactional update (`WHERE stock >= requested_quantity`).
3. **Zero Negative Balances**: Under no circumstances does the system permit inventory to drop below zero.

---

## 2. Restocking & Compensating Actions

If an order is cancelled or aborted:
- An automated compensating transaction invokes `POST /api/products/{id}/increment-stock`.
- The physical units are returned to active catalog availability.
- The Redis catalog cache is invalidated immediately so storefront visitors see the restored stock.

---

## 3. Warehouse Fulfillment Guidelines

- Warehouse teams prioritize orders by creation timestamp (`ASC`).
- Before packing, barcode scans verify that picked items match `order_items` product IDs.
- Transitioning to `SHIPPED` requires recording carrier information (e.g. FedEx, UPS) and a valid tracking number.
