# Universal Commerce Platform — Analytics Metric Dictionary

**Version:** 1.0.0  
**Effective Date:** October 23, 2026  
**Audience:** Full-Stack Engineers, Data Analysts, Product Managers, Store Administrators  
**Authority:** Single Source of Truth for Platform Analytics & Business Reporting  

---

## 1. Governance & Guiding Principles

To ensure commercial integrity and prevent financial misinformation across the Universal Commerce Platform:
1. **Mathematical Canon:** Every metric rendered on executive dashboards, reports, and exported files must derive from a single canonical formula defined in this document.
2. **Truth in Labeling:** Never label gross sales or net revenue as "Profit". Gross profit is only calculated when reliable wholesale product cost (COGS) is recorded.
3. **Database-Side Truth:** All aggregations must execute directly against PostgreSQL relational data models. No synthetic or guessed numbers may be presented to administrators.
4. **Transparent Boundaries:** All time periods must specify the active timezone (UTC standard) and explicit boundary conditions (inclusive start timestamp, exclusive end timestamp: `[start, end)`).

---

## 2. Metric Classification Matrix

| Metric Name | Status | Source of Truth | Verification Level |
| :--- | :--- | :--- | :--- |
| **Gross Merchandise Value (GMV)** | **Active** | `orders.total_amount` + `orders.discount_amount` | **Verified** |
| **Net Sales (Net Order Value)** | **Active** | `orders.total_amount` - Verified Refunds | **Verified** |
| **Orders Placed** | **Active** | `COUNT(orders.id)` | **Verified** |
| **Delivered / Completed Orders** | **Active** | `COUNT(orders.id)` WHERE `status = 'DELIVERED'` | **Verified** |
| **Average Order Value (AOV)** | **Active** | Net Sales / Completed & Active Orders | **Verified** |
| **Cancelled Orders & Rate** | **Active** | `COUNT(status = 'CANCELLED')` / Total Orders | **Verified** |
| **Refund Amount & Rate** | **Active** | `order_return.refund_amount` & Subledger `REFUND` | **Verified** |
| **Units Sold** | **Active** | `SUM(order_items.quantity)` for valid orders | **Verified** |
| **Current Low-Stock Products** | **Active** | `COUNT(products.stock_quantity <= reorder_threshold)` | **Verified** (via V4 migration) |
| **New vs. Returning Customers** | **Active** | Order count per distinct `user_id` | **Verified** |
| **Gross Profit / Margin** | **Conditional** | Net Sales - COGS (`cost_price` $\times$ `quantity`) | **Available only when cost configured** |
| **Cart-to-Order Conversion** | **Active** | Completed Orders / (Active Carts + Completed Orders) | **Verified** |

---

## 3. Canonical Metric Definitions & Mathematical Formulas

### 3.1 Gross Merchandise Value (GMV)
- **Description:** Total top-line monetary value of all merchandise transactions initiated across the platform prior to coupon deductions and refunds.
- **Order Lifecycle Status Inclusions:** `PLACED`, `PROCESSING`, `SHIPPED`, `DELIVERED`, `CONFIRMED`.
- **Order Lifecycle Status Exclusions:** `CANCELLED`.
- **Formula:**
  $$\text{GMV} = \sum_{o \in \text{ValidOrders}} (o.\text{totalAmount} + o.\text{discountAmount})$$
  *Alternatively expressed at line item granularity:*
  $$\text{GMV} = \sum_{i \in \text{ValidItems}} (i.\text{price} \times i.\text{quantity})$$

---

### 3.2 Net Sales (Net Order Value)
- **Description:** Realized gross sales net of discounts and processed customer refunds. Represents the actual recorded revenue of the store.
- **Formula:**
  $$\text{Net Sales} = \sum_{o \in \text{ValidOrders}} (o.\text{totalAmount}) - \sum_{r \in \text{ProcessedRefunds}} (r.\text{refundAmount})$$
- **Refund Source:** `order_return` records where `status = 'REFUNDED'` and subledger entries where `entry_type = 'REFUND'`.

---

### 3.3 Orders Placed & Status Breakdown
- **Orders Placed ($N_{\text{placed}}$):**
  $$N_{\text{placed}} = \text{COUNT}(orders) \quad \text{within } [t_{\text{start}}, t_{\text{end}})$$
- **Status Categories:**
  - **Pending / Processing:** `status IN ('PLACED', 'PROCESSING')`
  - **In Transit:** `status = 'SHIPPED'`
  - **Delivered (Fulfilled):** `status = 'DELIVERED'`
  - **Cancelled:** `status = 'CANCELLED'`
  - **Returned / Refunding:** `status IN ('RETURN_REQUESTED', 'RETURNED')`

---

### 3.4 Average Order Value (AOV)
- **Description:** Average monetary value generated per valid commercial transaction.
- **Denominator Rule:** Excludes cancelled orders (`status = 'CANCELLED'`). If valid order count is zero, AOV is strictly `$0.00` (prevents division by zero).
- **Formula:**
  $$\text{AOV} = \frac{\text{Net Sales}}{N_{\text{valid orders}}}$$

---

### 3.5 Cancellation Rate
- **Description:** Proportion of placed orders that were voided or aborted before fulfillment.
- **Formula:**
  $$\text{Cancellation Rate (\%)} = \begin{cases} 
  \left(\frac{N_{\text{cancelled}}}{N_{\text{total placed}}}\right) \times 100, & \text{if } N_{\text{total placed}} > 0 \\ 
  0.0\%, & \text{otherwise} 
  \end{cases}$$

---

