# Bajaj Spare Parts Inventory & Sales Management System
## Sales Management & Point of Sale (POS) Workflow Specification

**Module:** Sales Management & POS (Member 5)  
**System:** Bajaj Spare Parts Inventory & Sales Management System  
**Technology Stack:** React + Vite, Java 21 + Spring Boot, PostgreSQL + Flyway  
**Package Root:** `com.bajaj.inventory_backend`  
**API Base URL:** `/api/v1/sales`  
**Data Conventions:** JSON: `camelCase` | Database: `snake_case` | Monetary Precision: Java `BigDecimal`, PostgreSQL `NUMERIC(12,2)`  
**File Location:** `docs/SALES_WORKFLOW.md`

---

## 1. Overview & Operational Context

The Point of Sale (POS) counter at a Bajaj spare parts outlet handles high-speed retail transactions, workshop part dispatches, and institutional fleet orders. 

The sales engine supports two core customer profiles:
1. **Walk-in Customers (Retail Counter Sales):** Individual motorcycle owners and local mechanics purchasing fast-moving maintenance items (e.g., spark plugs, engine oil, brake pads, cables). Rapid checkout is essential, and customer profile creation is strictly optional (`customer_id = NULL`).
2. **Registered Workshop & Fleet Accounts:** Pre-registered mechanics, Bajaj-authorized service partners, and commercial accounts who may have credit terms, purchase history tracking, or negotiated discount tiers.

This document details the complete 9-phase operational workflow, detailing client-side presentation mechanisms, server-side transactional processing, stock coordination with `InventoryService`, receipt emission, and mandatory zero-trust validation rules.

---

## 2. High-Level Architecture & Tier Allocation

```
       CLIENT-SIDE (React + Vite)                         SERVER-SIDE (Spring Boot + PostgreSQL)
  ┌─────────────────────────────────┐                 ┌────────────────────────────────────────┐
  │ 1. Search & Select Products     │───(GET search)─►│ Query Product Catalog & Active Stock   │
  │ 2. Cart Operations (Add/Mod/Del)│                 └────────────────────────────────────────┘
  │ 3. Client Subtotal & Discounts  │
  │ 4. Optimistic Stock Check       │
  │ 5. Submit Checkout Payload      │───(POST sale)──►│ 4b. Authoritative DB Stock Check       │
  │                                 │                 │ 6.  Save Sale Header & Items           │
  │                                 │                 │ 7.  Deduct Stock via InventoryService  │
  │ 8. Render & Print Receipt       │◄──(201 Created)─┤     (Atomic DB Transaction Commit)     │
  │ 9. View Sales History & Audits  │───(GET history)►│ Paginated Sales Retrieval              │
  └─────────────────────────────────┘                 └────────────────────────────────────────┘
```

---

## 3. Step-by-Step Sales Workflow

### Step 1: Search and Select Products
* **Execution Tier:** **Client-Side Trigger $\rightarrow$ Server-Side Execution**
* **Workflow:**
  1. Cashier scans a physical barcode with a handheld scanner or enters search keywords (Bajaj OEM part number like `DH101048`, part description, or bike model like "Pulsar 150").
  2. The React frontend applies a 300 ms debounce to keyboard input and issues a `GET /api/v1/products/search?q={query}` request to the backend.
  3. The backend executes an indexed query across `part_number`, `barcode`, and `name` in PostgreSQL, filtering strictly for active products (`is_active = true`).
  4. The search endpoint returns matching items with `productId`, `partNumber`, `description`, official `sellingPrice`, and current `availableStock`.
  5. The cashier selects the product from the dropdown (or an exact barcode scan automatically selects and stages the item).

---

### Step 2: Add, Update, and Remove Cart Items
* **Execution Tier:** **Client-Side (React State Management)**
* **Workflow:**
  1. **Add Item:**
     * If the selected spare part is not present in the active cart, a new line item is created with default `quantity = 1`.
     * If the item already exists in the cart, its quantity is incremented by 1.
  2. **Update Item:**
     * The cashier can directly adjust quantity using increment/decrement controls or direct numeric input (strictly $\ge 1$).
     * The cashier can apply authorized item-level discounts (flat amount or percentage) if permitted by shop policy.
  3. **Remove Item:**
     * The cashier can delete individual line items or click "Clear Cart" to reset the checkout session without affecting database records.

---

