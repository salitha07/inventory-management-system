# Bajaj Spare Parts Inventory & Sales Management System
## Sale Header Field Definitions (`sales`)

**Module:** Sales Management & Point of Sale (POS) — Member 5  
**System:** Bajaj Spare Parts Inventory & Sales Management System  
**Technology Stack:** React + Vite, Java 21 + Spring Boot, PostgreSQL + Flyway  
**Package Root:** `com.bajaj.inventory_backend`  
**API Base URL:** `/api/v1/sales`  
**Conventions:** JSON: `camelCase` | Database: `snake_case` | Monetary Precision: Java `BigDecimal`, PostgreSQL `NUMERIC(12,2)`  
**File Location:** `docs/SALE_HEADER_FIELDS.md`

---

## 1. Overview

The `sales` entity represents the master header record for each sales transaction conducted at the retail counter or service workshop. It captures customer association, transaction status, financial totals, payment tender details, and cashier audit trails.

The design strictly accommodates **Walk-in Customers** as first-class retail participants without requiring customer profile creation, mandatory phone numbers, or dummy database accounts.

---

## 2. Sale Header Field Specifications Table

| Field Name (Java / JSON) | Column Name (SQL) | Java Type | PostgreSQL Type | Nullable | Primary Purpose | Key Validation Rules |
| :--- | :--- | :--- | :--- | :---: | :--- | :--- |
| **`id`** | `id` | `Long` | `BIGINT` | **No** (PK) | Internal synthetic primary key uniquely identifying the sale record. | Auto-generated via database sequence (`BIGSERIAL`). Immutable. |
| **`saleNumber`** | `sale_number` | `String` | `VARCHAR(32)` | **No** (Unique) | Unique human-readable invoice or receipt reference number. | `@NotBlank`, max 32 chars, format `INV-YYYYMM-XXXX`, globally unique, server-generated, immutable. |
| **`customerId`** | `customer_id` | `Long` | `BIGINT` | **Yes** | References registered customer (`customers.id`); `NULL` for walk-in retail sales. | `@Positive` when present; must reference active customer if non-null; `CREDIT` payment blocked when null. |
| **`saleDate`** | `sale_date` | `Instant` | `TIMESTAMPTZ` | **No** | Exact timestamp when the sale was finalized at the POS counter. | `@NotNull`, `@PastOrPresent`, defaults to server `CURRENT_TIMESTAMP`, immutable once finalized. |
| **`status`** | `status` | `SaleStatus` (Enum) | `VARCHAR(20)` | **No** | Current operational lifecycle state of the sale. | `@NotNull`, valid enum: `DRAFT`, `COMPLETED`, `CANCELLED`, `REFUNDED`, `PARTIALLY_REFUNDED`; post-sale void requires Manager role. |
| **`subtotal`** | `subtotal` | `BigDecimal` | `NUMERIC(12,2)` | **No** | Gross sum of all line item totals before header discounts or tax adjustments. | `@NotNull`, `@DecimalMin("0.00")`, max 10 integer digits + 2 decimals, must equal $\sum \text{line\_total}$. |
| **`discount`** | `discount` | `BigDecimal` | `NUMERIC(12,2)` | **No** | Header-level monetary discount deducted from the subtotal. | `@NotNull`, `@DecimalMin("0.00")`, max 10 integer digits + 2 decimals, default `0.00`, must be $\le \text{subtotal}$. |
| **`total`** | `total` | `BigDecimal` | `NUMERIC(12,2)` | **No** | Net payable amount settled by the customer (`subtotal - discount`). | `@NotNull`, `@DecimalMin("0.00")`, invariant $\text{total} = \text{subtotal} - \text{discount}$, re-computed on server. |
| **`paymentMethod`** | `payment_method` | `PaymentMethod` (Enum) | `VARCHAR(30)` | **No** | Monetary tender instrument used for transaction settlement. | `@NotNull`, valid enum: `CASH`, `CARD`, `UPI`, `BANK_TRANSFER`, `CREDIT`; requires specific tender validation. |
| **`createdBy`** | `created_by` | `Long` | `BIGINT` | **No** | Foreign key referencing the authenticated cashier (`users.id`). | `@NotNull`, must reference valid `users.id`, resolved from server JWT context, cannot be set by client. |

---

## 3. Detailed Field Definitions & Validation Rules

### 1. `saleNumber` (`sale_number`)
* **Java Type:** `java.lang.String`
* **PostgreSQL Type:** `VARCHAR(32)`
* **Nullability:** `NOT NULL` (Database `UNIQUE` constraint)
* **Purpose:** Provides an immutable, human-readable invoice identifier displayed on printed thermal receipts, customer invoices, and audit reports (e.g., `INV-202611-0001`).
* **Validation & Business Rules:**
  * `@NotBlank(message = "Sale number is required")`
  * `@Size(max = 32, message = "Sale number cannot exceed 32 characters")`
  * Generated exclusively on the server using an atomic sequence or year-month-prefixed monotonic counter.
  * Rejects client-supplied invoice numbers to prevent duplicate sequence injection.

