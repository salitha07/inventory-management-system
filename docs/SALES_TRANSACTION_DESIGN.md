# Bajaj Spare Parts Inventory & Sales Management System
## POS Checkout Transaction Design & Concurrency Specification

**Module:** Sales Management & Point of Sale (POS) — Member 5  
**Collaborating Module:** Centralized Inventory & Stock Management — Member 6  
**System:** Bajaj Spare Parts Inventory & Sales Management System  
**Technology Stack:** Java 21 + Spring Boot, Spring Data JPA, PostgreSQL + Flyway  
**Package Root:** `com.bajaj.inventory_backend`  
**File Location:** `docs/SALES_TRANSACTION_DESIGN.md`

---

## 1. Executive Summary & Problem Statement

In a high-velocity Bajaj spare parts retail counter, multiple cashiers operate POS terminals concurrently. The checkout workflow involves two distinct business domains that must remain strictly synchronized:
1. **Financial Domain (Member 5):** Persisting the `sales` invoice header, saving `sale_items` line items, and recording tender payments.
2. **Inventory Domain (Member 6):** Validating stock on-hand, locking database rows, decrementing stock balances, and logging entries in `stock_movements`.

If these two operations are decoupled or executed across independent transactions, the system becomes vulnerable to **phantom stock**, **overselling**, and **financial drift** (e.g., an invoice is printed and given to the customer, but stock deduction fails due to a network glitch or stock deficit).

This document establishes the transaction design, row-level locking mechanisms, concurrency safety, rollback triggers, and ACID guarantees that keep sales and inventory operations consistent within a single database transaction.

---

## 2. ACID Guarantees in the Checkout Transaction

The POS checkout process strictly satisfies all four ACID properties:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ACID TRANSACTION LIFECYCLE                      │
├────────────────────────────────────────────────────────────────────────┤
│ • ATOMICITY:   Sale header, items & stock deductions commit together. │
│                If ANY item fails, ALL changes roll back 100%.          │
│ • CONSISTENCY: Invariants enforced: stock >= 0, line totals == subtotal,│
│                foreign keys valid, unit cost frozen.                   │
│ • ISOLATION:   Row-level pessimistic locking prevents concurrent       │
│                cashiers from double-selling the same physical unit.    │
│ • DURABILITY:  PostgreSQL Write-Ahead Logging (WAL) ensures committed  │
│                invoices survive system crashes or power outages.       │
└────────────────────────────────────────────────────────────────────────┘
```

### 1. Atomicity (All-or-Nothing Execution)
* The creation of the `sales` record, the insertion of all `sale_items` rows, the decrementing of inventory counts, and the writing of `stock_movements` logs are bound into **one atomic unit of work**.
* If an order contains 10 spare parts and the 10th item has insufficient stock, the entire transaction rolls back. No partial invoice is saved, and decrements for the first 9 items are completely undone.

### 2. Consistency (State Invariants)
* Database constraints and application rules maintain complete relational and business integrity:
  * Non-negative stock invariant: $\text{available\_quantity} \ge 0$.
  * Mathematical invoice invariant: $\text{total} = \text{subtotal} - \text{discount}$.
  * Foreign key referential integrity: Every `sale_items` row references a valid `sales.id` and `products.id`.
  * Cost snapshot invariant: `unit_cost_snapshot` is permanently frozen upon commit.

### 3. Isolation (Concurrency Control)
* PostgreSQL operates under `READ COMMITTED` isolation combined with **explicit pessimistic row-level write locks** (`FOR UPDATE`).
* Uncommitted stock deductions made by Cashier A are never visible to Cashier B until Cashier A's transaction commits.

### 4. Durability (Persistent Commitment)
* Once the transaction commits, all data is flushed to PostgreSQL's Write-Ahead Log (WAL) on disk.
* A printed receipt represents a permanently durable transaction that cannot be lost even during sudden hardware failure or server restarts.

---

## 3. Single-Transaction Architecture & Thread-Bound Connection

To guarantee that sales records and stock decrements remain consistent, `SalesService` and `InventoryService` **participate in the exact same physical database transaction**:

```
                       HTTP POST /api/v1/sales
                                  │
                                  ▼
                   SalesController.createSale(...)
                                  │
                   @Transactional (Propagation.REQUIRED)
                   Acquires dedicated JDBC Connection from HikariCP
                                  │
        ┌─────────────────────────┴─────────────────────────┐
        │                                                   │
        ▼                                                   ▼
