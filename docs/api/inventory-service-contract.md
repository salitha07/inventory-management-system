# Inventory internal service contract — Phase 01

The Inventory core is storage-independent. Spring registers the JPA persistence adapters
for internal stock reads, movements and initialization. Production InventoryOperations and
SalesInventoryService registration still requires approved policies and metadata sources.
V6 is in the active Flyway resource directory. Shared migration coordination and rollout
approval are pending.

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

## Read-only query contract

InventoryQueries declares these methods using existing DTOs and Spring Data Page results:

```java
CurrentStockView getCurrentStock(Long productId);
Page<CurrentStockView> searchCurrentStock(Long productId, CurrentStockQuery query, PageQuery page);
Page<StockMovementView> searchStockMovements(StockMovementQuery query, PageQuery page);
```

JpaInventoryQueries uses Spring Data JPA projections for balances and Specifications for
history. Count and page-content reads may observe concurrent changes unless the caller
supplies a suitable snapshot transaction.

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

## JPA persistence and transactions

InventoryPorts.AtomicStockStore applies a validated MovementWrite under a REQUIRED write
transaction. JpaAtomicStockStore flushes pending caller changes, acquires Product
FOR KEY SHARE, then locks the stock row with PESSIMISTIC_WRITE. It refreshes any managed
balance under that lock, validates the planned quantity, updates the balance and flushes
the ledger insert in the same transaction. Product and actor references use scalar IDs
with foreign keys defined by V6. No association cascades to Product or User.

A caller using the application JPA transaction manager owns commit and rollback.
Multi-product callers must lock in a consistent order. The Product lock protects identity,
while active-state policy and return eligibility still require agreement.

## Stock initialization

JpaStockInitializer runs in a REQUIRED write transaction. It flushes pending JPA changes,
checks Product identity under FOR KEY SHARE and inserts a zero balance with a null
threshold using PostgreSQL ON CONFLICT DO NOTHING. Repeated initialization preserves
the balance, threshold and timestamp. Missing Products and uninitialized balances remain
distinct failures. Product creation and backfill need separate lifecycle agreement.

## V6 Flyway migration and rollout

[Executable V6 migration](../../backend/src/main/resources/db/migration/V6__create_inventory_stock_and_stock_movements.sql)
is the single maintained schema definition. The old .sql.draft is only a superseded pointer.
Flyway discovers V6 in `classpath:db/migration`. Hibernate schema validation includes
the Inventory mappings, so startup requires both Inventory tables. Member 1 must coordinate
rollout after resolving the competing V2 versions and checking the target schema history.
Purchase V4 is present in the active migration directory after integrating main at
`c50cf9f`. The duplicate Catalog and Audit V2 versions still block full Flyway startup.

The candidate uses BIGINT identifiers, INTEGER balances and thresholds, and TIMESTAMPTZ
for the JPA Instant fields. The movement identity uses GENERATED BY DEFAULT AS IDENTITY.
V6 owns the foreign keys, CHECK constraints, defaults and indexes. Hibernate schema
validation covers mapped schema compatibility, so database tests also exercise constraints.
The balance primary key supports exact stock lookup and initialization conflict detection.
Ledger indexes support product history, global history and reference lookup. Product name
and code substring search has no dedicated Inventory index. Index selection depends on
the query and data distribution.

PROPOSED storage: inventory_stock is the sole balance keyed by product_id, with nonnegative
quantity, nullable nonnegative reorder_level and updated_at. stock_movements contains all
required metadata, canonical type/sign checks, nonnegative after/valid prior-balance checks,
restrictive Product/user FKs and history/reference indexes. At most one balance is enforced;
initializer lifecycle integration and backfill need separate coordination. The candidate
does not insert opening stock.

## Sales compatibility and remaining dependencies

Reference Sales snapshot 2f6832d proposes inventory.service.InventoryService and
inventory.entity.StockMovementType (SALE/RETURN/ADJUSTMENT/PURCHASE). Member 6 has not created
competing classes at those paths. Its current InventoryOperations and InventoryQueries are
Inventory-owned contracts awaiting integration agreement.

| Sales declaration | Coordination needed |
| --- | --- |
| isStockAvailable(Long,Integer), getAvailableStock(Long), validateStockAvailability(Long,Integer) | Availability is not reservation; agree read mapping |
| void deductStock(Long,Integer,StockMovementType,Long) | `SALE` maps to `SALE_DEDUCT` with reference type `SALE` and the saved sale ID. Production reason still needs agreement. |
| void restoreStock(Long,Integer,StockMovementType,Long) | Distinguish customer return from sale void |
| BigDecimal getCurrentUnitCost(Long) | Agree owner/source with Member 2; costPrice is a candidate |

Four-argument calls lack explicit reason/referenceType/actor. Sales's optional Inventory
injection can bypass stock operations. The compatibility adapter remains unregistered
pending approved collaborators and metadata. Sales's seeded catalogue must map to real IDs.

For Sales checkout deduction, `SalesStockActorResolver` reads the authenticated UserDetails
from Spring Security, resolves its email through UserRepository and requires an active
persisted account. The current account role and authenticated authority must agree.
The documented checkout roles are CASHIER, MANAGER and ADMIN. The adapter rejects a
movement context whose actor differs from the resolved user or whose reference ID differs
from the sale ID. This checkout check grants no permissions for receipts, adjustments,
returns or voids and does not implement the general OperationPolicy.

The checkout caller uses availability validation, acquisition-cost lookup and deduction.
The cost source and deduction reason still require agreement before
registering the service. Restoration remains unavailable until approved return eligibility
and void classification are defined. The purchase contract requires a purchase reference
and describes its role list as proposed. Manual adjustment still needs an approved role
and reference policy. InventoryQueries and JpaStockInitializer remain internal JPA beans.

Member 1: version/activation, shared errors, principal/permissions and integration order.
Member 2: dedicated balance/threshold ownership, Product initialization, opening data and
active-state policy. Member 5: canonical vocabulary, service compatibility, caller transaction
sharing and return/void semantics. Returns module approval/restockability checks and trusted
authentication/authorization integration remain pending. Request idempotency remains unimplemented; generic business
reference pairs cannot safely be assumed unique for partial/multi-line operations.

## Verification scope

JpaInventoryPostgresTest and SalesCheckoutPostgresTest are opt-in using
INVENTORY_TEST_POSTGRES_PORT. Point them only at
a newly initialized disposable loopback PostgreSQL cluster. Use the database and role
inventory_jpa_test and test-only trust authentication within that cluster. Apply the Users
V1, catalog V2, Sales V5 and V5_1, and Inventory V6 scripts from
`backend/src/main/resources/db/migration` to the disposable database, then insert a category
named Inventory fixture. Run the Inventory tests with Java 21 and Maven, then stop the
temporary server. These test contexts validate their mapped schemas and use a disposable
database.

The isolated PostgreSQL 18.4 run passed 37 Inventory tests, including five checkout
integration tests. The checkout tests use the Spring-proxied SalesService, real Sales and
Inventory repositories and a shared JPA transaction manager. They verified committed sale
items and cost snapshots, sale-linked movements with the authenticated creator, insufficient
stock rejection, and two checkouts that both passed preflight but could commit only one sale
for the last unit. A forced failure on the second deduction rolled back the sale, sale items,
earlier balance update and earlier ledger insert.

The checkout fixture supplies the catalog, acquisition cost, movement reason and operation
policy for tests only. The run does not establish production bean registration, authorization
through the HTTP filter chain, full Flyway startup or rollout readiness.
