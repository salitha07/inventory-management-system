
# Purchase Management API Contract

## 1. Overview

The Purchase Management module handles supplier purchases,
purchase items, purchase calculations, purchase searching,
purchase cancellation and purchase receiving.

Base URL: `/api/v1/purchases`

Responsible Member: Member 4

## 2. Implementation Status

| Component | Status |
|---|---|
| Purchase database migration | Implemented |
| Purchase entities | Implemented |
| Purchase repositories | Implemented |
| Purchase DTO validation | Implemented |
| Purchase creation | Implemented |
| Purchase listing | Implemented |
| Purchase searching | Implemented |
| Purchase cancellation | Implemented |
| Supplier validation | Implemented |
| Product validation | Implemented |
| Monetary validation | Implemented |
| Exception handling | Implemented |
| Concurrency protection | Implemented |
| Role-based authorization | Implemented and tested |
| Purchase receiving | Implemented and tested |
| Stock movement integration | Verified in isolated PostgreSQL |
| Unit testing | Passed (24/24) |
| PostgreSQL integration testing | Passed (6/6) |
| REST API and authorization testing | Passed (26/26) |
| Production inventory activation | Pending team coordination |
| Full application startup verification | Pending |

## 3. API Endpoints

| Method | Endpoint | Success Status |
|---|---|---|
| GET | /api/v1/purchases | 200 OK |
| GET | /api/v1/purchases/search | 200 OK |
| GET | /api/v1/purchases/{id} | 200 OK |
| POST | /api/v1/purchases | 201 Created |
| POST | /api/v1/purchases/{id}/cancel | 200 OK |
| POST | /api/v1/purchases/{id}/receive | 200 OK |

All six endpoints are implemented.

Purchase receiving requires an available production
InventoryOperations bean and initialized product stock.

## 4. List Purchases

GET `/api/v1/purchases`

Returns all available purchase records.

Success response: HTTP 200 OK.

An empty result is returned as an empty JSON array.

## 5. Search Purchases

GET `/api/v1/purchases/search`

### Optional Query Parameters

| Parameter | Type | Description |
|---|---|---|
| purchaseNumber | String | Purchase number search |
| supplierId | Long | Supplier identifier |
| status | String | Purchase status |
| fromDate | Date | Starting purchase date |
| toDate | Date | Ending purchase date |

Date format: `YYYY-MM-DD`

Supported status values:

- DRAFT
- RECEIVED
- CANCELLED

Example:

GET /api/v1/purchases/search?status=DRAFT&supplierId=1

Success response: HTTP 200 OK.

Invalid date ranges, supplier identifiers or status values
return HTTP 400 Bad Request.

## 6. Get Purchase Details

GET `/api/v1/purchases/{id}`

Returns purchase information and its associated items.

Success response: HTTP 200 OK.

A missing purchase returns HTTP 404 Not Found.

An invalid purchase identifier returns HTTP 400 Bad Request.

## 7. Create Purchase

POST `/api/v1/purchases`

Creates a purchase containing one or more purchase items.

The initial purchase status is DRAFT.

Creating a purchase does not update inventory stock.

The authenticated active user is recorded as the purchase creator.

### Example Request

```json
{
  "supplierId": 1,
  "supplierInvoiceNumber": "INV-2026-001",
  "purchaseDate": "2026-11-25",
  "discount": 100.00,
  "items": [
    {
      "productId": 1,
      "quantity": 5,
      "unitCost": 250.00
    },
    {
      "productId": 2,
      "quantity": 3,
      "unitCost": 500.00
    }
  ]
}
```

### Expected Calculations

| Description | Amount |
|---|---:|
| Item 1 line total | 1250.00 |
| Item 2 line total | 1500.00 |
| Subtotal | 2750.00 |
| Discount | 100.00 |
| Total | 2650.00 |

Success response: HTTP 201 Created.

### Example Response

The following response contains illustrative generated identifiers.