---

### 2. `customerId` (`customer_id`) — Walk-in vs Registered Customer
* **Java Type:** `java.lang.Long`
* **PostgreSQL Type:** `BIGINT`
* **Nullability:** `NULL` (Explicitly Nullable)
* **Purpose:** Associates the transaction with a registered customer profile (e.g., local workshop, Bajaj authorized dealer, fleet owner). When set to `NULL`, the transaction represents an anonymous walk-in retail customer.
* **Validation & Business Rules:**
  * If `customerId != null`:
    * Must be a positive integer (`@Positive`).
    * Must match an active record in `customers(id)` (`FOREIGN KEY (customer_id) REFERENCES customers(id)`).
  * If `customerId == null` (Walk-in Customer):
    * **No Account Required:** No customer registration, placeholder profile, or dummy record is inserted into the `customers` table.
    * **Printed Receipt:** Customer section automatically renders `"Walk-in Customer"`.
    * **Payment Method Restriction:** Cannot select `PaymentMethod.CREDIT`.

---

### 3. `saleDate` (`sale_date`)
* **Java Type:** `java.time.Instant`
* **PostgreSQL Type:** `TIMESTAMPTZ` (`TIMESTAMP WITH TIME ZONE`)
* **Nullability:** `NOT NULL` (Default `CURRENT_TIMESTAMP`)
* **Purpose:** Records the exact point in time when the sale was finalized and payment was received at the counter.
* **Validation & Business Rules:**
  * `@NotNull(message = "Sale date is required")`
  * `@PastOrPresent(message = "Sale date cannot be in the future")`
  * Assigned by the server during checkout commit; cannot be backdated or manipulated via client payload.
  * Used for end-of-day cash drawer balancing, cashier shift audits, and Member 7 daily revenue reporting.

---

### 4. `status` (`status`)
* **Java Type:** `com.bajaj.inventory_backend.sales.entity.SaleStatus` (Enum)
* **PostgreSQL Type:** `VARCHAR(20)`
* **Nullability:** `NOT NULL`
* **Purpose:** Tracks the current state of the transaction across its lifecycle.
* **Allowed Values:**
  * `DRAFT`: In-progress POS cart; items are staged but payment is not received.
  * `COMPLETED`: Successful checkout; payment received and stock deducted.
  * `CANCELLED`: Discarded before payment, or voided post-completion by a manager.
  * `REFUNDED`: Full return processed; items returned and customer reimbursed.
  * `PARTIALLY_REFUNDED`: Subset of parts returned; prorated refund issued.
* **Validation & Business Rules:**
  * `@NotNull(message = "Sale status is required")`
  * Transitions must strictly follow the allowed state machine.
  * Transitioning from `COMPLETED` to `CANCELLED` (same-day invoice void) requires `ROLE_MANAGER` or `ROLE_ADMIN` credentials.

---

### 5. `subtotal` (`subtotal`)
* **Java Type:** `java.math.BigDecimal`
* **PostgreSQL Type:** `NUMERIC(12,2)`
* **Nullability:** `NOT NULL`
* **Purpose:** Represents the gross aggregate amount of all line items before invoice-level discounts or deductions.
* **Validation & Business Rules:**
  * `@NotNull(message = "Subtotal is required")`
  * `@DecimalMin(value = "0.00", inclusive = true, message = "Subtotal cannot be negative")`
  * `@Digits(integer = 10, fraction = 2, message = "Subtotal exceeds precision limits")`
  * Must strictly equal the sum of all associated `sale_items.line_total` values:
    $$\text{subtotal} = \sum_{i=1}^{n} \text{line\_total}_i$$
  * Calculated authoritatively on the server using `BigDecimal` arithmetic.

---

### 6. `discount` (`discount`)
* **Java Type:** `java.math.BigDecimal`
* **PostgreSQL Type:** `NUMERIC(12,2)`
* **Nullability:** `NOT NULL` (Default `0.00`)
* **Purpose:** Captures any header-level monetary discount deducted from the subtotal (e.g., promotional discount, goodwill discount, or trade discount).
* **Validation & Business Rules:**
  * `@NotNull(message = "Discount is required")`
  * `@DecimalMin(value = "0.00", inclusive = true, message = "Discount cannot be negative")`
  * `@Digits(integer = 10, fraction = 2, message = "Discount exceeds precision limits")`
  * **Boundary Rule:** Must not exceed subtotal ($0.00 \le \text{discount} \le \text{subtotal}$).
  * **Permission Rule:** Discounts exceeding standard cashier authority (e.g., > 5% of subtotal) require supervisor approval or Manager PIN override.

