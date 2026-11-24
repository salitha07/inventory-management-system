# Bajaj Spare Parts Inventory & Sales Management System
## Sales Management & Centralized Inventory Integration Specification

**Module:** Sales Management & Point of Sale (POS) — Member 5  
**Collaborating Module:** Centralized Inventory & Stock Management — Member 6  
**System:** Bajaj Spare Parts Inventory & Sales Management System  
**Technology Stack:** Java 21 + Spring Boot, Spring Data JPA, PostgreSQL + Flyway  
**Package Root:** `com.bajaj.inventory_backend`  
**File Location:** `docs/SALES_INVENTORY_INTEGRATION.md`

---

## 1. Domain Ownership Boundaries & Separation of Concerns

A core principle of the system architecture is the strict encapsulation of domain responsibilities between **Member 5 (Sales Management)** and **Member 6 (Centralized Inventory Management)**:

```
┌─────────────────────────────────────────────────────────────┐
│              SALES DOMAIN (Member 5 Ownership)              │
├─────────────────────────────────────────────────────────────┤
│ • POS counter session, cart compilation & line math         │
│ • Price calculations, discounts & tender settlement         │
│ • Invoice & receipt generation (sales & sale_items tables)  │
│ • Freezing acquisition cost basis (unit_cost_snapshot)      │
│ • Cashier audit tagging (created_by)                        │
└──────────────────────────────┬──────────────────────────────┘
                               │
                Calls Synchronous Java Interface
                 (Inside @Transactional boundary)
                               │
┌──────────────────────────────▼──────────────────────────────┐
│           INVENTORY DOMAIN (Member 6 Ownership)             │
├─────────────────────────────────────────────────────────────┤
│ • Authoritative stock levels (inventory / stock tables)     │
│ • Concurrency control & row-level locking                   │
│ • Enforcing reorder level / minimum stock thresholds        │
│ • Audit logging in stock_movements ledger                   │
│ • Physical bin & warehouse location management              │
└─────────────────────────────────────────────────────────────┘
```

### Strict Ownership Rule
> [!CAUTION]
> **`SalesService` must NEVER directly execute updates against inventory tables** (e.g., `UPDATE inventory ...` or modifying `Product.quantity` via JPA). 
> 
> Direct stock manipulation by the Sales module would bypass inventory business rules, skip mandatory movement logging, violate audit trails, and break concurrency locks. All stock decrements and restorations must delegate exclusively to `InventoryService`.

---

## 2. Centralized Stock Deduction Contract (`InventoryService.deductStock`)

### Proposed Java Interface Specification
Member 5 coordinates with Member 6 to establish the synchronous deduction contract:

```java
package com.bajaj.inventory_backend.inventory.service;

import com.bajaj.inventory_backend.inventory.entity.StockMovementType;
import java.math.BigDecimal;

public interface InventoryService {

    /**
     * Atomically validates and decrements stock for a single spare part.
     * Must participate in the caller's active database transaction.
     *
     * @param productId     The primary key of the catalog spare part.
     * @param quantity      The positive integer quantity to deduct (>= 1).
     * @param movementType  The classification of stock movement (e.g., SALE, RETURN, ADJUSTMENT).
     * @param referenceId   The ID of the triggering business entity (sale.getId()).
     * @throws InsufficientStockException if available on-hand stock is less than quantity.
     * @throws ProductNotFoundException   if productId does not exist or is inactive.
     */
    void deductStock(
        Long productId,
        Integer quantity,
        StockMovementType movementType,
        Long referenceId
    );

    /**
     * Retrieves the current unit acquisition/cost basis of a spare part
     * to be frozen into sale_items.unit_cost_snapshot for Member 7 reporting.
     *
     * @param productId The primary key of the spare part.
     * @return The acquisition cost per unit.
     */
    BigDecimal getCurrentUnitCost(Long productId);

    /**
     * Restores stock during same-day sale voiding or customer return workflow.
     */
    void restoreStock(
        Long productId,
        Integer quantity,
        StockMovementType movementType,
        Long referenceId
    );
}
```

---

## 3. Stock Availability Validation & Concurrency Control

In a busy Bajaj spare parts counter with multiple concurrent POS terminals, multiple cashiers may attempt to sell the last remaining stock of a high-demand part (e.g., a Bajaj Pulsar 150 digital speedometer assembly) at the same second.

### 1. Two-Tier Stock Validation Strategy
1. **Tier 1 — Client-Side Optimistic Validation (React):**
   * Early warning in the POS UI based on cached stock numbers from the product search API.
   * Warns the cashier immediately if they attempt to stage more units than known in memory.
   * *Non-authoritative:* Does not prevent race conditions.
2. **Tier 2 — Server-Side Authoritative Row-Level Lock (Member 6 / PostgreSQL):**
   * Executed within the checkout database transaction.
   * `InventoryService` acquires a pessimistic row write lock on the target stock record:
     ```sql
     -- Option A: Explicit Row Lock
     SELECT quantity FROM inventory WHERE product_id = :productId FOR UPDATE;
     
     -- Option B: Conditional Atomic Decrement
     UPDATE inventory 
     SET quantity = quantity - :quantity, updated_at = CURRENT_TIMESTAMP
     WHERE product_id = :productId AND quantity >= :quantity;
     ```
   * If the row lock or conditional decrement indicates $\text{quantity} < \text{requestedQuantity}$, the deduction fails immediately.

---

## 4. Insufficient Stock Error Handling

When available stock is less than the requested checkout quantity:

1. **Exception Hierarchy:**
   `InventoryService` throws a dedicated runtime exception:
   ```java
   package com.bajaj.inventory_backend.common.exception;

   public class InsufficientStockException extends RuntimeException {
       private final Long productId;
       private final String productName;
       private final Integer requestedQuantity;
       private final Integer availableQuantity;

       public InsufficientStockException(
               Long productId, 
               String productName, 
               Integer requestedQuantity, 
               Integer availableQuantity) {
           super(String.format(
               "Insufficient stock for product ID %d ('%s'). Requested: %d, Available: %d",
               productId, productName, requestedQuantity, availableQuantity
           ));
           this.productId = productId;
           this.productName = productName;
           this.requestedQuantity = requestedQuantity;
           this.availableQuantity = availableQuantity;
       }
       // getters...
   }
   ```
2. **REST API Mapping:**
   Caught by the global exception handler (`GlobalExceptionHandler`) and mapped to HTTP `409 Conflict`:
   ```json
   {
     "timestamp": "2026-11-13T15:40:12Z",
     "status": 409,
     "error": "Conflict",
     "message": "Insufficient stock for product ID 101 ('Clutch Plate Assembly'). Requested: 5, Available: 2",
     "path": "/api/v1/sales"
   }
   ```

---

## 5. Transaction Rollback & Atomicity Guarantees

The entire checkout process must operate under an **atomic all-or-nothing transactional boundary**:

```
[Cashier Submits Checkout]
          │
  @Transactional (Propagation.REQUIRED)
          │
  1. Validate Customer & Calculate Totals
  2. Mint saleNumber (e.g. INV-202611-0001)
  3. Fetch current unit cost snapshots from InventoryService
  4. Save Sale & SaleItem entities to DB
          │
  5. For each item in Cart:
     └──► InventoryService.deductStock(item.productId, item.quantity, SALE, sale.id)
                │
                ├───► Stock Check PASS: Row decremented + Movement Logged
                │
                └───► Stock Check FAIL: Throws InsufficientStockException
                                              │
                      ┌───────────────────────▼───────────────────────┐
                      │    SPRING AUTOMATIC TRANSACTION ROLLBACK      │
                      ├───────────────────────────────────────────────┤
                      │ • Sale record is NOT inserted                 │
                      │ • SaleItem records are NOT inserted           │
                      │ • Prior successful item decrements REVERTED   │
                      │ • Ledger movement records REVERTED            │
                      │ • Zero orphaned data; Zero inventory drift    │
                      └───────────────────────────────────────────────┘
```

### Transaction Configuration
* The `SalesService.createSale(...)` method is annotated with `@Transactional(rollbackFor = Exception.class)`.
* `InventoryService.deductStock(...)` runs with default `Propagation.REQUIRED`, participating directly in the caller's active database transaction.
* If any item in a 10-item cart fails stock validation, the entire transaction rolls back cleanly, leaving database state identical to before checkout was attempted.

---

## 6. Stock Movement Recording Dependencies

For auditing and statutory compliance, `InventoryService` must maintain an immutable stock ledger (`stock_movements` table) tracking every quantity change:

### Required Ledger Attributes
When `InventoryService.deductStock` is invoked by `SalesService`, it must persist a stock movement row capturing:
* `product_id`: The spare part affected.
* `quantity`: Negative delta (e.g., `-2` for a 2-unit sale).
* `previous_quantity`: Stock before decrement.
* `new_quantity`: Stock after decrement.
* `movement_type`: `StockMovementType.SALE`.
* `reference_id`: Synthetic ID of the sale (`sales.id`).
* `reference_number`: The invoice number (`sale_number`, e.g., `INV-202611-0001`).
* `created_by`: Cashier user ID (`users.id`).
* `created_at`: `TIMESTAMPTZ` audit timestamp.

This immutable ledger allows **Member 7 (Reporting)** to cross-reconcile physical inventory audits against recorded sales revenue.

---

## 7. Coordination & Missing Contracts with Member 6

To finalize implementation, Member 5 requires confirmation from Member 6 on the following technical contracts:

| Contract / Requirement | Current Status | Specific Confirmation Required from Member 6 |
| :--- | :---: | :--- |
| **`deductStock` Signature** | **Pending** | Confirm parameter types: `(Long productId, Integer quantity, StockMovementType type, Long referenceId)`. |
| **Batch Deduction Method** | **Proposed** | Evaluate if Member 6 prefers a batch method: `void deductStockBatch(List<StockDeductionRequest> items, Long saleId)` to minimize round-trips. |
| **Unit Cost Retrieval** | **Missing** | Confirm method name and return type for fetching current acquisition cost: `BigDecimal getCurrentUnitCost(Long productId)`. |
| **Stock Restoration Method** | **Pending** | Confirm signature for restocking items during same-day invoice voiding: `restoreStock(Long productId, Integer quantity, StockMovementType type, Long referenceId)`. |
| **Exception Hierarchy** | **Pending** | Confirm whether `InsufficientStockException` will be placed in `com.bajaj.inventory_backend.common.exception` for shared consumption across modules. |

---

## 8. Document Cross-References

* **Roles & Permissions:** [docs/SALES_ROLES_AND_ERRORS.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALES_ROLES_AND_ERRORS.md)
* **Checkout API Examples:** [docs/CHECKOUT_API_EXAMPLES.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/CHECKOUT_API_EXAMPLES.md)
* **REST API Contract Specification:** [docs/POS_API_CONTRACT.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/POS_API_CONTRACT.md)
* **Sales Workflow Specification:** [docs/SALES_WORKFLOW.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALES_WORKFLOW.md)

