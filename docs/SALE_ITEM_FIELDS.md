# Bajaj Spare Parts Inventory & Sales Management System
## Sale Item Field Definitions (`sale_items`)

**Module:** Sales Management & Point of Sale (POS) — Member 5  
**System:** Bajaj Spare Parts Inventory & Sales Management System  
**Technology Stack:** React + Vite, Java 21 + Spring Boot, PostgreSQL + Flyway  
**Package Root:** `com.bajaj.inventory_backend`  
**API Base URL:** `/api/v1/sales`  
**Conventions:** JSON: `camelCase` | Database: `snake_case` | Monetary Precision: Java `BigDecimal`, PostgreSQL `NUMERIC(12,2)`  
**File Location:** `docs/SALE_ITEM_FIELDS.md`

---

## 1. Overview

The `sale_items` entity stores the individual line-item records associated with a parent `sales` header. Each row represents a specific Bajaj spare part purchased, capturing the unit quantity, agreed selling price, frozen acquisition cost snapshot, and computed line total.

---

## 2. Sale Item Field Specifications Table

| Field Name (Java / JSON) | Column Name (SQL) | Java Type | PostgreSQL Type | Nullable | Primary Purpose | Key Validation & Calculation Rules |
| :--- | :--- | :--- | :--- | :---: | :--- | :--- |
| **`id`** | `id` | `Long` | `BIGINT` | **No** (PK) | Internal synthetic primary key uniquely identifying the line item. | Auto-generated via database sequence (`BIGSERIAL`). Immutable. |
| **`saleId`** | `sale_id` | `Long` | `BIGINT` | **No** (FK) | Foreign key linking the line item to its parent sale header (`sales.id`). | `@NotNull`, `@Positive`, `ON DELETE RESTRICT` (or cascaded with parent sale). |
| **`productId`** | `product_id` | `Long` | `BIGINT` | **No** (FK) | Foreign key referencing the catalog spare part (`products.id`). | `@NotNull`, `@Positive`, must reference active product (`is_active = true`). |
| **`quantity`** | `quantity` | `Integer` | `INTEGER` | **No** | Number of units of the spare part purchased in this line item. | `@NotNull`, `@Min(1)`, strictly $\ge 1$; must be $\le$ available on-hand stock locked at checkout. |
| **`unitSellingPrice`** | `unit_selling_price` | `BigDecimal` | `NUMERIC(12,2)` | **No** | Actual unit price charged to the customer at the time of checkout. | `@NotNull`, `@DecimalMin("0.00")`, max 10 integer digits + 2 decimals. Server-verified against catalog price. |
| **`unitCostSnapshot`** | `unit_cost_snapshot` | `BigDecimal` | `NUMERIC(12,2)` | **No** | Acquisition cost per unit captured from inventory at checkout time. | `@NotNull`, `@DecimalMin("0.00")`, max 10 integer digits + 2 decimals. Server-populated from inventory; client cannot provide. |
| **`lineTotal`** | `line_total` | `BigDecimal` | `NUMERIC(12,2)` | **No** | Net total amount payable for this line item. | `@NotNull`, `@DecimalMin("0.00")`, calculated as $\text{quantity} \times \text{unitSellingPrice}$, re-computed on server. |

---

## 3. Detailed Field Definitions & Rules

### 1. Product Reference (`productId` / `product_id`)
* **Java Type:** `java.lang.Long`
* **PostgreSQL Type:** `BIGINT`
* **Nullability:** `NOT NULL` (`CONSTRAINT fk_sale_items_product FOREIGN KEY (product_id) REFERENCES products(id)`)
* **Purpose:** Uniquely links the sale line item to the master spare part record (e.g., Bajaj Pulsar 150 Front Disc Pad, Discover 125 Clutch Cable).
* **Validation Rules:**
  * `@NotNull(message = "Product ID is required")`
  * `@Positive(message = "Product ID must be a positive integer")`
  * The product must exist in the database and have an active status (`is_active = true`). Discontinued or archived parts are rejected at checkout.

---

### 2. Quantity (`quantity`)
* **Java Type:** `java.lang.Integer`
* **PostgreSQL Type:** `INTEGER`
* **Nullability:** `NOT NULL`
* **Purpose:** Specifies the exact physical count of units sold in this line item.
* **Validation Rules:**
  * `@NotNull(message = "Quantity is required")`
  * `@Min(value = 1, message = "Quantity must be at least 1")`
  * Must be a whole positive integer (fractional quantities are not permitted for discrete automotive spare parts).
  * **Stock Availability Constraint:** During checkout, the server verifies:
    $$\text{quantity} \le \text{Available On-Hand Inventory}$$
    If quantity exceeds available stock, the checkout aborts with an `InsufficientStockException`.

---

### 3. Unit Selling Price (`unitSellingPrice` / `unit_selling_price`)
* **Java Type:** `java.math.BigDecimal`
* **PostgreSQL Type:** `NUMERIC(12,2)`
* **Nullability:** `NOT NULL`
* **Purpose:** Represents the unit price charged to the customer for this spare part on this specific invoice.
* **Validation & Security Rules:**
  * `@NotNull(message = "Unit selling price is required")`
  * `@DecimalMin(value = "0.00", inclusive = true, message = "Unit selling price cannot be negative")`
  * `@Digits(integer = 10, fraction = 2, message = "Unit selling price exceeds precision limits")`
  * **Zero-Trust Rule:** The server ignores client-submitted prices and loads the official active selling price from `products.selling_price`. If counter staff applies an authorized line-item discount, the server validates that the discounted rate does not violate minimum margin rules.

---