SalesServiceImpl                                InventoryServiceImpl
• Saves Sale header                             • Inherits active Tx
• Saves SaleItem rows                           • Locks stock row (FOR UPDATE)
• Stuffs unit_cost_snapshot                     • Decrements inventory count
• Recomputes BigDecimal totals                  • Writes stock_movements log
        │                                                   │
        └─────────────────────────┬─────────────────────────┘
                                  │
                      All Operations Succeeded?
                                  │
                 ┌────────────────┴────────────────┐
                 │ YES                             │ NO (Exception thrown)
                 ▼                                 ▼
         COMMIT TRANSACTION                ROLLBACK TRANSACTION
         • sales committed                 • sales reverted
         • sale_items committed            • sale_items reverted
         • inventory decremented           • inventory restored
         • Return 201 Created              • Return 409 / 400 Error
```

### Transaction Configuration Standard
```java
@Transactional(
    propagation = Propagation.REQUIRED,
    isolation = Isolation.READ_COMMITTED,
    rollbackFor = Exception.class
)
public SaleResponse processCheckout(SaleCreateRequest request, Long cashierUserId) { ... }
```
* **`Propagation.REQUIRED`:** When `SalesService` calls `InventoryService.deductStock(...)`, Spring detects the existing transaction on the current thread and enlists `InventoryService` into it.
* **`rollbackFor = Exception.class`:** Ensures that both checked and unchecked runtime exceptions (specifically `InsufficientStockException`) trigger an immediate, complete rollback.

---

## 4. Step-by-Step Checkout Transaction Execution Flow

Below is the precise chronological sequence of execution inside the transactional boundary:

```
Step 1: Input Validation & Security Extraction
  ├── Resolve cashier ID from SecurityContextHolder
  ├── Validate payment rules (if CREDIT -> verify customerId != null)
  └── Check discount threshold (if > 5% -> verify X-Manager-Override-PIN)

Step 2: Authoritative Product & Pricing Resolution
  ├── Query PostgreSQL catalog for official selling prices (discard client prices)
  └── Query InventoryService.getCurrentUnitCost(productId) for each line item

Step 3: Financial Recalculation (BigDecimal)
  ├── Calculate line totals: quantity * catalogPrice
  ├── Calculate subtotal: sum(line totals)
  ├── Calculate total: subtotal - discount
  └── Validate cash tender: amountTendered >= total (if CASH)

Step 4: Invoice Generation & Entity Persistence
  ├── Mint next sequential saleNumber (e.g. INV-202611-0042)
  ├── Instantiate Sale header entity (status = COMPLETED)
  ├── Instantiate SaleItem detail entities (with frozen unitCostSnapshot)
  └── Invoke saleRepository.save(sale) -> flushes IDs to database

Step 5: Centralized Stock Deduction & Row Locking (Iterative)
  ├── Sort line items by productId ascending (Deadlock Prevention)
  └── For each line item:
        └── inventoryService.deductStock(item.productId, item.quantity, SALE, sale.id)
              ├── Acquire row-level lock on inventory table
              ├── IF stock < quantity: THROW InsufficientStockException (ABORT)
              ├── Decrement available stock
              └── Insert audit record into stock_movements

Step 6: Transaction Commit & Receipt Emission
  ├── Commit JDBC connection to PostgreSQL
  └── Return SaleResponse DTO (HTTP 201 Created) to React client
```

---

## 5. Stock Locking & Overselling Prevention Strategies

To prevent race conditions, the system rejects optimistic in-memory counters and enforces **database-level row serialization**.

### 1. Locking Mechanisms Compared

| Strategy | Implementation | Concurrency Behavior | Selected Pattern |
| :--- | :--- | :--- | :---: |
| **Optimistic Locking** | `@Version` column on inventory table. | Checks version upon update. If another cashier updated the row, throws `OptimisticLockException`. Requires retry loop. Under high counter volume, cashiers suffer frequent checkout retries. | *Rejected for POS* |
| **Pessimistic Row Lock (`FOR UPDATE`)** | `SELECT ... FOR UPDATE` via JPA `LockModeType.PESSIMISTIC_WRITE`. | Blocks competing transactions until the holding transaction finishes. Guarantees deterministic evaluation without retry loops. | **Recommended Standard** |
| **Atomic Conditional Update** | `UPDATE inventory SET quantity = quantity - :qty WHERE product_id = :id AND quantity >= :qty;` | Executes the check and decrement in a single atomic SQL statement. Checks affected row count ($1 = \text{success}, 0 = \text{insufficient stock}$). Highest performance. | **Approved Alternative** |

### 2. Lock Ordering (Deadlock Prevention)
If Cashier 1 buys Part A then Part B, while Cashier 2 buys Part B then Part A, a database deadlock occurs.
* **Mandatory Rule:** Before acquiring stock locks, `SalesService` **sorts all line items by `productId` in ascending numerical order**:
  ```java
  List<SaleItemRequest> sortedItems = request.items().stream()
      .sorted(Comparator.comparing(SaleItemRequest::productId))
      .toList();
  ```
* Because all concurrent threads acquire row locks in the exact same global order, **deadlocks are mathematically impossible**.

---

## 6. Concurrent Checkout Scenarios (Race Condition Analysis)

### Scenario: Two Cashiers Selling the Last Remaining Spare Part

* **Starting Inventory:** Bajaj Pulsar 150 Clutch Plate (`productId: 101`), `availableQuantity = 1`.
* **Cashier A:** Attempts to sell 1 unit at Terminal 1.
* **Cashier B:** Attempts to sell 1 unit at Terminal 2 at the exact same millisecond.

```
Time    Terminal 1 (Cashier A)                   Terminal 2 (Cashier B)
────    ──────────────────────                   ──────────────────────
T0      Submits checkout (qty: 1)                Submits checkout (qty: 1)
T1      Begins Tx A                              Begins Tx B
T2      Saves Sale A (uncommitted)               Saves Sale B (uncommitted)
T3      Requests lock on Product 101:            Requests lock on Product 101:
        SELECT ... FOR UPDATE                    SELECT ... FOR UPDATE
        ► LOCK GRANTED to Tx A                   ► BLOCKED (Queued waiting for Tx A)
