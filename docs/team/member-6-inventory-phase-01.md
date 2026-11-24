# Member 6: Inventory & Stock Movements — Phase 01

The JDBC implementation and verification described below are historical. See the current
[Inventory internal service contract](../api/inventory-service-contract.md) for the JPA
adapters, PostgreSQL test setup and V6 activation dependency.

Implementation status for review, not approval of shared architecture or production readiness.
Package: com.bajaj.inventory_backend. Branch: feature/inventory-stock-movements.

## Verified scope and dependencies

The current integration base is cached origin/main c0fd5a5 (V1 users/roles only).
Latest inspected Product branch: 22e8868; Product/V2 remains unmerged. Product uses Long ID,
productCode/productName, category, prices, active and timestamps; no quantity/reorder fields.
ProductRepository supports JPA specifications. ProductService does not initialize stock.
Sales reference 2f6832d remains divergent and proposes conflicting inventory contracts.
No teammate branch was merged and no Product/Category entity or repository was duplicated.

Reference paths:

- backend/src/main/java/com/bajaj/inventory_backend/product/Product.java
- backend/src/main/java/com/bajaj/inventory_backend/repository/ProductRepository.java
- backend/src/main/resources/db/migration/V2__create_categories_and_products.sql
- Sales: inventory/service/InventoryService.java and inventory/entity/StockMovementType.java
  under backend/src/main/java/com/bajaj/inventory_backend/ on the Sales branch only.

## Phase 01 deliverables

| Requirement | Current deliverable |
| --- | --- |
| Four canonical movement types | MovementKind enum with sign/arithmetic invariants |
| Product, delta, after, reference, reason, creator | InventoryContracts records; MovementContext.actor is AuthenticatedActor(userId) |
| Receive/deduct/return/manual adjustment | InventoryOperations methods and database-free operation tests |
| Current stock/Product/low-stock/history contracts | InventoryQueries contract and implemented, unwired PostgresInventoryQueries JDBC adapter using existing DTOs |
| Negative-stock prevention | Core validation and candidate SQL constraints |
| Exclusive Inventory balance writes | Inventory-owned combined persistence port and documented ownership |
| Transactional balance/ledger writes, overselling prevention | Unwired JDBC adapter implements REQUIRED transaction and row locking; real PostgreSQL guarantees unverified |
| Prepare V6 | Single executable SQL submission candidate under docs/migrations, not active/approved/verified |
| Internal contracts | Updated API document matching current Java types and explicit pending integration |
| Compiling skeleton and tests | Java 21 compile passed after corrections; 33 passed, eight opt-in ITs skipped |
| Submit PR | Pending user review and separate authorization; no commit/push/PR action in this task |

Current stock search, Product/low-stock filtering and movement history have JDBC implementations.
PostgresStockInitializer also implements explicit stock initialization. All JDBC components
remain unwired and introduce no startup SQL or missing-table Hibernate mappings. No REST
endpoints or React screens are provided. Real PostgreSQL behavior remains unverified.

## Query contract and missing-stock semantics

See [service contract](../api/inventory-service-contract.md) and InventoryQueries.java.
getCurrentStock(Long) distinguishes nonexistent Product, uninitialized stock and initialized
zero quantity. searchCurrentStock(Long productId, CurrentStockQuery, PageQuery) accepts an
optional exact Product filter plus existing text/category/active/lowStockOnly fields.
Search returns initialized balances only; it does not invent missing quantities. The DTO has
no missing-status or lowStock field. Low stock is quantity <= configured reorderLevel;
null means no configured threshold, excluded from lowStockOnly rather than treated as zero.
History uses StockMovementQuery filters and stable createdAt/movementId descending order.
PostgresInventoryQueries implements the interface using parameterized JDBC SQL and has no
bean registration. Count and content queries may observe concurrent changes unless the caller
provides a suitable snapshot transaction. Existing tests do not directly verify these queries.

## Storage direction and V6 candidate

