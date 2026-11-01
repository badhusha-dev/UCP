# Product & Catalog Business Rules

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Product Invariants & Validation Constraints

1. **Title & Naming**:
   - Product name must be non-empty and between 3 and 200 characters.
   - Unique names per category are recommended to prevent search confusion.
2. **Pricing & Currency**:
   - Prices must be positive values (`price > 0.00`) specified in USD with exactly two decimal places (`NUMERIC(10, 2)`).
   - Zero or negative pricing is rejected at the API Gateway and database constraints.
3. **Category Taxonomy**:
   - Every product must belong to an approved category: `Electronics`, `Fashion`, or `Home`.
   - New categories require administrative configuration.

---

## 2. Stock Level Thresholds & Visual Indicators

| Stock Range | Status Classification | UI Presentation | Business Action |
| :--- | :--- | :--- | :--- |
| **`stock >= 5`** | In Stock | Green Badge ("In Stock") | Normal sales allowed. |
| **`1 <= stock <= 4`** | Low Stock | Amber Badge ("Only X left!")| Urgency badge displayed; alerts inventory buyer. |
| **`stock == 0`** | Out of Stock | Red Badge ("Out of Stock") | "Add to Cart" button disabled; checkout blocked. |

---

## 3. Catalog Caching & Consistency Rules

- Read queries for catalog lists and single products are served from Redis L2 cache.
- Whenever a merchandiser modifies a product title, price, or image, the Redis cache for that product ID is evicted immediately (`@CacheEvict`).
- When a customer purchases an item, the inventory decrement automatically invalidates the Redis stock count.
