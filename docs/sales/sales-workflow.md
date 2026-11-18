# Sales Workflow Analysis & Field Definitions
**Module:** Sales Management / Point of Sale (POS) — Member 5  
**System:** Bajaj Spare Parts Inventory & Sales Management System  
**Technology Stack:** React + Vite, Java 21 + Spring Boot, PostgreSQL + Flyway  
**Package Structure:** `com.bajaj.inventory_backend` (Project root: `com.bajaj.inventory_backend.sales`, `com.bajaj.inventory_backend.entity`)  
**API Base URL:** `/api/v1/sales`  
**Data Conventions:** JSON: `camelCase` | Database: `snake_case` | Monetary Precision: Java `BigDecimal`, PostgreSQL `NUMERIC(12,2)`

---

## 1. Executive Summary & Domain Scope

The Sales Management / Point of Sale (POS) module handles counter-level spare parts retailing, workshop billing, and invoice generation for the Bajaj Spare Parts Inventory & Sales Management System. 

The module is engineered to handle two distinct customer flows:
1. **Walk-in Customers (Retail Counter Sales):** Individual motorcycle riders and independent mechanics purchasing fast-moving maintenance items (e.g., spark plugs, engine oils, brake shoes, control cables). Speed of checkout is paramount; customer registration is strictly optional (`customer_id = NULL`).
2. **Registered Customers (Workshops & Commercial Accounts):** Pre-registered authorized Bajaj service centres, independent garages, and institutional fleet buyers who hold registered profiles, trade discounts, or credit accounts.

The POS system manages the transaction lifecycle from item lookup, real-time stock validation, cart building, and pricing calculations, through to payment capture, atomic stock decrement, receipt printing, and historical audit retrieval.

---

## 2. Sale Header Field Definitions (`sales` Table)

The sale header represents the master record for each retail or workshop invoice.

### Header Specification Table

| Field Name (Java / JSON) | Column Name (SQL) | Java Type | SQL Type | Nullable | One-Line Purpose |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **id** | `id` | `Long` | `BIGINT` | **No** (PK) | Unique synthetic primary key identifying the sale record internally. |
| **saleNumber** | `sale_number` | `String` | `VARCHAR(32)` | **No** (Unique) | Unique human-readable invoice or receipt reference number (e.g., `INV-20261113-0001`). |
| **customerId** | `customer_id` | `Long` | `BIGINT` | **Yes** | Foreign key referencing registered customer (`customers.id`); `NULL` indicates an anonymous walk-in retail buyer. |
| **saleDate** | `sale_date` | `Instant` | `TIMESTAMP WITH TIME ZONE` | **No** | Exact timestamp marking when the sale transaction was finalized at the counter. |
| **status** | `status` | `SaleStatus` (Enum) | `VARCHAR(20)` | **No** | Current operational status of the sale (`DRAFT`, `COMPLETED`, `CANCELLED`, `REFUNDED`, `PARTIALLY_REFUNDED`). |
| **subtotal** | `subtotal` | `BigDecimal` | `NUMERIC(12,2)` | **No** | Sum of all line item totals before deducting header-level discounts or adjustments. |
| **discount** | `discount` | `BigDecimal` | `NUMERIC(12,2)` | **No** | Total monetary discount amount deducted from the subtotal at the sale header level (defaults to `0.00`). |
| **total** | `total` | `BigDecimal` | `NUMERIC(12,2)` | **No** | Net payable amount settled by the customer (`subtotal - discount`). |
| **paymentMethod** | `payment_method` | `PaymentMethod` (Enum) | `VARCHAR(30)` | **No** | Tender instrument used for settlement (`CASH`, `CARD`, `UPI`, `BANK_TRANSFER`, `CREDIT`). |
| **createdBy** | `created_by` | `Long` | `BIGINT` | **No** | Foreign key referencing the authenticated user (`users.id`) who executed the transaction at the POS counter. |

---

## 3. Sale Item Field Definitions (`sale_items` Table)

The `sale_items` table records each individual spare part line item associated with a parent invoice.

### Item Specification Table

