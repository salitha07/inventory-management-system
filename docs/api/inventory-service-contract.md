# Inventory internal service contract

**DRAFT — PROPOSED contracts only; not approved, compiled or implemented.**

Prepared 2026-11-16 for Member 6 Phase 01. See the
[team/dependency draft](../team/member-6-inventory-phase-01.md) for verified snapshots,
storage alternatives, V6 outline and coordination questions. All signatures, DTO names,
defaults and policies below are PROPOSED or TBD unless identified as assignment requirements
or verified teammate code. No Java interface or HTTP endpoint is created by this document.

## Required movement vocabulary and data

| Required type | Intended balance effect |
| --- | --- |
| PURCHASE_RECEIVE | Positive change for received purchases |
| SALE_DEDUCT | Negative change for completed sale deductions |
| CUSTOMER_RETURN | Positive change for returned items accepted back into stock |
| MANUAL_ADJUSTMENT | Positive or negative correction; resulting balance never negative |

Required movement data: Product, quantity change, quantity after movement, reference
type, reference ID, reason and created by. PROPOSED representation:

| Attribute | Proposed representation / rules |
| --- | --- |
| product | Long productId, referencing Member 2's products.id |
| quantityChange | Signed Integer; never zero; computed from operation, not blindly accepted |
| quantityAfter | Integer >= 0; computed from authoritative locked/atomically updated balance |
| referenceType | String from an agreed vocabulary; PURCHASE/SALE/CUSTOMER_RETURN/MANUAL are examples, not approved values |
| referenceId | Long business-document ID; manual reference policy TBD |
| reason | Nonblank String PROPOSED for every write; maximum length TBD |
| createdBy | Long trusted user ID referencing users.id; actor sourcing TBD with Member 1 |

PROPOSED supporting attributes: generated movementId and Instant createdAt. Store the
required movement names as strings. Never accept client-supplied quantityAfter or timestamps
as authoritative. Previous quantity and reference number from Sales documentation are TBD,
not requirements adopted here. Product/user deletion and historical retention policy are TBD.

## Proposed mutation signatures

The following is illustrative contract notation, not an additional interface. Parameter and
result types require team approval. Commands intentionally choose the movement kind through
the operation, avoiding an arbitrary caller-supplied kind with contradictory sign.

```java
StockMovementResult receiveStock(StockChangeCommand command);
StockMovementResult deductStock(StockChangeCommand command);
StockMovementResult returnStock(StockChangeCommand command);
StockMovementResult manualAdjustment(ManualAdjustmentCommand command);
```

PROPOSED StockChangeCommand fields: `Long productId`, `Integer quantity` (> 0),
`MovementContext context`. The service converts positive input into the appropriate signed
delta. receiveStock means purchase receipt; deductStock means sale deduction; returnStock
means customer return accepted into stock. Other business cases require explicit agreement.

PROPOSED ManualAdjustmentCommand fields: `Long productId`, `Integer quantityChange`
(signed, nonzero), `MovementContext context`. A signed delta is proposed rather than a
target quantity; this remains TBD. A reason is mandatory under this proposal.

PROPOSED MovementContext fields: `String referenceType`, `Long referenceId`, `String reason`,
`Long actorUserId`. Actor ID must be supplied only by trusted internal orchestration after
authorization; HTTP clients must not choose it. Alternative: resolve actor from authenticated
context inside Inventory. Background-job/system actors and principal/actor mismatch behavior
remain TBD. Do not silently invent an actor when context is absent.

PROPOSED StockMovementResult contains movement ID, product ID, movement type, signed delta,
quantity after, reference type/id, reason, created-by ID and creation instant.

For each command: reject null IDs/quantities, invalid sign, overflow, invalid metadata,
missing Product and insufficient stock before committing any change. Inactive Product handling
is TBD: a return might need different rules from a sale. Business source-document validation
and prevention of excessive returns/duplicate receipts must be allocated to the caller or
Inventory explicitly; this draft does not claim those checks exist.