### Step 3: Calculate Subtotal, Discounts, and Totals
* **Execution Tier:** **Client-Side (Real-Time Display) & Server-Side (Authoritative Enforcement)**
* **Workflow:**
  1. **Client-Side Calculation:**
     * The React component computes immediate cashier feedback as cart items change:
       $$\text{lineTotal} = (\text{quantity} \times \text{unitSellingPrice}) - \text{itemDiscount}$$
       $$\text{subtotal} = \sum \text{lineTotal}$$
       $$\text{total} = \text{subtotal} - \text{headerDiscount}$$
  2. **Server-Side Verification:**
     * The backend recomputes all line totals, subtotals, discounts, and final totals independently using `java.math.BigDecimal` (`RoundingMode.HALF_UP`). Client-calculated arithmetic is never stored directly.

---

### Step 4: Validate Available Stock
* **Execution Tier:** **Client-Side (Optimistic Guard) & Server-Side (Authoritative Lock)**
* **Workflow:**
  1. **Client-Side Pre-Validation:**
     * As items are added or quantities incremented, React checks $\text{cartQuantity} \le \text{cachedAvailableStock}$. If quantity exceeds cached stock, the UI displays an instant warning banner to avoid wasted counter time.
  2. **Server-Side Authoritative Validation:**
     * Client stock data is inherently stale in multi-terminal setups.
     * The server validates actual available stock within an active database transaction (`@Transactional`) before committing the sale.
     * If any part lacks sufficient on-hand quantity at the moment of checkout, the checkout is rejected with an `InsufficientStockException`.

---

### Step 5: Submit Checkout
* **Execution Tier:** **Client-to-Server (HTTP Request)**
* **Workflow:**
  1. **Customer Selection:**
     * **Walk-in Sales:** Default state. Cashier leaves customer blank (`customerId = null`).
     * **Registered Customer:** Cashier searches by phone number, customer name, or vehicle registration and selects the registered account.
  2. **Payment Tender Selection:**
     * Cashier selects payment method: `CASH`, `CARD`, `UPI`, `BANK_TRANSFER`, or `CREDIT`.
     * For `CASH`: Cashier enters `amountTendered`; UI displays `changeDue = amountTendered - total`.
     * For `CARD` / `UPI`: Cashier enters terminal approval code or 12-digit UPI UTR reference.
     * For `CREDIT`: System requires a registered customer and checks available credit limit.
  3. **Payload Submission:**
     * React dispatches an authenticated `POST /api/v1/sales` request:
       ```json
       {
         "customerId": null,
         "paymentMethod": "CASH",
         "amountTendered": 500.00,
         "discount": 0.00,
         "items": [
           {
             "productId": 101,
             "quantity": 2,
             "unitSellingPrice": 220.00
           }
         ]
       }
       ```

---

### Step 6: Save Sale Header and Sale Items
* **Execution Tier:** **Server-Side (`@Transactional` Boundary)**
* **Workflow:**
  1. Inside an atomic database transaction, Spring Boot validates the incoming DTO and resolves the authenticated cashier (`users.id`) from `SecurityContextHolder`.
  2. Generates an immutable, sequential invoice number (`sale_number`, e.g., `INV-202611-0001`).
  3. Re-queries official selling prices from the database for each `productId` and verifies line math.
  4. Retrieves the current acquisition cost from inventory and sets `unitCostSnapshot` on each line item (freezing historical COGS for Member 7 reporting).
  5. Instantiates the `Sale` entity and cascaded `SaleItem` entities, setting status to `COMPLETED` (or `PENDING` if awaiting bank transfer verification).
  6. Persists records to PostgreSQL in the `sales` and `sale_items` tables.

---

### Step 7: Deduct Stock Through `InventoryService`
* **Execution Tier:** **Server-Side (Centralized Inventory Integration)**
* **Workflow:**
  1. **Same-Transaction Coordination:**
     * Inside the exact same database transaction as Step 6, the `SalesService` invokes Member 6's centralized inventory engine:
       ```java
       inventoryService.deductStock(productId, quantity, StockMovementType.SALE, sale.getId());
       ```
  2. **Row Locking & Atomic Decrement:**
     * `InventoryService` executes a row-level locked decrement in PostgreSQL:
       $$\text{UPDATE inventory SET quantity = quantity} - :qty \text{ WHERE product\_id =} :id \text{ AND quantity} \ge :qty$$
  3. **Rollback Guarantee:**
     * If any single product in the cart fails the stock condition, `InventoryService` throws a runtime `InsufficientStockException`.
     * Spring Boot rolls back the entire database transaction automatically. Neither the `sales` header nor the `sale_items` rows are persisted, guaranteeing that inventory and sales records never fall out of sync.
  4. **Stock Movement Ledger:**
     * Upon success, `InventoryService` writes an audit log entry recording stock deduction linked to `reference_type = 'SALE'` and `reference_id = sale.id`.