| Field Name (Java / JSON) | Column Name (SQL) | Java Type | SQL Type | Nullable | One-Line Purpose |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **id** | `id` | `Long` | `BIGINT` | **No** (PK) | Unique synthetic primary key identifying the individual line item record. |
| **saleId** | `sale_id` | `Long` | `BIGINT` | **No** (FK) | Foreign key linking the line item directly to its parent sale header (`sales.id`). |
| **productId** | `product_id` | `Long` | `BIGINT` | **No** (FK) | Foreign key referencing the catalog spare part (`products.id`) sold in this transaction. |
| **quantity** | `quantity` | `Integer` | `INTEGER` | **No** | Number of units of the spare part purchased in this line item (must be $\ge 1$). |
| **unitSellingPrice** | `unit_selling_price` | `BigDecimal` | `NUMERIC(12,2)` | **No** | Unit price per part charged to the customer at the time of sale. |
| **unitCostSnapshot** | `unit_cost_snapshot` | `BigDecimal` | `NUMERIC(12,2)` | **No** | Acquisition cost per unit captured from inventory at the exact moment the sale was finalized. |
| **lineTotal** | `line_total` | `BigDecimal` | `NUMERIC(12,2)` | **No** | Net total amount for this line item, calculated as `quantity * unitSellingPrice`. |

---

## 4. Purpose of Storing `unit_cost_snapshot` (Reporting by Member 7)

Persisting the historical unit cost snapshot on each sale item is a vital architectural requirement for financial analytics and accounting owned by **Member 7 (Reporting & Analytics Module)**:

1. **COGS (Cost of Goods Sold) Immutability:**
   - In the automotive spare parts industry, wholesale acquisition prices from manufacturers (Bajaj Auto Ltd.) and distributors fluctuate over time due to raw material price revisions, freight adjustments, and batch quantity tiers.
   - If `sale_items` did not snapshot the acquisition cost and instead joined dynamically back to `products.cost_price`, any future stock replenishment at a new purchase price would **retroactively corrupt past profit and loss figures**.
   - `unit_cost_snapshot` freezes the historical cost at transaction time, guaranteeing strictly immutable COGS.

2. **Accurate Gross Margin & Profitability Metrics:**
   - Member 7's analytics engines compute profitability using:
     $$\text{Line Gross Profit} = \text{lineTotal} - (\text{quantity} \times \text{unitCostSnapshot})$$
     $$\text{Gross Margin \%} = \left(\frac{\text{Line Gross Profit}}{\text{lineTotal}}\right) \times 100$$
   - This enables exact daily, monthly, and SKU-level margin reports across fast-moving consumables (e.g., Bajaj 4T DTS-i engine oil) versus slow-moving body/engine components.

3. **Audit & Statutory Tax Compliance:**
   - GAAP/IFRS standards and tax audits require past transaction records and gross margins to remain fixed, reproducible, and verifiable.

---

## 5. Allowed Sale Statuses and State Transitions

### 5.1 Status Definitions

| Status | Category | Definition & System State |
| :--- | :--- | :--- |
| **`DRAFT`** | In-Progress | Active counter session; items are staged in the cart. No payment received; no inventory deducted. |
| **`COMPLETED`** | Final (Success) | Payment received and verified. Stock atomically decremented from inventory. Invoice number issued. |
| **`CANCELLED`** | Final (Aborted / Voided) | The sale was discarded prior to payment, or voided post-completion by a manager (which reverses inventory). |
| **`REFUNDED`** | Final (Full Return) | All parts from the invoice were returned via the Returns workflow. Customer reimbursed; items restocked. |
| **`PARTIALLY_REFUNDED`** | Historical (Partial Return) | A subset of parts was returned. Returned units restocked; prorated refund issued. |

### 5.2 State Transition Matrix & Role Permissions