PROPOSED error outcomes: validation failure, Product not found, insufficient stock, invalid
actor, and concurrency/lock failure. Exact exception types and HTTP mappings are TBD.
Reuse/reconcile Sales's shared exception proposals; do not create duplicate classes.

## Proposed read/query signatures

```java
CurrentStockView getCurrentStock(Long productId);
Page<CurrentStockView> searchCurrentStock(CurrentStockQuery query, Pageable page);
Page<StockMovementView> searchStockMovements(StockMovementQuery query, Pageable page);
```

`Page`/`Pageable` refer to Spring Data concepts for this proposal; whether public internal
contracts expose them directly or use module-owned page DTOs is TBD.

PROPOSED CurrentStockQuery:

- Optional text: case-insensitive Product code/name substring search.
- Optional categoryId and active filter; default active-only is PROPOSED.
- Optional lowStockOnly flag: `quantity <= reorderLevel` is the current PROPOSED rule.
- Reorder null/default behavior and Products without a balance row are TBD; do not silently
  present missing persistence as zero without an approved initialization policy.
- Stable default sort by productCode then productId, zero-based pages, proposed size 20/max 100.

PROPOSED CurrentStockView: productId, productCode, productName, categoryId, active,
quantity, reorderLevel and lowStock. Quantity/reorder types and storage await Member 2.

PROPOSED StockMovementQuery: optional productId, movementType, referenceType,
referenceId, createdBy, from and to timestamps. Time range is proposed as inclusive-from,
exclusive-to in UTC instants. Default ordering: createdAt descending then movementId
descending; page defaults as above. StockMovementView contains the required movement
attributes and supporting ID/time. Authorization and visibility of actor information are TBD.

These reads are snapshots only. A successful availability query does not reserve stock or
guarantee that a subsequent deduction succeeds. No HTTP paths or React changes are approved.

## Comparison with Member 5's verified interface

Source: local `origin/feature/sales-management` at 2f6832d,
`backend/src/main/java/com/bajaj/inventory_backend/inventory/service/InventoryService.java`.

| Existing declaration | Relationship to this proposal / decision |
| --- | --- |
| boolean isStockAvailable(Long, Integer) | Could remain a convenience read; never a reservation. Retention TBD |
| Integer getAvailableStock(Long) | Could project getCurrentStock.quantity; missing/inactive policy TBD |
| void validateStockAvailability(Long, Integer) | Could remain fail-fast validation; authoritative validation must still occur during mutation |
| void deductStock(Long, Integer, StockMovementType, Long) | Existing Sales caller depends on it; command proposal is incompatible without coordinated caller changes or an approved adapter |
| BigDecimal getCurrentUnitCost(Long) | Outside stock mutation core; owner/source TBD with Members 2/5; Product.costPrice is only a candidate |
| void restoreStock(Long, Integer, StockMovementType, Long) | Proposal separates customer return; sale void semantics and adapter retention TBD |
| receiveStock / manualAdjustment | No equivalent declared in existing interface |

Existing enum path: `backend/src/main/java/com/bajaj/inventory_backend/inventory/entity/StockMovementType.java`.

| Existing name | Required candidate replacement — mapping PROPOSED |
| --- | --- |
| PURCHASE | PURCHASE_RECEIVE |
| SALE | SALE_DEDUCT |
| RETURN | CUSTOMER_RETURN; only if the event is genuinely a customer return |
| ADJUSTMENT | MANUAL_ADJUSTMENT; only for manual corrections |

Do not blindly classify sale voiding as CUSTOMER_RETURN. Four-argument mutation methods
cannot explicitly carry reason, reference type and actor; any adapter must define trusted
metadata sourcing without fabricating it. Return type changes also require coordination.
Keep one canonical interface and enum; no duplicate interfaces or legacy aliases are approved.

