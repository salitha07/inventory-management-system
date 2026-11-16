# Member 6: Inventory & Stock Movements — Phase 01

**DRAFT — for team review; no proposed design is approved or implemented.**

Prepared 2026-11-16 on `feature/inventory-stock-movements`. This document and the
[draft service contract](../api/inventory-service-contract.md) describe the assignment,
dependencies and proposals only. They do not establish working stock functionality.

## Verified baseline

Evidence comes from local Git snapshots, without fetching or switching branches:

| Snapshot | Commit | Relevant evidence |
| --- | --- | --- |
| HEAD / origin/main | c0fd5a5 | Authentication, users/roles and V1; Inventory is an empty placeholder |
| origin/feature/products-categories | c9a198a | Product, Category, ProductRepository and V2; two commits ahead of cached main |
| origin/feature/sales-management | 2f6832d | Proposed Inventory interface/enum and Sales callers; diverged from main |

Existing Java root is `com.bajaj.inventory_backend`. Using that root is PROPOSED;
the assignment document's `com.bajaj.inventory` requires Member 1 clarification.
README specifies Java 21, Spring Boot, JPA, PostgreSQL, Flyway, React and Vite.
Current documentation directories otherwise contain empty placeholders.

Exact Product-branch paths:

- `backend/src/main/java/com/bajaj/inventory_backend/product/Product.java`
- `backend/src/main/java/com/bajaj/inventory_backend/category/Category.java`
- `backend/src/main/java/com/bajaj/inventory_backend/repository/ProductRepository.java`
- `backend/src/main/resources/db/migration/V2__create_categories_and_products.sql`

Product uses `Long id`, mapped to `products.id BIGSERIAL`. It defines product code,
name, category, active flag, cost/selling prices and timestamps. Neither the entity
nor V2 has stock quantity, reorder level or locking/version fields. ProductRepository
extends `JpaRepository<Product, Long>` with code/category checks, but no stock queries
or explicit locking lookup. These files are unmerged into cached main.

Exact Sales-branch paths under `backend/src/main/java/com/bajaj/inventory_backend/`:

- `inventory/service/InventoryService.java`
- `inventory/entity/StockMovementType.java`
- `sales/SalesService.java` and `sales/validation/CheckoutValidator.java`
- `common/exception/InsufficientStockException.java` and `ProductNotFoundException.java`
- `product/service/ProductCatalogService.java`, `DefaultProductCatalogService.java`
- `product/dto/ProductPriceDetails.java`

Sales proposes SALE/RETURN/ADJUSTMENT/PURCHASE and calls `deductStock(..., SALE, saleId)`.
It accepts an optional Inventory dependency and skips stock work when absent.
Its catalogue fallback is seeded in memory; IDs need not match database Products.
`docs/SALES_INVENTORY_INTEGRATION.md` on that branch is a proposal, not Member 6 approval.

## Required scope and current gaps

| Assignment requirement | Baseline / planned evidence |
| --- | --- |
| PURCHASE_RECEIVE, SALE_DEDUCT, CUSTOMER_RETURN, MANUAL_ADJUSTMENT | Required vocabulary; enum reconciliation TBD |
| Product, quantity change, quantity after, reference type/id, reason, created by | Required movement data; detailed proposal in service contract |
| Current stock search, low-stock filter, movement history | Query contracts PROPOSED; no implementation |
| receiveStock, deductStock, returnStock, manual adjustment | Contracts PROPOSED; no implementation |
| Exclusive Inventory writes, no negative balance | Required invariants; persistence design TBD |
| Atomic balance plus movement; concurrent overselling prevention | Required behavior; database implementation and tests missing |
| V6 stock_movements | Required migration deliverable; prerequisites unresolved; commented review draft under docs/migrations |
| Internal contracts, compiling skeleton, tests and PR | Documentation drafted; skeleton, compilation, tests and PR remain pending |

PROPOSED Phase 01 boundary: agree and document contracts/schema, then implement only
the authorized skeleton and required behavior. Whether executable mutations/controllers
are required in Phase 01 is TBD with Member 1. React screens are not assumed in scope.

## Stock and reorder storage alternatives

**Final choice: PENDING MEMBER 2 / TEAM APPROVAL.** Dedicated inventory_stock is the current provisional direction, not an approved choice.

| PROPOSED option | Advantages | Tradeoffs / decisions |
| --- | --- | --- |
| Quantity and reorder level on products | Simple current-stock query; existing Product row provides lock target | Couples catalogue and balance writes; coordinate entity changes and prevent stale Product saves from overwriting balance; Inventory alone changes quantity |
| One balance row per Product, reorder level on Product | Separates transactional balance ownership from catalogue policy | Requires join, unique Product association and safe initial-row creation; reorder edits remain a Product-owner decision |
| One inventory row per Product containing quantity and reorder level | Inventory owns stock queries and thresholds together | New entity/table and lifecycle; decide who may edit thresholds and handle Products without rows |