```json
{
  "id": 1,
  "purchaseNumber": "PUR-A1B2C3D4",
  "supplierId": 1,
  "supplierInvoiceNumber": "INV-2026-001",
  "purchaseDate": "2026-11-25",
  "status": "DRAFT",
  "subtotal": 2750.00,
  "discount": 100.00,
  "total": 2650.00,
  "createdBy": 10,
  "items": [
    {
      "id": 1,
      "productId": 1,
      "quantity": 5,
      "unitCost": 250.00,
      "lineTotal": 1250.00
    },
    {
      "id": 2,
      "productId": 2,
      "quantity": 3,
      "unitCost": 500.00,
      "lineTotal": 1500.00
    }
  ]
}
```

## 8. Cancel Purchase

POST `/api/v1/purchases/{id}/cancel`

Cancels an existing DRAFT purchase.

The purchase status changes from DRAFT to CANCELLED.

RECEIVED and CANCELLED purchases cannot be cancelled.

Cancellation does not update inventory stock.

No request body is required.

Success response: HTTP 200 OK.

Invalid status transitions return HTTP 409 Conflict.

## 9. Receive Purchase

POST `/api/v1/purchases/{id}/receive`

Receives an existing DRAFT purchase and increases
the corresponding inventory stock quantities.

No request body is required.

The receiving operation:

1. Authenticates and authorizes the receiving user.
2. Resolves the active user account and verifies its role.
3. Locks the purchase record using pessimistic write locking.
4. Verifies that the purchase status is DRAFT.
5. Rejects purchases without items.
6. Checks that InventoryOperations is available.
7. Processes purchase items in product identifier order.
8. Receives stock through InventoryOperations.receiveStock().
9. Records PURCHASE_RECEIVE stock movements.
10. Changes the purchase status to RECEIVED.
11. Completes the operation within one database transaction.

If a stock operation fails, the receiving transaction
is rolled back.

Success response: HTTP 200 OK.

Duplicate receiving attempts return HTTP 409 Conflict.

Missing stock initialization returns HTTP 409 Conflict.

Unavailable InventoryOperations returns
HTTP 503 Service Unavailable.

The receiving implementation has been verified using
an isolated PostgreSQL database.

Production activation remains dependent on the approved
Inventory configuration and stock initialization lifecycle.

## 10. Purchase Status Lifecycle

Supported purchase statuses:

- DRAFT
- RECEIVED
- CANCELLED

Allowed status transitions:

- DRAFT -> RECEIVED
- DRAFT -> CANCELLED

RECEIVED and CANCELLED are terminal states for the
current purchase workflow.

Receiving or cancelling an already completed purchase
is not permitted.

## 11. Validation Rules

- Supplier identifiers must be positive.
- Product identifiers must be positive.
- Suppliers must exist and be active at purchase creation.
- Products must exist and be active at purchase creation.
- Purchase quantities must be greater than zero.
- Supplier invoice numbers must not be blank.
- Supplier invoice numbers must not exceed 100 characters.
- Purchase dates must not be null.
- Purchase item collections must not be empty or null.
- Individual purchase items must not be null.
- Unit costs must be nonnegative.
- Discounts must be nonnegative.
- Discounts must not exceed the purchase subtotal.
- Monetary values must support NUMERIC(12,2).
- Monetary values must not exceed 9999999999.99.
- Monetary values must not exceed two decimal places.
- Line totals and subtotals must not exceed database limits.
- Search date ranges must be valid.
- Purchase identifiers must be positive.
- Only DRAFT purchases can be received or cancelled.
- Inventory stock must be initialized before receiving.

Receipt-time product active-state policy remains
subject to team agreement.

## 12. Purchase Calculations

Line Total = Quantity x Unit Cost

Subtotal = Sum of all Line Totals

Total = Subtotal - Discount

All monetary calculations use Java BigDecimal.

Purchase calculations are performed by the backend.

Client-provided calculated totals are not required.

## 13. Authentication and Authorization

Purchase endpoints require authenticated users.

