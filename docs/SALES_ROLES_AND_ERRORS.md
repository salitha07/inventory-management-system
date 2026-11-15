# Bajaj Spare Parts Inventory & Sales Management System
## Sales Management & POS: Roles, Permissions & Error Handling

**Module:** Sales Management & Point of Sale (POS) — Member 5  
**System:** Bajaj Spare Parts Inventory & Sales Management System  
**Technology Stack:** React + Vite, Java 21 + Spring Boot, PostgreSQL + Flyway  
**Package Root:** `com.bajaj.inventory_backend`  
**Authentication Standard:** Spring Security + Stateless JWT (`Bearer <token>`)  
**File Location:** `docs/SALES_ROLES_AND_ERRORS.md`

---

## 1. System Role Definitions & Coordination with Member 1

The system roles are strictly governed by **Member 1's Authentication & User Management module** in `com.bajaj.inventory_backend.entity.RoleName`:

```java
public enum RoleName {
    ADMIN,
    MANAGER,
    CASHIER,
    INVENTORY_STAFF
}
```

Spring Security maps these enum values to granted authorities with the `ROLE_` prefix:
* `ROLE_ADMIN`
* `ROLE_MANAGER`
* `ROLE_CASHIER`
* `ROLE_INVENTORY_STAFF`

> [!IMPORTANT]
> The Sales module **does not invent custom or unsupported roles**. All endpoint security constraints are strictly mapped to Member 1's predefined authorities.

---

## 2. Sales Access Permissions & RBAC Matrix

The table below defines the Role-Based Access Control (RBAC) matrix for all Sales and POS endpoints:

| Endpoint Path | HTTP Method | Permitted Authorities | Purpose & Description |
| :--- | :---: | :--- | :--- |
| `/api/v1/products/search` | `GET` | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN`, `ROLE_INVENTORY_STAFF` | Catalog search and barcode lookup for parts. |
| `/api/v1/sales` | `POST` | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Perform sale checkout and finalize invoice. |
| `/api/v1/sales/{id}` | `GET` | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | View full details of a specific sale invoice. |
| `/api/v1/sales` | `GET` | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | View paginated sales history and audit lists. |
| `/api/v1/sales/{id}/receipt` | `GET` | `ROLE_CASHIER`, `ROLE_MANAGER`, `ROLE_ADMIN` | Retrieve formatted receipt for thermal printing. |
| `/api/v1/sales/{id}/status` | `PATCH` | `ROLE_MANAGER`, `ROLE_ADMIN` *(Cashier Blocked)* | Void or cancel a completed invoice (`COMPLETED -> CANCELLED`). |

* **`ROLE_CASHIER`:** Dedicated counter sales operator. Has access to search, cart checkout, receipt printing, and sales history viewing. Cannot cancel finalized invoices or grant excessive discounts.
* **`ROLE_MANAGER`:** Store supervisor. Has full sales operational access plus supervisor override authority (voiding completed sales, authorizing discounts > 5%, overriding customer credit limits).
* **`ROLE_ADMIN`:** Full administrative system control across all operational modules.
* **`ROLE_INVENTORY_STAFF`:** Warehouse / stockroom operator. Permitted to search products, but **strictly forbidden** from executing POS checkout (`POST /api/v1/sales`) or voiding sales.

---

## 3. Checkout Authorization & Supervisor Overrides

During counter checkout (`POST /api/v1/sales`), authorization is verified across two tiers:

### 1. Standard Cashier Authorization
* Any authenticated user with `ROLE_CASHIER`, `ROLE_MANAGER`, or `ROLE_ADMIN` can execute checkout.
* The cashier identity is bound automatically to `created_by` using the JWT subject (`SecurityContextHolder`).
* Permitted tender types: `CASH`, `CARD`, `UPI`, `BANK_TRANSFER`.
* For `CREDIT` tender, the customer must be registered (`customerId != null`) and have an active credit limit.

### 2. Supervisor / Manager Override Requirements
Certain high-risk checkout actions exceed cashier standard authority and require **Store Manager** or **Admin** credentials:
1. **High Discounts:** Header discounts exceeding the standard cashier policy threshold (e.g., > 5% of subtotal).
2. **Credit Limit Breach:** A credit checkout where $\text{Current Balance} + \text{Sale Total} > \text{Customer Credit Limit}$.
3. **Same-Day Invoice Voiding (`COMPLETED -> CANCELLED`):** Cashiers cannot void finalized invoices once printed.

**Override Mechanism:**
The client sends the supervisor authorization credential via the custom request header:
```http
X-Manager-Override-PIN: <manager-pin-or-token>
```
If the header is missing or contains invalid supervisor credentials when an override condition is met, the backend rejects the transaction with `403 Forbidden`.

---

## 4. Standard Error Response Structure

All error responses across the backend conform to the standard Spring Boot / RFC 7807 structure:

```json
{
  "timestamp": "2026-11-13T14:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Specific human-readable error description",
  "path": "/api/v1/sales"
}
```

For validation failures containing multiple field violations:
```json
{
  "timestamp": "2026-11-13T14:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for 2 field(s)",
  "validationErrors": [
    {
      "field": "items[0].quantity",
      "rejectedValue": 0,
      "message": "Quantity must be at least 1"
    }
  ],
  "path": "/api/v1/sales"
}
```

---

## 5. Detailed Error Catalog & Scenarios

### 1. Invalid Checkout Data (`400 Bad Request`)
* **Trigger:** Malformed request body, missing mandatory fields, negative discounts, or cash tender less than grand total.
* **Status Code:** `400 Bad Request`
* **Response Body Example:**
```json
{
  "timestamp": "2026-11-13T14:31:10Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Amount tendered (500.00) is less than the required total payable amount (870.00).",
  "path": "/api/v1/sales"
}
```

---

### 2. Product Not Found (`404 Not Found` / `400 Bad Request`)
* **Trigger:**
  * During checkout: A submitted `productId` does not exist in the database, or refers to a discontinued/inactive part (`is_active = false`).
  * During detail retrieval: The requested `saleId` does not exist.
* **Status Code:** `404 Not Found` (or `400 Bad Request` during checkout validation)
* **Response Body Example:**
```json
{
  "timestamp": "2026-11-13T14:32:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Product with ID 999 was not found or is discontinued in the active parts catalog.",
  "path": "/api/v1/sales"
}
```

---

### 3. Insufficient Stock (`409 Conflict`)
* **Trigger:** A cashier attempts to purchase a quantity greater than currently available in inventory, or a concurrent cashier sold the remaining stock milliseconds earlier.
* **Mechanism:** Member 6's `InventoryService.deductStock(...)` fails the atomic row lock condition and throws `InsufficientStockException`. The database transaction rolls back completely.
* **Status Code:** `409 Conflict`
* **Response Body Example:**
```json
{
  "timestamp": "2026-11-13T14:33:15Z",
  "status": 409,
  "error": "Conflict",
  "message": "Insufficient stock for product ID 101 ('Clutch Plate Assembly'). Requested: 5, Available: 2.",
  "path": "/api/v1/sales"
}
```

---

### 4. Invalid Quantity (`400 Bad Request`)
* **Trigger:** Quantity is zero, negative, or violates integer precision rules.
* **Validation Annotation:** `@Min(value = 1, message = "Quantity must be at least 1")`.
* **Status Code:** `400 Bad Request`
* **Response Body Example:**
```json
{
  "timestamp": "2026-11-13T14:34:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for 1 field(s)",
  "validationErrors": [
    {
      "field": "items[0].quantity",
      "rejectedValue": -2,
      "message": "Quantity must be at least 1"
    }
  ],
  "path": "/api/v1/sales"
}
```

---

### 5. Unauthorized Access (`401 Unauthorized`)
* **Trigger:**
  * Missing `Authorization: Bearer <token>` header.
  * Expired JWT token.
  * Malformed token or invalid signature.
* **Mechanism:** Caught by `JwtAuthenticationFilter` and Spring Security `AuthenticationEntryPoint`.
* **Status Code:** `401 Unauthorized`
* **Response Body Example:**
```json
{
  "timestamp": "2026-11-13T14:35:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource. JWT token is missing or expired.",
  "path": "/api/v1/sales"
}
```

---

### 6. Forbidden Access (`403 Forbidden`)
* **Trigger:**
  * An authenticated user with `ROLE_INVENTORY_STAFF` attempts to checkout a sale (`POST /api/v1/sales`).
  * A `ROLE_CASHIER` attempts to void a completed invoice (`PATCH /api/v1/sales/{id}/status`).
  * A cashier attempts to apply a > 5% discount without a valid manager override PIN.
  * A cashier attempts a `CREDIT` payment on an anonymous walk-in sale (`customerId = null`).
* **Status Code:** `403 Forbidden`
* **Response Body Example:**
```json
{
  "timestamp": "2026-11-13T14:36:20Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access Denied: User with role 'INVENTORY_STAFF' is not authorized to execute sales transactions.",
  "path": "/api/v1/sales"
}
```

---

### 7. Checkout Failure & System Error (`500 Internal Server Error`)
* **Trigger:** Unexpected database connection failure, unhandled runtime exception, or unexpected transaction deadlock during commit.
* **Mechanism:** Spring Boot `@Transactional` guarantees that if an unhandled exception occurs, the transaction automatically rolls back. Zero orphaned rows are written to `sales` or `sale_items`, and inventory counts remain untouched.
* **Status Code:** `500 Internal Server Error`
* **Response Body Example:**
```json
{
  "timestamp": "2026-11-13T14:37:45Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "An unexpected error occurred while processing checkout. The transaction was rolled back safely. Please retry.",
  "path": "/api/v1/sales"
}
```

---

## 6. Document Cross-References

* **API Examples:** [docs/CHECKOUT_API_EXAMPLES.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/CHECKOUT_API_EXAMPLES.md)
* **API Contract Specification:** [docs/POS_API_CONTRACT.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/POS_API_CONTRACT.md)
* **Sale Header Field Definitions:** [docs/SALE_HEADER_FIELDS.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALE_HEADER_FIELDS.md)
* **Sales Workflow Specification:** [docs/SALES_WORKFLOW.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALES_WORKFLOW.md)