**PENDING MEMBER 2 / TEAM APPROVAL.** Dedicated inventory_stock is the provisional sole
balance store. It isolates quantity writes from Product catalogue saves but requires joins
and explicit lifecycle initialization. Alternatives are quantity/threshold on Product or a
separate balance with Product-owned threshold; neither is silently implemented.

[Single V6 SQL candidate](../migrations/V6__create_inventory_stock_and_stock_movements.sql)
replaces the commented schema draft; .sql.draft is now only a pointer, not another definition.
It is executable SQL for submission review but UNAPPROVED / UNVERIFIED and outside active
Flyway locations. No database SQL has been executed. Final V6 version/name needs Member 1
approval. V1 users and Member 2 V2 products must exist first. Do not activate on this branch
while Product/V2 is absent or rewrite already applied migrations.

The candidate preserves the reviewed tables, restrictive FKs, canonical type/sign checks,
nonnegative balance/threshold checks and history/reference indexes. Generic reference IDs
cannot share one FK across multiple source tables. The balance PK guarantees at most one
row per Product; it does not automatically initialize all Products. No opening stock/backfill
is guessed. PostgresStockInitializer.initializeStock(Long) is implemented with Product
existence validation and ON CONFLICT (product_id) DO NOTHING, inserting zero/null threshold
without resetting existing quantity, reorder level or updated_at. It distinguishes INITIALIZED,
ALREADY_INITIALIZED and missing Product. It remains unwired and PostgreSQL-unverified.
The future Product lifecycle call must follow persistence/flush in the same compatible
transaction. Nonzero opening stock requires an attributed approved process.

## Integrity implementation boundaries

AtomicStockStore combines balance and movement persistence. The unwired PostgresAtomicStockStore
uses supplied JdbcTemplate/TransactionTemplate, REQUIRED transaction, Product lookup, stock
FOR UPDATE lock, one planner invocation, checked result validation, exact-one-row update and
movement INSERT RETURNING. Failures propagate; outer rollback must include Inventory writes.
Caller JPA/Sales wiring must share the DataSource and a compatible manager. Product key-share
locking does not freeze active status. Actor value objects do not authenticate principals;
OperationPolicy requires a trusted production adapter. Return source eligibility/quantity
must be checked under an agreed transaction/locking policy.

Database privileges, append-only enforcement and idempotency are not implemented. Query and
initializer JDBC implementations exist, but schema activation, lifecycle calls and production
wiring remain integration work. Ordinary mutations still reject missing stock rows; they do
not silently initialize them. Trusted authentication and Returns approval/restockability
integration remain pending through OperationPolicy.
Do not infer idempotency from a reference pair or automatically retry ambiguous commits.

The eight opt-in PostgreSQL tests use minimal identity fixtures and dedicated database/schema
guards with restrictive cleanup. They have not been executed. They do not establish production
migration fidelity or JPA/Sales transaction compatibility. Existing database-free reports show
33 passing tests and eight skipped ITs. Java 21 compilation of the query adapter and initializer
also passed with those existing test counts; neither component has direct behavior-test coverage.

## Remaining coordination and submission

- Member 1: approve V6/storage integration, shared error/actor conventions, permissions and
  package-document reconciliation; current code follows the actual inventory_backend root.
- Member 2: approve sole balance/threshold ownership, initialization/backfill and active-state
  policy. Product changes remain with Member 2; no duplicate Product code is introduced.
- Member 5: reconcile SALE/RETURN/ADJUSTMENT/PURCHASE with canonical names, four-argument void
  mutations with metadata-bearing commands, and restoreStock void/return semantics. Sales's
  optional Inventory dependency, unit-cost owner and seeded Product IDs need integration work.
- Sales migration V5_1 still depends on its missing base Sales migration in the inspected ref.

Suggested message: Member 6's Phase 01 core, unwired JDBC mutation/query adapters, explicit
stock initializer and V6
submission candidate are ready for review. Please approve shared storage/lifecycle and service
compatibility before production wiring or migration activation. Real PostgreSQL verification
remains pending; this is not a fully integrated application feature.

Next: review these minimal deliverables, record final Java 21 results and prepare a focused
PR when authorized. Do not include the unrelated frontend/package-lock.json modification.