### 3.6 Refund Rate
- **Description:** Ratio of refund dollars returned to customers compared against gross sales.
- **Formula:**
  $$\text{Refund Rate (\%)} = \begin{cases} 
  \left(\frac{\text{Total Refund Amount}}{\text{Gross Sales}}\right) \times 100, & \text{if Gross Sales} > 0 \\ 
  0.0\%, & \text{otherwise} 
  \end{cases}$$

---

### 3.7 Units Sold
- **Description:** Total physical inventory items successfully purchased in valid orders.
- **Formula:**
  $$\text{Units Sold} = \sum_{o \in \text{ValidOrders}} \left( \sum_{i \in o.\text{items}} i.\text{quantity} \right)$$

---

### 3.8 Customer Classification: New vs. Returning
- **New Customer:** A customer whose very first order in the platform history was created within the selected date window $[t_{\text{start}}, t_{\text{end}})$.
- **Returning Customer:** A customer who placed an order within $[t_{\text{start}}, t_{\text{end}})$ AND possesses at least one earlier order placed prior to $t_{\text{start}}$.
- **Repeat Purchase Rate:**
  $$\text{Repeat Purchase Rate (\%)} = \frac{N_{\text{customers with } \ge 2 \text{ lifetime orders}}}{N_{\text{total customers with } \ge 1 \text{ lifetime orders}}} \times 100$$

---

### 3.9 Low-Stock Product Count & Valuation
- **Configured Threshold Rule:** A product is classified as *Low Stock* when:
  $$\text{stock\_quantity} \le \text{reorder\_threshold} \quad \text{AND} \quad \text{stock\_quantity} > 0$$
- **Out of Stock:**
  $$\text{stock\_quantity} = 0$$
- **Inventory Valuation (Asset Value):**
  Calculated only when `cost_price` is populated:
  $$\text{Inventory Valuation} = \sum_{p \in \text{Products}} (p.\text{stock\_quantity} \times p.\text{cost\_price})$$
  *(Note: If `cost_price` is null, cost defaults to wholesale estimate or is excluded from asset valuation).*

---

### 3.10 Period-Over-Period Comparison & Trend Calculation
- **Previous Period Boundary Determination:**
  Let current period duration be $D = t_{\text{end}} - t_{\text{start}}$.
  The equivalent previous period is:
  $$[t_{\text{prev\_start}}, t_{\text{prev\_end}}) = [t_{\text{start}} - D, \, t_{\text{start}})$$
- **Percentage Change ($\Delta\%$):**
  $$\Delta\% = \begin{cases} 
  0.0\%, & \text{if } V_{\text{prev}} = 0 \text{ and } V_{\text{curr}} = 0 \\
  +100.0\%, & \text{if } V_{\text{prev}} = 0 \text{ and } V_{\text{curr}} > 0 \\
  -100.0\%, & \text{if } V_{\text{prev}} > 0 \text{ and } V_{\text{curr}} = 0 \\
  \left(\frac{V_{\text{curr}} - V_{\text{prev}}}{V_{\text{prev}}}\right) \times 100, & \text{otherwise}
  \end{cases}$$
- **Trend Indicator Direction:**
  - `UP` ($\uparrow$): $\Delta\% > 0.05\%$
  - `DOWN` ($\downarrow$): $\Delta\% < -0.05\%$
  - `FLAT` ($\rightarrow$): $-0.05\% \le \Delta\% \le +0.05\%$
  *(Note: For negative metrics such as Cancellation Rate and Refunds, `UP` is flagged as a Warning/Negative condition).*

---

## 4. Date & Timezone Conventions

1. **Storage & Processing Timezone:** All timestamps stored in database tables (`created_at`, `updated_at`, `processed_at`) are stored in `UTC` (`TIMESTAMP WITH TIME ZONE`).
2. **Query Filtering:** Date filter ranges provided by client (e.g., `2026-10-01` to `2026-10-23`) are converted to UTC midnight boundaries:
   - `startInstant`: `2026-10-01T00:00:00Z`
   - `endInstant`: `2026-10-23T23:59:59.999Z`
3. **Preset Date Ranges:**
   - **Today:** `[Today 00:00:00Z, Now]`
   - **Yesterday:** `[Yesterday 00:00:00Z, Today 00:00:00Z)`
   - **Last 7 Days:** `[Now - 7 days, Now]`
   - **Last 30 Days:** `[Now - 30 days, Now]`
   - **Month to Date (MTD):** `[1st of current month 00:00:00Z, Now]`
   - **Previous Month:** `[1st of last month 00:00:00Z, 1st of current month 00:00:00Z)`
   - **Custom:** Explicit user-selected `[from, to]` interval (maximum bounded to 365 days to prevent resource exhaustion).

---

## 5. Security & Formula Injection Defense (CSV Export)

When administrators export Sales, Orders, or Inventory reports to CSV format, malicious customer names, product titles, or order notes could execute arbitrary commands in Microsoft Excel or LibreOffice Calc (CSV/Formula Injection attack).

### 5.1 Neutralization Standard
Any textual cell string that begins with any of the following characters:
- Equals sign: `=`
- Plus sign: `+`
- Minus sign: `-`
- At symbol: `@`
- Tab character: `\t`
- Carriage return: `\r`

**Must be prefixed with a single quotation mark (`'`)** before outputting to the RFC-4180 CSV stream:
```java
public static String sanitizeForCsv(String value) {
    if (value == null) return "";
    String trimmed = value.trim();
    if (trimmed.startsWith("=") || trimmed.startsWith("+") || 
        trimmed.startsWith("-") || trimmed.startsWith("@") || 
        trimmed.startsWith("\t") || trimmed.startsWith("\r")) {
        return "'" + value;
    }
    return value;
}
```
This forces spreadsheet software to treat the contents strictly as literal text rather than executable formulas.