For all options, initial quantity, initial movement policy, threshold defaults/nullability,
inactive Product behavior and integer limits are TBD. A ledger-only balance is not
proposed for this minimal phase. Never maintain two independently writable balances.

## V6 prerequisites and schema outline

PROPOSED filename: `backend/src/main/resources/db/migration/V6__create_stock_movements.sql`.
This is a design outline only; no executable migration is supplied.

Prerequisites:

1. Member 1 reserves V6 and approves migration ownership/order.
2. V1 users and Member 2's V2 products exist in the agreed integration base.
3. Balance/reorder storage and initial-row/backfill policy are approved.
4. Movement names, metadata nullability, actor policy and reference semantics are agreed.
5. Fresh-database and existing-database upgrade paths are reviewed. Do not rewrite an
   already applied V2; coordinate any additions as a new migration.

PROPOSED `stock_movements` outline:

| Column | Proposed shape / constraint |
| --- | --- |
| id | BIGINT generated primary key |
| product_id | Non-null BIGINT FK to products.id |
| movement_type | Non-null bounded string with four agreed names |
| quantity_change | Non-null signed INTEGER, nonzero |
| quantity_after | Non-null INTEGER, >= 0 |
| reference_type | Non-null bounded string; vocabulary/length TBD |
| reference_id | BIGINT; mandatory for receive/sale/return, manual policy TBD |
| reason | TEXT, nonblank PROPOSED; limits TBD |
| created_by | Non-null BIGINT FK to users.id, subject to actor policy |
| created_at | Non-null TIMESTAMPTZ |

PROPOSED indexes: `(product_id, created_at, id)` for history and
`(reference_type, reference_id)` for traceability. Sign constraints should match the
movement kinds. Balance nonnegative constraints belong on the chosen authoritative
balance storage too. Foreign-key deletion policy is TBD; preserve historical evidence.
A polymorphic reference pair cannot use one ordinary FK to multiple source tables.
Previous quantity and invoice/reference number are Sales proposals, not approved additions.

Current main has only V1. Product branch adds V2. Sales branch has
`V5_1__add_qr_payment_method.sql`, which alters sales but supplies no base sales migration.
V6 does not inherently require V3–V5 if it references only users/products, but the complete
application migration chain must be coordinated. Missing version numbers alone are not
a dependency failure; missing referenced tables are.

## Coordination questions and ready-to-send drafts

These messages are prepared for the user to send; none has been sent automatically.

### Member 1: integration, security and phase boundary

Questions: Which package root is authoritative? Who owns V6 and shared exceptions?
Which roles may query/adjust stock? How is a trusted user resolved for internal calls?
Does Phase 01 require working mutations or a compiling skeleton? What integration order
preserves main's authentication while incorporating the divergent Sales branch?

> Member 1: I have drafted Member 6 contracts, with no Java/SQL changes. Please confirm
> `com.bajaj.inventory_backend`, V6 ownership, actor/permission conventions and whether
> Phase 01 requires executable stock operations or a skeleton. Product is unmerged and
> Sales diverges from main; please confirm the integration order and shared exceptions.

### Member 2: Product, balances and reorder policy

Questions: Where should quantity and reorder level live? Who changes thresholds?
How are zero-stock rows initialized? May Inventory lock the Product row or should it lock
a dedicated balance row? What are the active-product, quantity range and cost-price rules?
How will database Product data replace Sales's seeded catalogue?

> Member 2: Your Product/V2 at c9a198a has no quantity or reorder level. Please review the
> three storage options in my draft and agree one authoritative balance, Inventory-only
> quantity writes, threshold ownership and initial-row behavior. I will reuse your Product
> and repository rather than duplicate them. Please also confirm costPrice semantics.

### Member 5: shared contract and checkout safety

Questions: Can the four required movement names replace the legacy names in one
coordinated change? How should reference type, reason and actor be supplied? Is sale
voiding distinct from customer return? Should restoreStock remain as an adapter?
Can checkout require Inventory rather than silently bypass it? Who owns unit-cost lookup,
shared exceptions, retry/idempotency policy and completion of the Sales migration chain?