---

### 7. `total` (`total`)
* **Java Type:** `java.math.BigDecimal`
* **PostgreSQL Type:** `NUMERIC(12,2)`
* **Nullability:** `NOT NULL`
* **Purpose:** Represents the final net payable amount settled by the customer.
* **Validation & Business Rules:**
  * `@NotNull(message = "Total is required")`
  * `@DecimalMin(value = "0.00", inclusive = true, message = "Total cannot be negative")`
  * `@Digits(integer = 10, fraction = 2, message = "Total exceeds precision limits")`
  * **Mathematical Invariant:** Must strictly satisfy:
    $$\text{total} = \text{subtotal} - \text{discount}$$
  * Server enforces this formula authoritatively, rejecting any client payload with mismatched numbers.

---

### 8. `paymentMethod` (`payment_method`)
* **Java Type:** `com.bajaj.inventory_backend.sales.entity.PaymentMethod` (Enum)
* **PostgreSQL Type:** `VARCHAR(30)`
* **Nullability:** `NOT NULL`
* **Purpose:** Specifies the monetary instrument or tender used by the customer to settle the invoice.
* **Allowed Values:**
  * `CASH`: Physical cash tender.
  * `CARD`: Credit or debit card POS terminal payment.
  * `UPI`: Unified Payments Interface / QR code scan.
  * `BANK_TRANSFER`: Direct NEFT / RTGS / IMPS wire transfer.
  * `CREDIT`: Store account / Accounts Receivable (A/R).
* **Validation & Business Rules:**
  * `@NotNull(message = "Payment method is required")`
  * If `CASH`: Requires `amountTendered` $\ge \text{total}$; server calculates `changeDue`.
  * If `CARD`: Requires POS card authorization / terminal reference number.
  * If `UPI`: Requires 12-digit UPI transaction reference (UTR).
  * If `CREDIT`: `customerId` **must not be null**; customer's current balance + `total` must not exceed their sanctioned credit limit.

---

### 9. `createdBy` (`created_by`)
* **Java Type:** `java.lang.Long`
* **PostgreSQL Type:** `BIGINT`
* **Nullability:** `NOT NULL`
* **Purpose:** Foreign key linking the sale to the authenticated counter cashier (`users.id`) who processed the checkout.
* **Validation & Business Rules:**
  * `@NotNull(message = "Created-by user is required")`
  * Must reference an active user record in `users(id)` (`FOREIGN KEY (created_by) REFERENCES users(id)`).
  * Populated exclusively on the server from `SecurityContextHolder` / JWT claims; any value submitted by the client is overwritten.
  * Immutable after creation to maintain an auditable financial paper trail.

---

## 4. Supplementary Audit Fields

To align with the project's standard auditing patterns (as seen in `User.java`), the `sales` table also includes standard metadata:

| Field Name | Column Name | Java Type | PostgreSQL Type | Nullability | Purpose |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **`createdAt`** | `created_at` | `Instant` | `TIMESTAMPTZ` | **No** | Server timestamp marking record creation (`DEFAULT CURRENT_TIMESTAMP`). |
| **`updatedAt`** | `updated_at` | `Instant` | `TIMESTAMPTZ` | **No** | Server timestamp updated automatically on record modification. |

---

## 5. Walk-in Customer Architectural Summary

```
                      [Checkout Payload Received]
                                  │
                       Is customerId null?
                                  │
                 ┌────────────────┴────────────────┐
                 │ YES                             │ NO
                 ▼                                 ▼
         [Walk-in Customer]              [Registered Customer]
         • customer_id = NULL            • customer_id = <Long>
         • No account record inserted    • Verified against customers table
         • Receipt: "Walk-in Customer"   • Receipt: Customer Name & Phone
         • Payment: CASH, CARD, UPI      • Payment: Any (including CREDIT)
         • CREDIT payment BLOCKED        • Credit limit checked if CREDIT
```

1. **Zero Database Bloat:** Avoids cluttering the `customers` table with thousands of fake, disposable, or empty customer records.
2. **Speed at the Counter:** Eliminates checkout bottlenecks during peak retail hours.
3. **Receipt Compliance:** Receipts clearly distinguish between anonymous walk-in retail purchases and verified business account sales.

---

## 6. Document Cross-References

* **Workflow Specification:** [docs/SALES_WORKFLOW.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALES_WORKFLOW.md)
* **Sales Module Gap Analysis:** [docs/SALES_MODULE_ANALYSIS.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALES_MODULE_ANALYSIS.md)
* **Initial Analysis & Field Breakdown:** [docs/sales/sales-workflow.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/sales/sales-workflow.md)