The currently implemented role-based access matrix is:

| Operation | ADMIN | MANAGER | INVENTORY_STAFF | CASHIER |
|---|---|---|---|---|
| List purchases | Yes | Yes | Yes | No |
| Search purchases | Yes | Yes | Yes | No |
| View purchase details | Yes | Yes | Yes | No |
| Create purchase | Yes | Yes | Yes | No |
| Cancel purchase | Yes | Yes | No | No |
| Receive purchase | Yes | Yes | No | No |

Role restrictions are declared using Spring Security
PreAuthorize annotations.

Method security is enabled through EnableMethodSecurity.

Purchase receiving additionally validates the active
persisted user and the matching authenticated authority.

The role restrictions have been tested using MockMvc
with the existing Spring Security configuration.

Final permissions must follow the approved team role matrix.

Production requests use the Authorization header:

Authorization: Bearer <JWT_TOKEN>

Real JWT signature validation and complete production
authentication flows require separate verification.

## 14. HTTP Response Codes

| HTTP Status | Description |
|---|---|
| 200 OK | Successful retrieval, cancellation or receiving |
| 201 Created | Purchase successfully created |
| 400 Bad Request | Invalid request or validation failure |
| 401 Unauthorized | Invalid or unavailable authenticated account |
| 403 Forbidden | Insufficient permissions or unauthenticated request rejected by the current security configuration |
| 404 Not Found | Purchase, supplier or product not found |
| 409 Conflict | Invalid purchase status transition or missing stock initialization |
| 503 Service Unavailable | Inventory receiving integration unavailable |

The current security configuration returns HTTP 403
for unauthenticated requests rejected by the security filter chain.

Shared authentication and exception response conventions
remain subject to team coordination.

## 15. Transaction Management

Purchase creation, cancellation and receiving use
transactional service methods.

Purchase cancellation and receiving use pessimistic
write locking to protect against concurrent status changes.

Purchase receiving and inventory updates participate
in the same database transaction.

Purchase items are processed in product identifier order
to maintain consistent inventory lock ordering.

A failure during receiving must not leave partially
updated stock balances or stock movement records.

PostgreSQL integration testing verified rollback when
a later product stock operation failed.

Simultaneous purchase receiving and cancellation stress
testing remains a separate verification activity.

## 16. Inventory Integration Rules

- Only the Inventory module manages stock balances.
- Creating a DRAFT purchase does not increase stock.
- Cancelling a DRAFT purchase does not increase stock.
- Stock increases only when a purchase is received.
- Purchase receiving is atomic.
- Stock movements contain the purchase reference.
- Stock movements identify the authenticated actor.
- Purchase receiving uses PURCHASE_RECEIVE movements.
- Purchase Management does not directly modify inventory tables.

### Inventory Service Contract

The receiving implementation uses:

InventoryOperations.receiveStock()

### Movement Metadata

| Field | Value |
|---|---|
| Movement type | PURCHASE_RECEIVE |
| Reference type | PURCHASE |
| Reference ID | Purchase identifier |
| Reason | Purchase receipt |
| Actor | Authenticated receiving user identifier |
| Quantity change | Positive purchased quantity |

The reference vocabulary and reason are implemented
values awaiting final team agreement.

### Integration Status

The Inventory implementation and V6 migration are
available in the shared codebase.

Purchase receiving has been verified using the real
Inventory JPA persistence adapters and PostgreSQL.

The following production dependencies remain pending:

- Production InventoryOperations bean registration.
- Approved purchase receiving authorization policy.
- Product stock initialization and backfill.
- Purchase reference metadata agreement.
- Receipt-time product active-state policy.
- Resolution of duplicate Flyway migration versions.

## 17. Testing and Verification

### Test Classes

| Test Class | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| PurchaseServiceTest | 13 | 0 | 0 | 0 |
| PurchaseReceivingServiceTest | 11 | 0 | 0 | 0 |
| PurchaseReceivingPostgresTest | 6 | 0 | 0 | 0 |
| PurchaseControllerTest | 26 | 0 | 0 | 0 |
| Total | 56 | 0 | 0 | 0 |