> Member 5: I found your InventoryService and enum at 2f6832d. My draft uses the required
> PURCHASE_RECEIVE/SALE_DEDUCT/CUSTOMER_RETURN/MANUAL_ADJUSTMENT vocabulary and proposes
> metadata-bearing commands. Please review a single shared contract, return/void semantics
> and compatibility approach before either of us edits interfaces. Checkout currently skips
> stock writes when Inventory is absent; we need mandatory participation and a shared
> transaction, plus the missing base Sales migration and real PostgreSQL concurrency tests.

## Dependency-ordered follow-up — PROPOSED

1. Resolve the questions above and record approvals explicitly.
2. Integrate prerequisites through the team's approved process; do not copy entire feature snapshots.
3. Establish one enum/interface and DTO set under the agreed root; reconcile Sales callers.
4. Add the approved balance schema/access and V6, then StockMovement entity/repository.
5. Add the authorized service skeleton/implementation and query methods. No no-op success paths.
6. Verify compilation and isolated PostgreSQL tests: validation, signed deltas, exact depletion,
   insufficient stock, persistence-failure rollback, caller rollback, concurrent checkout,
   initial-row race, low-stock boundaries and stable history pagination.
7. Review the diff, record actual results, and prepare a focused PR when authorized.

No implementation, migration execution, tests, commits or push are part of this draft task.
The pre-existing modification to `frontend/package-lock.json` must remain untouched.

## Independent Java foundation follow-up (2026-11-16)

The user subsequently authorized storage-independent Java foundation work under
`com.bajaj.inventory_backend`. The baseline and proposals above remain historical drafts.

Added `inventory/domain/MovementKind.java` with the four required names and pure signed
balance arithmetic, plus `inventory/dto/InventoryContracts.java` containing provisional
command, metadata, actor, stock/history view and query records. Constructor validation
is active without Spring. `AuthenticatedActor` represents trusted caller metadata; it
does not authenticate a user or prove authorization. No persistence or service exists.

The distinct MovementKind name avoids replacing Member 5's incompatible
`inventory.entity.StockMovementType`; it must be consolidated during team integration,
not treated as a second permanent shared enum. InventoryService remains pending because
Sales uses legacy enum names, four-argument void mutations and restoreStock. No adapter,
shared interface, repository, JPA mapping, migration or stock writer was added.

Provisional rules in DTOs: nonblank reason, signed manual delta, optional manual reference,
positive business-event references, nullable unknown reorder level, and bounded pagination.
Low-stock comparison, query implementation and sort policy remain deferred. Product identity
and code/name fields match Member 2; no Product/Category classes were copied.

Verification: Java 21.0.9 selected. Maven Wrapper failed to start in this environment;
cached Maven 3.10.0 reached the compiler but failed accessing a cached Spring Boot JAR.
The complete Maven compile is therefore not verified. Standalone JUnit execution found
five tests and passed all five without Spring/database access; javac also emitted an
environmental AccessDeniedException while closing JAR files. Generated check artifacts
are under ignored backend/target. Full Maven verification remains pending.


## Persistence design follow-up — PROPOSED / NOT APPROVED

Local snapshots rechecked 2026-11-16: HEAD/origin/main c0fd5a5, Product c9a198a,
Sales 2f6832d. V2 is still absent from HEAD and cached origin/main. No fetch was done.
The user reports subsequent successful Java 21 Maven compilation and five passing tests;
this supersedes the earlier environment report for their run, but was not rerun here.
Java foundation remains unchanged and provides no persistence guarantees.

### Dedicated authoritative balance

PROPOSED inventory_stock has product_id BIGINT primary key/FK to products.id,
quantity INTEGER NOT NULL DEFAULT 0 CHECK >= 0, nullable reorder_level INTEGER CHECK >= 0,
and updated_at TIMESTAMPTZ NOT NULL. Only Inventory writes quantities. Do not add a second
quantity to Product. Threshold write permissions are TBD with Members 1/2.
This separates balance writes from stale Product catalogue saves and offers one lock target.
Tradeoffs are Product joins, coordinated initialization and explicit missing-row handling.
The key ensures at most one row; application lifecycle must ensure exactly one per Product.
**PENDING MEMBER 2 / TEAM APPROVAL**, including nullable threshold and zero initialization.

### Product lifecycle and opening data

PROPOSED: Member 2 persists/flushed Product and synchronously invokes an Inventory initializer
in the same REQUIRED transaction. Insert zero/null threshold with ON CONFLICT(product_id)
DO NOTHING; never reset existing balances or thresholds. The FK rejects nonexistent Products,
and the primary key serializes competing inserts. Failure rolls back Product creation too.
Interface and Product availability sequencing require Member 2 agreement; no async event assumed.

