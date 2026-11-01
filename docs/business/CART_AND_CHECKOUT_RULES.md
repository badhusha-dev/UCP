# Shopping Cart & Checkout Business Rules

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Cart Management Rules

1. **State Persistence**:
   - Cart contents are maintained in browser local storage and managed via Redux Toolkit across microfrontends.
   - Refreshing or navigating across pages does not discard cart contents.
2. **Item Quantity Limits**:
   - Minimum purchase quantity per line item is `1`.
   - Maximum purchase quantity per line item is capped at `10` units per transaction to mitigate reseller bot scraping.

---

## 2. Server-Side Price Verification & Anti-Tampering

To prevent malicious client-side price tampering (e.g. modifying DOM or JSON payload prices):
1. **Source of Truth**: The Order Service receives the `productId` and requested `quantity`.
2. **Catalog Cross-Check**: Order Service verifies the actual unit price against `product-service` database records.
3. **Calculation Formula**:
   $$\text{Total Amount} = \sum_{i=1}^{n} (\text{quantity}_i \times \text{verified\_price}_i)$$
4. Any discrepancy between client requested price and official catalog price results in recalculation or transaction rejection.

---

## 3. Checkout Pre-Conditions

An order can only proceed to checkout if all four criteria are satisfied:
1. **User Authentication**: Customer possesses a valid, non-expired JWT Bearer token.
2. **Cart Non-Empty**: Cart contains at least one valid product line item.
3. **Inventory Availability**: Real-time stock for all line items is greater than or equal to requested quantities.
4. **Valid Shipping Address**: Customer has selected or provided a valid street, city, state, and zip code.