T4      Checks stock: 1 >= 1 (OK)                [Suspended waiting for lock]
T5      Decrements stock: 1 - 1 = 0              [Suspended waiting for lock]
T6      Writes stock_movement (SALE)             [Suspended waiting for lock]
T7      COMMIT Tx A                              [Suspended waiting for lock]
        ► Releases lock on Product 101           ► Lock released
T8      Terminal 1 receives 201 Created          ► LOCK GRANTED to Tx B
T9      -                                        Reads updated stock: available = 0
T10     -                                        Checks stock: 0 < 1 (FAIL!)
T11     -                                        Throws InsufficientStockException
T12     -                                        ROLLBACK Tx B
T13     -                                        Terminal 2 receives 409 Conflict
```

### Result Analysis:
1. **Zero Overselling:** Physical stock never dips below zero.
2. **Correct Serialization:** Cashier A (first to acquire lock) successfully completes checkout.
3. **Graceful Rejection:** Cashier B receives an immediate `409 Conflict` error indicating stock was claimed.
4. **Clean Rollback:** Sale B is wiped out cleanly; no orphaned rows exist.

---

## 7. Rollback Triggers & Failure Modes

The table below outlines every failure scenario and the resulting database behavior:

| Failure Trigger | Throwing Component | Resulting Exception | Transaction Action | HTTP Status Code |
| :--- | :--- | :--- | :--- | :---: |
| **Insufficient On-Hand Stock** | `InventoryService` | `InsufficientStockException` | Immediate complete rollback | `409 Conflict` |
| **Product Not Found / Inactive** | `SalesService` | `ProductNotFoundException` | Immediate complete rollback | `404 Not Found` |
| **Cash Tender < Total** | `SalesService` | `InvalidPaymentException` | Immediate complete rollback | `400 Bad Request` |
| **Credit Checkout on Walk-in** | `SalesService` | `UnauthorizedTenderException`| Immediate complete rollback | `403 Forbidden` |
| **Credit Limit Exceeded** | `CustomerService` | `CreditLimitExceededException`| Immediate complete rollback | `403 Forbidden` |
| **Database Network Timeout** | HikariCP / PostgreSQL | `CannotCreateTransactionException` | Automatic driver rollback | `500 Internal Error` |
| **JVM Crash / Power Failure** | Operating System | Server crash | Uncommitted WAL entries rolled back by PostgreSQL on recovery | N/A (Restart Recovery) |

---

## 8. Error Handling & Exception Translation Layer

When a rollback occurs, the Spring Web `@RestControllerAdvice` translates domain exceptions into standard JSON error responses:

```java
@RestControllerAdvice
public class SalesExceptionHandler {

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(
            InsufficientStockException ex, HttpServletRequest request) {

        ErrorResponse error = new ErrorResponse(
            Instant.now(),
            HttpStatus.CONFLICT.value(),
            "Conflict",
            ex.getMessage(),
            request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
}
```

---

## 9. Document Cross-References

* **Centralized Inventory Integration:** [docs/SALES_INVENTORY_INTEGRATION.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALES_INVENTORY_INTEGRATION.md)
* **Roles, Permissions & Error Handling:** [docs/SALES_ROLES_AND_ERRORS.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALES_ROLES_AND_ERRORS.md)
* **Checkout API Examples:** [docs/CHECKOUT_API_EXAMPLES.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/CHECKOUT_API_EXAMPLES.md)
* **REST API Contract Specification:** [docs/POS_API_CONTRACT.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/POS_API_CONTRACT.md)