---

### Step 8: Generate Receipts
* **Execution Tier:** **Server-Side Response $\rightarrow$ Client-Side Rendering & Print**
* **Workflow:**
  1. The server completes transaction commit and returns `201 Created` with the full `SaleResponse` (including invoice number, timestamp, cashier name, customer label, item breakdown, subtotal, discount, total, and payment tender details).
  2. The React frontend clears the POS cart session.
  3. A printable receipt modal opens automatically, formatted for standard 80 mm thermal POS receipt printers.
  4. For walk-in sales, the customer section explicitly reads **"Walk-in Customer"**.
  5. The cashier can trigger a thermal receipt print via `window.print()` or issue a digital invoice copy.

---

### Step 9: View Sales History & Audit Trails
* **Execution Tier:** **Client-Side Interface $\rightarrow$ Server-Side Paginated Query**
* **Workflow:**
  1. Staff navigates to the "Sales History" screen in the React application.
  2. The page provides filtering by date range, sale number, status (`COMPLETED`, `CANCELLED`, `REFUNDED`), payment method, and cashier.
  3. React requests `GET /api/v1/sales?page=0&size=20&sort=saleDate,desc`.
  4. Spring Boot queries PostgreSQL using indexed pagination and returns past transaction records.
  5. Cashiers can view complete line-item breakdowns, inspect payment audit logs, and trigger receipt re-printing.
  6. Store Managers can initiate authorized post-sale returns or same-day invoice voiding.

---

## 4. Walk-in Customer Handling Strategy

Walk-in retail sales constitute a major portion of counter volume in a spare parts dealership. The POS architecture explicitly optimizes for walk-in transactions:

1. **Schema Design:** `customer_id` in the `sales` table is nullable (`BIGINT NULL`).
2. **Frictionless Checkout:** Cashiers are not forced to ask for phone numbers, create placeholder profiles, or enter dummy registration details.
3. **Receipt Representation:** When `customer_id` is `null`, the receipt generator renders customer information as `"Walk-in Customer"`.
4. **Payment Method Constraints:**
   * Walk-in customers can settle using `CASH`, `CARD`, or `UPI`.
   * **`CREDIT` (Store Account) is strictly blocked** when `customer_id` is `null`. Credit checkout requires a verified, registered customer profile with an active credit limit.

---

## 5. Mandatory Server-Side Authority & Re-Validation (Zero-Trust Policy)

Under zero-trust client architecture, the React frontend is treated as an untrusted client. The Spring Boot backend must authoritatively re-validate all transaction parameters during checkout:

| Validation Category | Client-Side Responsibility (React) | Server-Side Authority (Spring Boot + DB) |
| :--- | :--- | :--- |
| **Product Selling Prices** | Displays catalog price for cashier reference. | **Strictly Re-Queried:** Ignores incoming payload prices; fetches official selling price from database to prevent payload tampering. |
| **Stock Availability** | Early UI warning based on cached stock. | **Locked & Validated:** Checked under row locks inside the `@Transactional` boundary; rolls back if quantity is insufficient. |
| **Arithmetic & Totals** | Real-time preview for counter speed. | **Recalculated from Scratch:** Recomputes line totals, subtotals, and totals using Java `BigDecimal` (`RoundingMode.HALF_UP`) matching `NUMERIC(12,2)`. |
| **Customer Credit Limit** | Disables credit option for walk-in sales. | **Enforced:** Queries customer ledger; blocks checkout if $\text{balance} + \text{total} > \text{creditLimit}$ unless Manager override is provided. |
| **User Identity & Roles** | Hides managerial buttons in UI. | **Extracted from JWT:** Resolves `created_by` from `SecurityContextHolder`; enforces role checks for discounts (> 5%) and invoice voiding. |

---

## 6. Document References

* **Workflow Analysis & Detailed Field Definitions:** [docs/sales/sales-workflow.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/sales/sales-workflow.md)
* **Module Architecture & Gap Analysis:** [docs/SALES_MODULE_ANALYSIS.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALES_MODULE_ANALYSIS.md)

