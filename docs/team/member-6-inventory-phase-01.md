# Member 6 — Inventory & Stock Movements, Phase 01

**Status:** Independently achievable Member 6 implementation and isolated Sales-to-Inventory testing are complete. Full production integration remains pending team coordination.

The original Inventory implementation merged through PR #9 (`968faac`). The follow-up work is on `feature/inventory-stock-movements`. The latest verified implementation commit is `9143644`. See the [Inventory internal service contract](../api/inventory-service-contract.md) for API details and disposable PostgreSQL test setup.

## Current Spring Data JPA implementation

| Deliverable | Current status |
| --- | --- |
| Stock movement operations | `InventoryOperations` implements `PURCHASE_RECEIVE`, `SALE_DEDUCT`, `CUSTOMER_RETURN`, and `MANUAL_ADJUSTMENT`. The operations have unit and PostgreSQL coverage. Production receipt, return, and adjustment authorization still depends on approved policies and callers. |
| Movement ledger | `StockMovement` records the product, movement type, signed quantity change, quantity after the movement, reference type and ID, reason, creator, and timestamp. Manual adjustments may omit the reference ID under V6. |
| Current stock and history | `JpaInventoryQueries` implements `InventoryQueries` for current stock lookup and search, exact product filtering, low-stock filtering, and paginated movement history. Lookup distinguishes a missing Product, an uninitialized balance, and a known zero balance. Low stock includes equality with a configured threshold and excludes null thresholds. |
| Internal contracts | `InventoryQueries`, `InventoryOperations`, and the existing Sales-facing `InventoryService` signatures are documented. The query and initializer beans are available to internal callers. Production `InventoryOperations` and `InventoryService` registration remain pending. |
| Negative stock and atomic writes | Checked arithmetic rejects negative resulting balances and overflow. V6 enforces database constraints. `JpaAtomicStockStore` takes a pessimistic stock-row lock, refreshes the balance, and writes the balance and ledger in the same `REQUIRED` transaction. PostgreSQL tests verify rollback, including commit-time failure. |
| Concurrent checkout | The isolated checkout test synchronized two transactions after availability validation. With one unit left, exactly one sale and one deduction committed, and stock ended at zero. |
| Trusted Sales actor | `SalesStockActorResolver` resolves the authenticated Spring Security identity to an active persisted user and checks the matching checkout role. The Sales adapter rejects a mismatched movement actor or sale ID. Sales deductions use `SALE_DEDUCT`, reference type `SALE`, the saved sale ID, and the trusted creator ID. |
| Stock initialization | `JpaStockInitializer` creates a zero balance with a null threshold using `ON CONFLICT DO NOTHING`. Repeated and concurrent initialization preserve existing balances and thresholds. Product lifecycle calls and backfill remain coordination items. |

## V6 migration

The [authoritative V6 migration](../../backend/src/main/resources/db/migration/V6__create_inventory_stock_and_stock_movements.sql) is in the active Flyway resource directory. It defines `inventory_stock` and `stock_movements`, including their foreign keys, stock and movement constraints, and history indexes. Hibernate validated the JPA mappings against this schema on disposable PostgreSQL. [Purchase V4](../../backend/src/main/resources/db/migration/V4__create_purchases_and_purchase_items.sql) is also present after integrating main at `c50cf9f`. Shared migration coordination and rollout approval remain pending.

## Latest verified results

| Environment and result | Value |
| --- | --- |
| Java | 21 |
| Maven | 3.10.0 |
| Disposable PostgreSQL | 18.4 |
| Total tests | 37 |
| Passed | 37 |
| Failed | 0 |
| Errors | 0 |
| Skipped | 0 |

The suite comprised 21 unit tests and 16 PostgreSQL integration tests. The five isolated Sales-to-Inventory scenarios used the real Spring-proxied Sales service, Sales and Inventory JPA repositories, and a shared JPA transaction manager. They verified the following.

1. **Successful checkout and stock deduction.** The sale and item committed with the fixture acquisition-cost snapshot. Stock fell from five units to three.
2. **`SALE_DEDUCT` movement recording.** The committed movement recorded delta `-2`, quantity after `3`, the Product ID, reference type `SALE`, the saved sale ID, and the authenticated creator.
3. **Insufficient stock rejection.** Checkout raised `InsufficientStockException` and committed no sale, item, balance change, or deduction.
4. **Concurrent checkout overselling prevention.** Both checkouts passed availability validation and competed for the last unit. One sale, one item, and one deduction committed.
5. **Complete Sales and Inventory transaction rollback.** The fixture forced the second deduction to fail after the first had written its balance and ledger. A separate transaction confirmed that the sale, both items, the earlier balance update, and the earlier ledger entry rolled back.

The checkout fixture supplied the Product catalog, acquisition cost, movement reason, and operation policy for testing. The run covered the isolated transaction path. Production HTTP/JWT wiring, the complete shared Flyway chain, and full application startup remain unverified.

## Production coordination

| Owner | Remaining dependency |
| --- | --- |
| Inventory, Auth, Sales, and Product owners | Agree on the acquisition-cost source, the stock movement reason, and the operation policies needed to register production `InventoryOperations` and `InventoryService`. Confirm how checkout enforces Product eligibility when deactivation occurs concurrently. |
| Product and Sales owners | Connect the authoritative Product catalog to checkout and make Inventory usage mandatory. Sales currently accepts Inventory optionally, so checkout can bypass stock validation and deduction when the bean is absent. |
| Product and Purchase owners | Coordinate Product initialization and backfill, opening balances, reorder-threshold ownership, and authorized Purchase receipt integration. |
| Sales and return workflow owners | Define return approval, restockability, eligible quantities, and sale void classification before enabling restoration. |
| Member 1 and migration owners | Resolve the duplicate Catalog and Audit V2 migrations and verify the target Flyway history. Coordinate rollout approval and full production application verification. |

## Historical JDBC work

The original Inventory adapters used Spring JDBC. Commit `8f08314` replaced them with Spring Data JPA while preserving the Inventory domain and service contracts. Commit `3769c90` moved V6 into the active Flyway resource directory. Earlier JDBC wiring notes, draft migration locations, and skipped PostgreSQL counts describe superseded implementation stages. The results above are from the current JPA implementation.