```
                ┌──────────────┐
                │    DRAFT     │
                └──────┬───────┘
                       │
         ┌─────────────┴─────────────┐
         │                           │
  [Checkout & Pay]            [Discard Cart]
  Cashier / Manager          Cashier / Manager
         │                           │
         ▼                           ▼
  ┌──────────────┐            ┌──────────────┐
  │  COMPLETED   │            │  CANCELLED   │
  └──────┬───────┘            └──────────────┘
         │                           ▲
         ├───────────────────────────┤ [Post-Sale Invoice Void]
         │                             Store Manager / Admin ONLY
         │                             (Reverses Stock & Ledger)
         │
   [Process Return]
   Manager / Cashier
         │
         ├───────────────────────────┐
         ▼                           ▼
  ┌──────────────┐            ┌──────────────────────┐
  │   REFUNDED   │            │  PARTIALLY_REFUNDED  │
  └──────────────┘            └──────────┬───────────┘
                                         │ [Return Balance of Items]
                                         ▼
```

| Source Status | Target Status | Trigger Action | Permitted Roles | Pre-conditions & Side Effects |
| :--- | :--- | :--- | :--- | :--- |
| *(None)* | **`DRAFT`** | Cashier initiates counter POS session. | Cashier, Manager, Admin | Staged in-memory / temporary cart; no database locks. |
| **`DRAFT`** | **`COMPLETED`** | Cashier collects payment and finalizes sale. | Cashier, Manager, Admin | **Validation:** Stock available; payment received $\ge$ total.<br>**Side Effects:** Stock atomically decremented; invoice generated; financial credit recorded. |
| **`DRAFT`** | **`CANCELLED`** | Cashier clears or abandons unpaid cart. | Cashier, Manager, Admin | Session cleared; zero inventory or financial impact. |
| **`COMPLETED`** | **`CANCELLED`** | Same-day invoice void due to billing error or billing dispute. | **Store Manager**, **Admin** *(Cashier prohibited)* | **Strict Control:** Requires manager PIN/login and audit remark.<br>**Side Effects:** Stock restored to inventory; financial entries reversed. |
| **`COMPLETED`** | **`REFUNDED`** | All parts returned with receipt via Returns module. | Store Manager, Cashier *(under return policy)* | Full payment refunded; all items restocked in inventory. |
| **`COMPLETED`** | **`PARTIALLY_REFUNDED`** | Selected items from invoice returned. | Store Manager, Cashier *(under return policy)* | Prorated refund issued; returned units restocked. |
| **`PARTIALLY_REFUNDED`** | **`REFUNDED`** | Balance of remaining items returned. | Store Manager, Cashier | Final refund balance issued; closes invoice return lifecycle. |
| **`CANCELLED`** | *(None)* | *Terminal state* | N/A | Immutable; no further transitions allowed. |
| **`REFUNDED`** | *(None)* | *Terminal state* | N/A | Immutable; no further transitions allowed. |

---

## 6. Allowed Payment Methods

The system supports five payment methods (`PaymentMethod` enum / `VARCHAR(30)`):

| Payment Method | Customer Eligibility | Settlement Mechanism | Operational Validation Rules |
| :--- | :--- | :--- | :--- |
| **`CASH`** | Walk-in & Registered | Immediate Physical Cash | Cashier inputs `amountTendered` ($\ge \text{total}$). System computes $\text{changeDue} = \text{amountTendered} - \text{total}$. Increments physical cash drawer balance. |
| **`CARD`** | Walk-in & Registered | Electronic POS Card Terminal | Customer taps/inserts card on bank terminal. Cashier captures bank authorization reference / approval code. |
| **`UPI`** | Walk-in & Registered | Instant QR Code Bank Credit | Customer scans shop QR code via UPI app. Cashier confirms credit and records the 12-digit UPI transaction reference (UTR). |
| **`BANK_TRANSFER`** | **Registered Only** | Direct NEFT / RTGS / IMPS | Designed for bulk wholesale or fleet buyers. Requires bank clearance verification and transaction reference before stock release. |
| **`CREDIT`** | **Registered Only** *(Blocked for Walk-in)* | Deferred Store Account (A/R) | Debits customer's Accounts Receivable ledger. **Validation:** Checks $\text{Current Balance} + \text{Sale Total} \le \text{Credit Limit}$. Over-limit checkout requires Manager PIN override. |

---

## 7. Step-by-Step Sales & POS Workflow

Below is the end-to-end workflow covering client-side interactions and server-side processing across ten distinct steps:

```
       CLIENT-SIDE (React + Vite)                         SERVER-SIDE (Spring Boot + PostgreSQL)
  ┌─────────────────────────────────┐                 ┌────────────────────────────────────────┐
  │ 1. Search Products (Debounced)  │───(GET search)─►│ Search Catalog & Return Active Stock   │
  │ 2. Select Product               │                 └────────────────────────────────────────┘
  │ 3. Add / Update / Remove Items  │
  │ 4. Client-Side Totals Calc      │
  │ 5. Optimistic Stock Check       │
  │ 6. Submit Checkout Payload      │───(POST sale)──►│ 5b. Authoritative DB Stock Validation  │
  │                                 │                 │ 7.  Save Sale Header & Items           │
  │                                 │                 │ 8.  Centralized Stock Deduction Tx     │
  │ 9. Render & Print Receipt       │◄──(201 Created)─┤     Commit Transaction                 │
  │ 10. View in History / Audit     │───(GET history)►│ Paginated Sales Retrieval              │
  └─────────────────────────────────┘                 └────────────────────────────────────────┘
```

### Detailed Execution Steps

| Step | Operation | Execution Tier | Mechanism & Technical Detail |
| :---: | :--- | :---: | :--- |
| **1** | **Search Products** | **Client & Server** | **Client:** Cashier types query (part number, description, bike model) or scans barcode; debounces keystrokes (300ms) $\rightarrow$ `GET /api/v1/products/search?q={query}`.<br>**Server:** Queries PostgreSQL indexed columns (`part_number`, `barcode`, `name`); returns matching active items with prices and stock. |
| **2** | **Select Products** | **Client-Side** | Cashier clicks an item from search results (or auto-selected on barcode match). Product metadata is staged for cart addition. |
| **3** | **Add / Update / Remove Cart Items** | **Client-Side** | React state updates: adds new row or increments quantity; updates quantity/item discount; or deletes row from local cart state. |
| **4** | **Calculate Totals** | **Client & Server** | **Client:** Computes real-time UI preview: $\text{lineTotal} = \text{qty} \times \text{unitPrice}$; $\text{subtotal} = \sum \text{lineTotal}$; $\text{total} = \text{subtotal} - \text{discount}$.<br>**Server:** Authoritatively recalculates all figures from catalog DB prices using `BigDecimal` during checkout. |
| **5** | **Validate Stock** | **Client & Server** | **Client:** Optimistic validation checks $\text{cartQty} \le \text{cachedAvailableQty}$ and warns cashier.<br>**Server:** Authoritative validation under database row locks during transaction commit. |
| **6** | **Submit Checkout** | **Client $\rightarrow$ Server** | Cashier confirms tender and clicks "Finalize Sale". React dispatches `POST /api/v1/sales` payload (`customerId`, `paymentMethod`, `discount`, array of `items`). |
| **7** | **Save Sale and Items** | **Server-Side** | Inside Spring `@Transactional` boundary: generates sequential `sale_number`, retrieves current unit cost snapshot, persists `sales` header and `sale_items` rows in PostgreSQL. |
| **8** | **Call Centralized Stock Deduction** | **Server-Side** | Inside the **same database transaction**: calls Member 6's inventory service (`deductStock(...)`). Executes atomic decrement with row lock (`WHERE quantity >= :qty`). If stock is insufficient, the transaction automatically rolls back (`400 InsufficientStockException`). |
| **9** | **Issue Receipt** | **Server & Client** | **Server:** Returns `201 Created` with full sale response payload.<br>**Client:** React clears cart and displays printable receipt modal for thermal POS printer output (`window.print()`). |
| **10** | **View in History** | **Client & Server** | **Client:** Cashier/manager opens History tab with search and date filters $\rightarrow$ `GET /api/v1/sales?page=0&size=20`.<br>**Server:** Queries PostgreSQL with pagination and returns historical sale summaries. |

---

## 8. Critical Design Note: Mandatory Server-Side Authority & Re-Validation

The React frontend operates as an **untrusted presentation client**. Under zero-trust architecture, the Spring Boot backend must authoritatively re-validate all business parameters upon checkout:

1. **Re-Validation of Product Selling Prices:**
   - The server completely discards client-submitted unit prices. Spring Boot looks up the official selling price from PostgreSQL for each `product_id`.
   - Ad-hoc counter discounts must be validated against permitted cashier thresholds or validated via a supervisor authorization token.