For existing Products, zero-row backfill is optional only after verifying there is no real
stock to import and coordinating deployment so concurrent Product creation cannot escape
initialization. Zero initialization has no movement because there is no quantity change.
Nonzero opening stock needs an approved attributed adjustment/import, never anonymous ledger
rows. Normal reads/writes reject missing rows; explicit Inventory repair can use the same
conflict-safe initializer. No lazy silent repair. Restrictive Product/user FKs preserve history;
prefer deactivation to deletion. Atomic active-status checks need an agreed Product lock/order
policy if required; locking only a stock row does not lock Product.active.

### Low-stock contract

PROPOSED low stock is quantity <= reorder_level for initialized rows with a configured
threshold. Quantity 0/threshold 0 and equality at positive values are low. A null threshold
means unconfigured/unknown, not zero; exclude it from lowStockOnly. Missing row means
STOCK_NOT_INITIALIZED, never zero or false. A detail query returns an explicit initialization
error. Search should left-join Product to expose missing rows separately; do not construct
CurrentStockView with invented quantity. Its current shape lacks initialization/status fields;
any DTO adaptation is deferred. Existing zero stock, missing stock and missing Product differ.

### Transactions and concurrent checkout

PROPOSED primary strategy: join caller REQUIRED transaction; lock existing inventory_stock
rows FOR UPDATE in ascending Product ID order; recheck balance and checked integer arithmetic;
update quantity/updated_at and append the matching movement. Locks last through caller commit.
Movement insert failure or later sale-line failure rolls back all balances, movements and
business records. Do not suppress exceptions or use REQUIRES_NEW. A preliminary availability
read does not reserve stock; READ_COMMITTED or JVM locks alone cannot prevent overselling.

Alternative, not selected: conditional atomic decrement with quantity >= requested and
RETURNING quantity. Zero affected rows must distinguish uninitialized stock from insufficient
stock; positive changes need overflow checks. Do not mix with unlocked stale JPA updates.
Timeout/deadlock aborts the whole transaction; retries need revalidation and idempotency policy.

### Ledger and duplicate requests

PROPOSED stock_movements columns: id, product_id, movement_type, quantity_change,
quantity_after, reference_type, reference_id, reason, created_by, created_at. The draft
uses required movement names, nonzero signed delta, type/sign checks, nonnegative after
balance and a derived before-balance integer-range check. Product and users.id FKs restrict
deletion; business references are polymorphic and cannot use one FK to several tables.
Actor must come from authenticated trusted orchestration, not client-supplied identity.
Reference nullability for manual adjustments and nonblank reason remain proposals.

History is append-only by service ownership: correct mistakes with new compensating movements.
SQL constraints alone do not enforce cross-row continuity, current-balance equality, actor
permissions or append-only privileges. These require implementation/privilege design and tests.
Use created_at DESC, id DESC for stable display ordering, not claimed global commit ordering.
Reconcile per-Product delta sums/last after-balance with stock; sequence gaps are legitimate.

Row locks do NOT deduplicate requests. A reference can span Products, duplicate lines and
partial receipts/returns, so do not add unique reference pairs or Product/reference triples.
An approved operation/line key plus atomic deduplication record and result replay in the
same transaction is an activation gate. Same key with changed payload must fail; identical
replay returns prior result. Extra schema/API is TBD and deliberately omitted. No automatic
retry after ambiguous commit outcome until this is resolved; duplicate protection is not claimed.

### Inactive V6 artifact and safe activation

Review [commented V6 draft](../migrations/V6__create_inventory_stock_and_stock_movements.sql.draft).
Every statement is commented; it is NOT EXECUTABLE / NOT APPROVED, outside active Flyway
locations and uses .sql.draft. It has not been run or database-validated.

1. Members 1/2 approve storage, initialization/backfill, actors, threshold ownership,
   retention/permissions, Product-active policy, V6 version/final filename and idempotency.
2. Member 5 agrees enum consolidation, metadata-bearing calls versus four-argument void
   methods, restoreStock/void semantics, mandatory Inventory dependency, cost source and
   real Product IDs. Sales V5_1 still lacks base Sales schema in the inspected snapshot.
3. Integrate V2 through approved workflow. Verify products.id/users.id prerequisites;
   do not rewrite applied migrations. Missing version numbers alone are not table dependencies.
4. Implement approved repositories/mappings/service in a separately authorized step.
5. Author a real reviewed migration in active db/migration only after approval; do not
   simply rename this commented artifact. Coordinate backfill/deployment and backup policy.
6. Validate fresh/existing isolated PostgreSQL schemas, FK/sign/range constraints, initialization
   races, overselling, transaction rollback, deduplication, null/equality low-stock cases,
   missing rows and retained history. This task runs no database migrations or tests.
