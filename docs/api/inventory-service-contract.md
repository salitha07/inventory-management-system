# Inventory internal service contract — Phase 01

The Java skeleton and mutation core are implemented under com.bajaj.inventory_backend.
Shared Product/Sales integration and the stock-storage architecture remain pending team
approval. The JDBC adapter is implemented but UNWIRED. No Inventory REST endpoints,
query implementations or frontend screens are provided. PostgreSQL integration tests
exist but have not been executed; database rollback/concurrency are not proven.

## Required movement vocabulary and metadata

MovementKind defines PURCHASE_RECEIVE, SALE_DEDUCT, CUSTOMER_RETURN and MANUAL_ADJUSTMENT.
Receive/return add positive quantities, sale deducts, manual adjustment accepts a signed
nonzero delta. Checked arithmetic rejects overflow and negative resulting balances.

Existing InventoryContracts records define:

| Data | Actual representation |
| --- | --- |
| Product | Long productId, matching Member 2 products.id BIGINT |
| Quantity change / after | Signed int quantityChange and nonnegative int quantityAfter |
| Reference | MovementContext.referenceType String and referenceId Long |
| Reason | MovementContext.reason, nonblank |
| Created by | MovementContext.actor: AuthenticatedActor(Long userId) |
| Ledger identity/time | StockMovementView.movementId and createdAt Instant |

AuthenticatedActor is a value object, not proof of authentication. Trusted orchestration
must verify the principal/permissions through OperationPolicy. Business references are
required positive IDs for receipt/sale/return; manual referenceId can be null. Reference
vocabulary, source-document validation and actor integration require team agreement.

## Implemented mutation operations

InventoryOperations is a plain Java object with these actual methods:

```java
StockMovementView receiveStock(StockChangeCommand command);
StockMovementView deductStock(StockChangeCommand command);
StockMovementView returnStock(StockChangeCommand command);
StockMovementView manualAdjustment(ManualAdjustmentCommand command);
```

StockChangeCommand contains productId, positive Integer quantity and MovementContext.
ManualAdjustmentCommand contains productId, signed nonzero Integer quantityChange and
MovementContext. Return eligibility is delegated to requireApprovedRestockableReturn;
production approval/restockability/remaining-return-quantity verification is not implemented.

## Compiling read-only query declarations

InventoryQueries declares these methods using existing DTOs and Spring Data Page results:

```java
CurrentStockView getCurrentStock(Long productId);
Page<CurrentStockView> searchCurrentStock(Long productId, CurrentStockQuery query, PageQuery page);
Page<StockMovementView> searchStockMovements(StockMovementQuery query, PageQuery page);
```

These are declarations only. No repository queries or HTTP routes are implemented.

- getCurrentStock requires a positive ID. Missing Product and STOCK_NOT_INITIALIZED are
  distinct failures; known zero quantity is a valid initialized balance. Exception mapping
  remains an integration decision.
- searchCurrentStock accepts an optional exact Product ID separately because CurrentStockQuery
  has no productId field. Its existing text/categoryId/active/lowStockOnly filters are retained.
  Text matches productCode/productName case-insensitively; null active means no activity filter.
- Low stock is quantity <= reorderLevel, including equality and 0/0. Null reorderLevel means
  no configured threshold and is excluded from lowStockOnly. Never coalesce it to zero.
- CurrentStockView contains productId, productCode, productName, categoryId, active, quantity
  and nullable reorderLevel. It has NO lowStock or initialization-status field. Derive low-stock
  status only for a configured threshold. Search returns initialized rows only; missing stock
  is never fabricated as zero. Detail lookup provides explicit missing/uninitialized errors.
- PageQuery is zero-based with size 1..100. Stock ordering is productCode ASC, productId ASC.
- StockMovementQuery supports productId, movementType, referenceType/id, createdBy and optional
  inclusive-from/exclusive-to instants. History order is createdAt DESC, movementId DESC.
  An empty history does not establish Product existence or balance initialization.

## Persistence port and implemented unwired JDBC adapter

