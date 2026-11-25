# Purchase Management API Contract

## 1. Overview

The Purchase Management module handles supplier purchases,
purchase items, purchase calculations, purchase searching,
purchase cancellation and purchase receiving.

Base URL: `/api/v1/purchases`

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
| Role-based authorization | Configured, pending verification |
| Purchase receiving | Pending inventory integration |
| Stock movement integration | Pending |
| Unit test execution | Passed (13/13) |
| PostgreSQL integration testing | Pending |

## 3. API Endpoints

| Method | Endpoint | Status |
|---|---|---|
| GET | /api/v1/purchases | Implemented |
| GET | /api/v1/purchases/search | Implemented |
| GET | /api/v1/purchases/{id} | Implemented |
| POST | /api/v1/purchases | Implemented |
| POST | /api/v1/purchases/{id}/cancel | Implemented |
| POST | /api/v1/purchases/{id}/receive | Pending |

## 4. List Purchases

GET `/api/v1/purchases`

Returns all available purchase records.

Success response: HTTP 200 OK.

## 5. Search Purchases

GET `/api/v1/purchases/search`

### Optional Query Parameters

| Parameter | Type | Description |
|---|---|---|
| purchaseNumber | String | Purchase number |
| supplierId | Long | Supplier identifier |
| status | String | Purchase status |
| fromDate | Date | Starting purchase date |
| toDate | Date | Ending purchase date |

Date format: `YYYY-MM-DD`

Example:

GET /api/v1/purchases/search?status=DRAFT&supplierId=1

Success response: HTTP 200 OK.

Invalid date ranges or supplier identifiers return
HTTP 400 Bad Request.

## 6. Get Purchase Details

GET `/api/v1/purchases/{id}`

Returns purchase information and its associated items.

Success response: HTTP 200 OK.

A missing purchase returns HTTP 404 Not Found.

## 7. Create Purchase

POST `/api/v1/purchases`

Creates a purchase containing one or more purchase items.

The initial purchase status is DRAFT.

Creating a purchase does not update inventory stock.

The authenticated user is recorded as the purchase creator.

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

## 8. Cancel Purchase

POST `/api/v1/purchases/{id}/cancel`

Cancels an existing DRAFT purchase.

The purchase status changes from DRAFT to CANCELLED.

RECEIVED and CANCELLED purchases cannot be cancelled.

Cancellation does not update inventory stock.

Success response: HTTP 200 OK.

Invalid status transitions return HTTP 409 Conflict.

## 9. Receive Purchase - Pending

POST `/api/v1/purchases/{id}/receive`

This endpoint is planned but not yet implemented.

The receiving operation will:

1. Authenticate and authorize the receiving user.
2. Lock the purchase record.
3. Verify that the purchase status is DRAFT.
4. Validate the purchase items.
5. Receive stock through InventoryOperations.receiveStock().
6. Record PURCHASE_RECEIVE stock movements.
7. Change the purchase status to RECEIVED.
8. Complete the operation within one database transaction.

If any stock operation fails, the entire receiving
transaction must be rolled back.

## 10. Purchase Status Lifecycle

Supported purchase statuses:

- DRAFT
- RECEIVED
- CANCELLED

Allowed status transitions:

- DRAFT -> RECEIVED (pending implementation)
- DRAFT -> CANCELLED

RECEIVED and CANCELLED are terminal states for the
current purchase workflow.

## 11. Validation Rules

- Supplier identifiers must be positive.
- Product identifiers must be positive.
- Suppliers must exist and be active.
- Products must exist and be active.
- Purchase quantities must be greater than zero.
- Supplier invoice numbers must not be blank.
- Supplier invoice numbers must not exceed 100 characters.
- Purchase dates must not be null.
- Purchase items must not be empty or null.
- Unit costs must be nonnegative.
- Discounts must be nonnegative.
- Discounts must not exceed the purchase subtotal.
- Monetary values must support NUMERIC(12,2).
- Monetary values must not exceed 9999999999.99.
- Monetary values must not exceed two decimal places.
- Line totals and subtotals must not exceed database limits.
- Search date ranges must be valid.
- Purchase identifiers must be positive.

## 12. Purchase Calculations

Line Total = Quantity x Unit Cost

Subtotal = Sum of all Line Totals

Total = Subtotal - Discount

All monetary calculations use Java BigDecimal.

Purchase calculations are performed by the backend.

## 13. Authentication and Authorization

Purchase endpoints require authenticated users.

The proposed role-based access matrix is:

| Operation | ADMIN | MANAGER | INVENTORY_STAFF | CASHIER |
|---|---|---|---|---|
| List purchases | Yes | Yes | Yes | No |
| Search purchases | Yes | Yes | Yes | No |
| View purchase details | Yes | Yes | Yes | No |
| Create purchase | Yes | Yes | Yes | No |
| Cancel purchase | Yes | Yes | No | No |
| Receive purchase | Pending | Pending | Pending | Pending |

Role restrictions are declared using Spring Security
PreAuthorize annotations.

Method security must be enabled using EnableMethodSecurity.

Final permissions must follow the approved team role matrix.

Runtime authorization testing remains pending.

## 14. HTTP Response Codes

| HTTP Status | Description |
|---|---|
| 200 OK | Successful retrieval or cancellation |
| 201 Created | Purchase successfully created |
| 400 Bad Request | Invalid request or validation failure |
| 401 Unauthorized | Authentication required |
| 403 Forbidden | Insufficient user permissions |
| 404 Not Found | Purchase, supplier or product not found |
| 409 Conflict | Invalid purchase status transition |

## 15. Transaction Management

Purchase creation and cancellation use transactional
service methods.

Purchase cancellation uses pessimistic write locking
to protect against concurrent status changes.

Purchase receiving must use the same locking strategy.

Purchase receiving and inventory updates must participate
in the same database transaction.

## 16. Inventory Integration Rules

- Only the Inventory module manages stock balances.
- Creating a DRAFT purchase must not increase stock.
- Cancelling a DRAFT purchase must not increase stock.
- Stock increases only when a purchase is received.
- Purchase receiving must be atomic.
- Stock movements must contain the purchase reference.
- Stock movements must identify the authenticated actor.
- Purchase receiving must use PURCHASE_RECEIVE movements.
- Purchase Management must not directly modify inventory tables.

The planned integration uses:

InventoryOperations.receiveStock()

The following dependencies remain pending:

- Production InventoryOperations bean registration.
- Approved purchase receiving authorization policy.
- Product stock initialization and backfill.
- Purchase reference metadata agreement.
- Inventory branch integration into the shared codebase.
- Resolution of duplicate Flyway migration versions.

## 17. Testing and Verification

Purchase service unit test coverage is intended to include:

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

Unit test execution results must be verified before
marking the tests as passed.

Additional verification required:

- Full Spring Boot application startup.
- Flyway migration execution.
- PostgreSQL integration testing.
- HTTP endpoint testing.
- Role-based authorization testing.
- Concurrent purchase operation testing.
- Purchase receiving integration testing.
- Stock movement and transaction rollback testing.

### Unit Test Results

- Total tests: 13
- Passed: 13
- Failures: 0
- Errors: 0
- Skipped: 0
- Build status: SUCCESS
- Verification date: 2026-11-25 

## 18. Module Ownership

Module: Purchase Management

Responsible Member: Member 4

Dependencies:

- Member 1 - Authentication and User Management
- Member 2 - Product Management
- Member 3 - Supplier Management
- Member 6 - Inventory and Stock Movements