Verification date: 2026-11-25

Build status: SUCCESS

Results are combined from separate successful test runs.

### Purchase Service Unit Testing

- Successful purchase creation.
- Correct purchase calculations.
- Missing supplier rejection.
- Inactive supplier rejection.
- Missing product rejection.
- Inactive product rejection.
- Invalid discount rejection.
- Monetary overflow prevention.
- Invalid monetary precision rejection.
- Missing purchase handling.
- Invalid purchase identifier handling.
- Successful draft cancellation.
- Invalid cancellation prevention.
- Invalid search date range rejection.

### Purchase Receiving Unit Testing

- Authentication validation.
- Active account validation.
- Role authorization.
- Purchase identifier validation.
- Missing purchase handling.
- Invalid purchase status rejection.
- Empty purchase item rejection.
- Unavailable inventory integration handling.
- Successful receiving.
- Stock movement command verification.
- Inventory failure handling.

### PostgreSQL Integration Testing

Six integration tests verified:

1. Purchase status persistence.
2. Inventory stock quantity updates.
3. Purchase stock movement persistence.
4. Multiple product receiving.
5. Duplicate receiving prevention.
6. Transaction rollback after a later stock operation failure.

The tests used PostgreSQL 18.4, a disposable loopback
database, real JPA repositories and a shared transaction manager.

The test database contained the required schema from
V1, Catalog V2, V3, V4 and V6 migrations.

A test-only operation policy was used.

The production inventory database was not modified.

### Controller and Authorization Testing

MockMvc tests verified:

- Purchase listing.
- Purchase detail retrieval.
- Purchase searching.
- Purchase creation.
- Purchase cancellation.
- Purchase receiving.
- Role-based access restrictions.
- Unauthorized request rejection.
- Request body validation.
- Invalid quantity rejection.
- Inactive purchase creator rejection.
- HTTP error responses.
- Inventory service unavailable response.

Controller tests use mocked service dependencies
and simulated authenticated users.

### Run Unit and Controller Tests

From the backend directory on Windows:

```powershell
.\mvnw.cmd "-Dtest=PurchaseServiceTest,PurchaseReceivingServiceTest,PurchaseControllerTest" test
```

### Run PostgreSQL Integration Tests

Prepare a disposable PostgreSQL cluster with the required
schema and an Inventory fixture category.

Use the database and role inventory_jpa_test.

Set INVENTORY_TEST_POSTGRES_PORT to the temporary
server port.

```powershell
$env:INVENTORY_TEST_POSTGRES_PORT = "55432"

.\mvnw.cmd "-Dtest=PurchaseReceivingPostgresTest" test
```

The port above is an example and must match the
actual disposable PostgreSQL server.

Do not point these tests at a production database.

Stop the temporary PostgreSQL server after testing.

### Remaining Verification

- Full Spring Boot application startup.
- Full Flyway migration execution.
- Production InventoryOperations registration.
- Production JWT authentication.
- Approved production authorization policy.
- Product stock initialization lifecycle.
- Concurrent purchase operation stress testing.
- Full application end-to-end integration testing.

## 18. Module Ownership

Module: Purchase Management

Responsible Member: Member 4

Dependencies:

- Member 1 - Authentication and User Management
- Member 2 - Product Management
- Member 3 - Supplier Management
- Member 6 - Inventory and Stock Movements

## 19. Backend Handover

The Purchase Management backend implementation is
ready for team integration review.

The module provides six REST API endpoints, purchase
validation, monetary calculations, status management,
transactional receiving and inventory integration.

The implementation has passed 56 tests across unit,
PostgreSQL integration and MockMvc controller test runs.

Production deployment readiness remains dependent on
the resolution of shared application integration issues.

The Purchase Management module must be reviewed with
the Authentication, Product, Supplier and Inventory modules
before final system deployment.