InventoryPorts.AtomicStockStore.apply(productId, planner) is one combined balance/ledger
boundary. MovementWrite is a validated plan, not persisted success. The adapter validates
Product identity, locks an existing stock row FOR UPDATE, invokes the planner once and
validates Product/delta/result consistency, updates exactly one balance row and inserts all
movement metadata using RETURNING. reference_type is limited to the candidate's 64 characters.

PostgresAtomicStockStore receives JdbcTemplate and TransactionTemplate, creates no DataSource
or manager and has no component/bean registration. Its private template uses the supplied
manager with REQUIRED/write settings without modifying the supplied template. It rejects an
existing read-only transaction. Errors propagate through the transaction boundary; results
inside a caller transaction remain provisional until caller commit. Commit outcome may be
ambiguous, so automatic retry is not implemented.

Eventual wiring must use the same DataSource and a manager compatible with caller JPA/Sales.
A separate JDBC manager would not guarantee atomicity with Sales. Multiple Product locks need
consistent ordering. Product FOR KEY SHARE protects identity, not Product.active. Production
policy and source-return locking remain unresolved. No in-memory lock provides cross-process
safety; existing mocks verify protocol, not PostgreSQL rollback or overselling prevention.

Only Inventory may write quantities or stock movement rows. Database privilege enforcement,
append-only ledger enforcement, initialization and idempotency are not implemented here.

## Single V6 submission candidate — UNAPPROVED / UNVERIFIED

[Executable SQL candidate](../migrations/V6__create_inventory_stock_and_stock_movements.sql)
is the single maintained schema definition. The old .sql.draft is only a superseded pointer.
The candidate is outside active db/migration and has not been executed or validated against
PostgreSQL. It requires existing V1 users and Member 2 V2 products before activation.

PROPOSED storage: inventory_stock is the sole balance keyed by product_id, with nonnegative
quantity, nullable nonnegative reorder_level and updated_at. stock_movements contains all
required metadata, canonical type/sign checks, nonnegative after/valid prior-balance checks,
restrictive Product/user FKs and history/reference indexes. At most one balance is enforced;
initialization/backfill must be separately coordinated. No automatic opening-stock insert is
included. Do not rewrite applied V2 or activate V6 on the current V1-only branch.

## Sales compatibility and remaining dependencies

Reference Sales snapshot 2f6832d proposes inventory.service.InventoryService and
inventory.entity.StockMovementType (SALE/RETURN/ADJUSTMENT/PURCHASE). Member 6 has not created
competing classes at those paths. Its current InventoryOperations and InventoryQueries are
Inventory-owned contracts awaiting integration agreement.

| Sales declaration | Coordination needed |
| --- | --- |
| isStockAvailable(Long,Integer), getAvailableStock(Long), validateStockAvailability(Long,Integer) | Availability is not reservation; agree read mapping |
| void deductStock(Long,Integer,StockMovementType,Long) | Reconcile canonical types and command metadata/result |
| void restoreStock(Long,Integer,StockMovementType,Long) | Distinguish customer return from sale void |
| BigDecimal getCurrentUnitCost(Long) | Agree owner/source with Member 2; costPrice is a candidate |

Four-argument calls lack explicit reason/referenceType/actor. Sales's optional Inventory
injection can bypass stock operations; production integration must address that. No adapter
or silent metadata defaults are introduced. Sales's seeded catalogue must map to real IDs.

Member 1: version/activation, shared errors, principal/permissions and integration order.
Member 2: dedicated balance/threshold ownership, Product initialization, opening data and
active-state policy. Member 5: canonical vocabulary, service compatibility, caller transaction
sharing and return/void semantics. Request idempotency remains unimplemented; generic business
reference pairs cannot safely be assumed unique for partial/multi-line operations.

## Verification scope

Existing evidence: Java 21 Maven compilation succeeded; 33 database-free Inventory tests
passed and eight opt-in PostgreSQL tests were skipped. The tests cover core invariants,
metadata, adapter protocol/failure handling and restrictive test cleanup. Final verification after the query declaration change passed with the same counts. No real PostgreSQL tests, production migration checks
or JPA/Sales transaction integration have been executed. Query declarations are compile-checked,
not behavior-tested implementations. Test setup is documented separately in the team harness guide.