SalesService currently calls `deductStock(productId, quantity, SALE, saleId)` after saving
the sale, reads unit cost, and performs a preliminary availability check. It sorts deductions
by Product ID and uses REQUIRED/READ_COMMITTED transactions. Inventory injection is optional
and checkout skips stock operations when absent; Member 5 must resolve this before integration.
The seeded Product catalogue must also be replaced/mapped to real database IDs.

Sales's `docs/SALES_INVENTORY_INTEGRATION.md` proposes quantity/previous_quantity/new_quantity,
reference_number and actor metadata. Field naming and extra attributes require explicit
reconciliation with quantityChange/quantityAfter/referenceType/reason in this draft.

## Integrity requirements and proposed mechanism

Required invariants: only Inventory changes balances; balances never become negative;
balance and movement commit or roll back together; concurrent checkouts cannot oversell.
These are requirements, not functionality established by this draft.

PROPOSED write sequence:

1. Enter a write transaction joining the caller transaction when present (REQUIRED is proposed).
2. Resolve trusted actor, Product and reference metadata; validate the command.
3. Acquire an agreed database write lock on the authoritative balance row, or use a
   conditional atomic update with correct affected-row checking and after-balance retrieval.
4. Validate current balance, sign and arithmetic bounds; update exactly one balance.
5. Append the matching immutable movement with the actual resulting quantity.
6. Commit both together; propagate failures so the caller's sale/purchase also rolls back.

Do not use an independent REQUIRES_NEW stock transaction. READ_COMMITTED alone does not
prevent read-check-write races. A pessimistic row lock is a PROPOSED simple strategy;
optimistic versioning with bounded whole-transaction retry is an alternative, not selected.
Application-local Java locks do not protect multiple application instances.

PROPOSED database nonnegative constraints provide a second line of defense. All balance
writers must use the same strategy. Multi-Product operations acquire locks in ascending
Product ID order. Initial balance-row creation needs its own uniqueness/race strategy if
balances are separate. Lock timeouts/deadlocks must fail safely; retry policy is TBD.

Movement writes are append-only through Inventory. Other modules must not save modified
Product quantities, directly update balance tables or create ledger rows. This is a team
ownership rule; database privilege enforcement is a separate TBD decision.

Retry/idempotency policy is TBD: do not add a unique `(referenceType, referenceId)` constraint
that would incorrectly prohibit legitimate multi-line or partial events. Agree an operation/
line identity before claiming duplicate-request protection.

## Review gates and validation — PROPOSED

Approval gates: Member 1 package/security/phase scope and V6; Member 2 storage and lifecycle;
Member 5 single interface, enum, transaction and return/void behavior. The storage choice is
**PENDING MEMBER 2 / TEAM APPROVAL**. See the companion draft for schema alternatives.

When implementation is authorized, verify positive/negative movement semantics, invalid
inputs, exact depletion, insufficient stock, actor/reference metadata, overflow, rollback
on movement-save failure, caller rollback, initial-row races and concurrent PostgreSQL
checkouts in independent transactions. Also verify query boundaries, deterministic history
ordering, migration compatibility and Hibernate validation. In-memory Sales mocks do not
prove database locks or rollback. No tests or migrations were run for this documentation task.


## Persistence addendum — PROPOSED / NOT APPROVED

This addendum narrows the earlier TBD query/storage policies without approving shared
architecture. **PENDING MEMBER 2 / TEAM APPROVAL**: inventory_stock is the sole balance,
keyed by products.id, with quantity, nullable reorder_level and updated_at. Product has no
second balance. See the [team design](../team/member-6-inventory-phase-01.md) for lifecycle,
concurrency and activation gates, and the [inactive V6 draft](../migrations/V6__create_inventory_stock_and_stock_movements.sql.draft).

Initializer signature TBD: synchronously create zero/null stock after Product persistence
inside the caller transaction, using conflict-safe insert and never overwriting existing rows.
Normal mutations reject uninitialized stock, separately from missing Product or insufficient
stock. Zero initialization is not a movement; nonzero opening stock requires an attributed
approved adjustment/import process. Active Product races require Member 2 locking agreement.