### 4. Unit Cost Snapshot (`unitCostSnapshot` / `unit_cost_snapshot`)
* **Java Type:** `java.math.BigDecimal`
* **PostgreSQL Type:** `NUMERIC(12,2)`
* **Nullability:** `NOT NULL`
* **Purpose:** Freezes the historical acquisition cost (procurement or weighted average purchase cost) of the unit at the exact second the sale is committed.
* **Validation & Security Rules:**
  * `@NotNull(message = "Unit cost snapshot is required")`
  * `@DecimalMin(value = "0.00", inclusive = true, message = "Unit cost snapshot cannot be negative")`
  * `@Digits(integer = 10, fraction = 2, message = "Unit cost snapshot exceeds precision limits")`
  * **Server-Only Population:** The client cannot pass or manipulate this value in the API payload. It is retrieved directly by Spring Boot from the inventory ledger during the checkout transaction.

---

### 5. Line Total (`lineTotal` / `line_total`)
* **Java Type:** `java.math.BigDecimal`
* **PostgreSQL Type:** `NUMERIC(12,2)`
* **Nullability:** `NOT NULL`
* **Purpose:** Represents the net monetary charge for this specific line item.
* **Calculation & Precision Rules:**
  * **Formula:**
    $$\text{lineTotal} = \text{quantity} \times \text{unitSellingPrice}$$
    *(or $(\text{quantity} \times \text{unitSellingPrice}) - \text{lineDiscount}$ if a line discount is applicable)*
  * **Precision Standard:** Calculated using Java `BigDecimal` with scale 2 and `RoundingMode.HALF_UP`.
  * **Invariant Rule:** Must never be negative ($\text{lineTotal} \ge 0.00$). The server authoritatively recalculates this value before persisting.

---

## 4. Why Original Selling Price and Cost Snapshot Must Be Stored

In an inventory and point-of-sale system, **storing historical price and cost snapshots directly on each sale line item is a mandatory architectural requirement**.

```
                                  TIME PASSES
 [Transaction committed] ─────────────────────────────► [Months / Years Later]
  • Part: Pulsar Clutch Plate                           • Catalog Selling Price raised to ₹320.00
  • Catalog Selling Price: ₹250.00                      • Supplier Purchase Cost rose to ₹195.00
  • Inventory Cost Basis:  ₹160.00
               │
               ▼
   sale_items record stores:
   unit_selling_price = ₹250.00   ◄─── IMMUTABLE INVOICE VALUE (Never distorted by catalog price increases)
   unit_cost_snapshot = ₹160.00   ◄─── IMMUTABLE COGS BASIS (Accurate historical profit reporting for Member 7)
```

### 1. Why Store `unit_selling_price` (Original Selling Price)?

1. **Catalog Prices Change Over Time:**
   * Spare parts prices are revised regularly due to manufacturer MSRP adjustments (Bajaj Auto Ltd.), distributor rate revisions, inflation, or seasonal promotions.
   * If `sale_items` only stored `product_id` and joined dynamically to `products.selling_price`, updating a spare part's price from ₹250.00 to ₹320.00 would **retroactively alter every historical invoice in the system**, corrupting customer receipts and financial ledgers.
2. **Customer Invoice Immutability:**
   * Tax regulations and commercial law require issued customer invoices to be permanently immutable. A reprinted invoice from 6 months ago must display the exact price charged on that date.
3. **Accurate Returns & Refunds:**
   * If a customer returns an uninstalled spare part with their receipt 14 days later, the return workflow (Member 6 / Returns module) must refund the **actual price paid**, not the current catalog price (which may have increased or decreased).

### 2. Why Store `unit_cost_snapshot` (Original Cost Basis)?

1. **Procurement Costs Fluctuate Continuously:**
   * Spare parts are purchased across multiple supplier batches. Bulk purchases, shipping rate variations, and raw material price spikes mean the acquisition cost for the same part number varies from batch to batch.
   * If the system dynamically referenced `products.cost_price`, receiving a new shipment at a higher cost would **retroactively shrink the recorded profit of sales made months earlier**.
2. **Deterministic Reporting for Member 7 (Reporting & Analytics Module):**
   * Member 7's profitability and financial performance reports require immutable historical data:
     $$\text{Line Cost of Goods Sold (COGS)} = \text{quantity} \times \text{unitCostSnapshot}$$
     $$\text{Line Gross Profit} = \text{lineTotal} - \text{Line COGS}$$
     $$\text{Gross Margin \%} = \left(\frac{\text{Line Gross Profit}}{\text{lineTotal}}\right) \times 100$$
   * Because `unit_cost_snapshot` is frozen at checkout time, Member 7's daily, monthly, and yearly profitability reports remain 100% stable, accurate, and reproducible.
3. **Audit & Statutory Compliance:**
   * Financial auditors and tax authorities (e.g., GST / VAT auditing) mandate that COGS and reported margins are backed by an immutable paper trail tied directly to inventory batch records at the time of sale.

---

## 5. Supplementary Audit Fields

| Field Name | Column Name | Java Type | PostgreSQL Type | Nullability | Purpose |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **`createdAt`** | `created_at` | `Instant` | `TIMESTAMPTZ` | **No** | Server timestamp recording line-item creation (`DEFAULT CURRENT_TIMESTAMP`). |

---

## 6. Document Cross-References

* **Sale Header Field Definitions:** [docs/SALE_HEADER_FIELDS.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALE_HEADER_FIELDS.md)
* **Sales Workflow Specification:** [docs/SALES_WORKFLOW.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALES_WORKFLOW.md)
* **Module Gap Analysis:** [docs/SALES_MODULE_ANALYSIS.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALES_MODULE_ANALYSIS.md)