2. **Re-Validation of Stock Availability & Race Mitigation:**
   - Stale client snapshots cannot prevent race conditions where two cashiers sell the last unit simultaneously.
   - Stock availability is authoritatively locked and evaluated within the database transaction (`@Transactional`), preventing negative stock balances.
3. **Re-Validation of Subtotals, Discounts, and Totals:**
   - All arithmetic calculations are re-computed on the server using `java.math.BigDecimal` with `RoundingMode.HALF_UP` to match PostgreSQL `NUMERIC(12,2)`. Client arithmetic is never trusted.
4. **Re-Validation of Permissions & Authorization Boundaries:**
   - User identity (`created_by`) and roles are extracted directly from the authenticated Spring Security JWT context (`SecurityContextHolder`).
   - Counter cashiers cannot apply discounts exceeding authorized policy limits, grant credit to unvetted accounts, or void finalized sales.

---

## 9. Assumptions and Open Integration Questions for Team Members

To guarantee harmonious integration across module boundaries, Member 5 has documented the following technical assumptions that require explicit confirmation:

### To Member 1 (Authentication & User Management)
* **Assumption 1.1:** The JWT token contains the user's primary key (`users.id`) as a custom claim (`userId`), allowing the Sales service to populate `created_by` without an extra database lookup.
* **Assumption 1.2:** System roles will be finalized as `ROLE_CASHIER`, `ROLE_MANAGER`, and `ROLE_ADMIN` in `com.bajaj.inventory_backend.entity.RoleName`.
* **Assumption 1.3:** Manager overrides (for large discounts, credit limits, or sale voiding) will be supported either via an HTTP header (e.g., `X-Manager-Override-Token`) or via a dedicated supervisor authorization endpoint.
* **Assumption 1.4:** The Sales module is responsible for recording the transaction audit timestamp and cashier ID (`created_by`), while cashier till shift management (opening vs closing drawer cash balances) is handled by Member 1.

### To Member 2 (Products & Catalog Management)
* **Assumption 2.1:** The `selling_price` column on the `products` table represents the gross customer-facing retail price (tax-inclusive).
* **Assumption 2.2:** Each spare part has a unique, non-null `barcode` indexed in PostgreSQL, enabling instant single-scan barcode lookup at the POS counter.
* **Assumption 2.3:** The `products` table includes an active flag (e.g., `is_active` boolean or `status = 'ACTIVE'`) to exclude discontinued parts from POS search results.
* **Assumption 2.4:** Product search queries can filter by vehicle model compatibility (e.g., Pulsar 150, Discover 125, Platina 100).

### To Member 6 (Inventory & Stock Management)
* **Assumption 6.1:** Member 6 provides a centralized, synchronous stock deduction method participating in the caller's `@Transactional` boundary:
  `void deductStock(Long productId, Integer quantity, StockMovementType type, Long referenceId)`.
* **Assumption 6.2:** If available stock is less than the requested quantity, Member 6's service throws an `InsufficientStockException`, causing the entire checkout transaction to abort and roll back.
* **Assumption 6.3:** The Inventory module exposes an API or method to fetch the current unit cost / weighted average acquisition cost of each spare part so that Member 5 can stamp `sale_items.unit_cost_snapshot` at the moment of sale.
* **Assumption 6.4:** No stock reservation is required while a sale is in `DRAFT` status; deduction occurs exclusively upon successful payment confirmation.

### To Member 10 (Customer Management)
* **Assumption 10.1:** Walk-in sales are modeled with `customer_id = NULL` in the `sales` table, eliminating the overhead of dummy customer accounts.
* **Assumption 10.2:** Member 10 provides a credit balance validation method:
  `boolean validateCreditLimit(Long customerId, BigDecimal saleAmount)` and an account debit method for `CREDIT` sales.
* **Assumption 10.3:** Customer accounts may have pre-configured discount tier percentages (e.g., 5% discount for verified independent mechanics) that the POS should fetch and apply.
* **Assumption 10.4:** Customer lookup supports fast querying by `phone_number` and `vehicle_registration_number`.