Low stock is PROPOSED as quantity <= configured reorderLevel, including equality and 0/0.
Null threshold means unknown/unconfigured and is excluded from low-stock filtering. Missing
stock means STOCK_NOT_INITIALIZED, not zero. Detail reads error explicitly; search left-joins
to identify missing rows separately. CurrentStockView cannot represent missing quantity or
explicit status, so approved response adaptation remains deferred; Java is unchanged here.

PROPOSED writes lock stock rows FOR UPDATE in ascending Product order, validate current
quantity/overflow, update quantity/updated_at and insert the exact signed movement in one
REQUIRED transaction. Return inside an outer transaction does not imply durable commit.
Any failure rolls back caller records and both inventory writes. Conditional update with
RETURNING is an alternative, not selected. PostgreSQL row locks, not preliminary reads,
provide serialization. Timeout/deadlock retry and ambiguous commit behavior require approval.

createdBy references users.id resolved by trusted authenticated orchestration; the existing
AuthenticatedActor value object does not prove authentication. Threshold edits require an
agreed permission/audit policy; they must not fabricate quantity movements. Restrictive FKs
preserve Product/user history. SQL checks do not enforce ledger continuity, balance equality,
append-only permissions, source-document validation or authorization by themselves.

Request idempotency remains an activation gate: agree an operation/line key, payload-match
validation and atomic result replay before implementing deduplication schema or retries.
Generic reference pairs/triples are not unique because partial and multi-line events are valid.
No duplicate-request safety is claimed by this draft or current foundation.

Sales conflict remains explicit: SALE/RETURN/ADJUSTMENT/PURCHASE names, four-argument void
mutations, restoreStock including voids, absent reason/referenceType/actor parameters, optional
Inventory injection and undecided cost source. No shared interface, adapter or enum replacement
is introduced. No persistence behavior described here has been implemented or exercised.

## Implemented database-independent core follow-up

`inventory/core/InventoryOperations.java` now implements receiveStock, deductStock,
returnStock and signed manualAdjustment using the existing validated commands and MovementKind.
This is a plain Java object, not a Spring bean or Member 5's shared InventoryService.
`inventory/core/InventoryPorts.java` owns two provisional internal ports:

- AtomicStockStore loads an existing protected balance and invokes a pure write planner,
  then atomically persists the resulting balance and movement. A combined port deliberately
  prevents the core from sequencing independent repository commits. Failures must propagate
  and roll back both writes; a joined outer transaction must be marked rollback-only.
- OperationPolicy verifies trusted actor identity/authorization and approved, restockable
  customer-return eligibility/quantity. Implementations must throw on rejection. Source-return
  eligibility must be serialized in the same transaction when concurrent returns are possible.

MovementWrite is an immutable validated plan without a generated movement ID; only the store
returns a persisted StockMovementView. The core uses injected Clock, preserves metadata,
rejects insufficient stock/negative balances/overflow and creates no state changes itself.
Reference vocabularies remain team-owned: nonblank type and required positive business IDs
are validated, but no unapproved PURCHASE/SALE/RETURN string enum is imposed.

The test-only store stages all changes until successful completion. Eight new operation tests
plus five foundation tests passed under Java 21 Maven; full main-source compilation passed.
Tests cover operations, metadata, eligibility, actor rejection, missing stock, invalid input,
overflow, exact depletion and simulated balance/movement/commit failure without partial writes.
They prove core behavior against the port contract, not actual database atomicity or locking.

No production policy/store adapter is supplied. PostgreSQL row locks, caller transaction
rollback, real principal verification, return-source validation, initialization, history/search,
idempotency and V6 remain pending. Product/Sales contracts and the dedicated storage direction
remain provisional. No no-op success implementation or startup dependency was added.
