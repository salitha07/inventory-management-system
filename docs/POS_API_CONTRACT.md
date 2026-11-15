# Bajaj Spare Parts Inventory & Sales Management System
## POS & Sales Management REST API Contract Specification

**Module:** Sales Management & Point of Sale (POS) — Member 5  
**System:** Bajaj Spare Parts Inventory & Sales Management System  
**Technology Stack:** React + Vite, Java 21 + Spring Boot, PostgreSQL + Flyway  
**Package Root:** `com.bajaj.inventory_backend`  
**API Root Base:** `/api/v1`  
**Conventions:** JSON: `camelCase` | Path parameters & URLs: `kebab-case` | Headers: `Bearer <token>`  
**File Location:** `docs/POS_API_CONTRACT.md`

---

## 1. Architectural Standards & API Conventions

1. **Base URL:** All endpoints are rooted under `/api/v1`.
2. **Authentication:** All requests must include standard Bearer authorization in the HTTP request header:
   ```http
   Authorization: Bearer <jwt-token>
   ```
3. **Naming & Formats:**
   * JSON payloads utilize `camelCase` strictly.
   * Dates and timestamps utilize ISO-8601 strings (e.g., `2026-11-13T14:30:00Z`).
   * Monetary amounts are represented as decimal numbers with 2-decimal precision (e.g., `245.50`), serialized from Java `BigDecimal` to ensure precision.
4. **Error Response Structure:** Standard RFC 7807 problem details or unified application response:
   ```json
   {
     "timestamp": "2026-11-13T14:30:00Z",
     "status": 400,
     "error": "Bad Request",
     "message": "Insufficient stock for product ID 101. Requested: 5, Available: 2",
     "path": "/api/v1/sales"
   }
   ```

---

## 2. POS Endpoint Specifications

### Endpoint 1: Product Search & Barcode Lookup
* **HTTP Method:** `GET`
* **Path:** `/api/v1/products/search`
* **Producer:** Owned by **Member 2 (Products)**, consumed heavily by Member 5 POS client.
* **Purpose:** Enables high-speed counter lookup of spare parts by OEM part number, description, bike model, or barcode scanner.

#### Request Parameters (Query)
| Parameter | Type | Required | Default | Description |
| :--- | :--- | :---: | :---: | :--- |
| `q` | `String` | Yes | - | Search query: Bajaj part number (e.g. `DH101048`), description, or barcode. |
| `limit` | `Integer` | No | `20` | Maximum number of candidate items to return. |

#### Response (`200 OK`)
```json
[
  {
    "productId": 101,
    "partNumber": "DH101048",
    "name": "Clutch Plate Assembly",
    "description": "Friction disc set for Pulsar 150/180 DTS-i",
    "barcode": "8901234567890",
    "categoryName": "Transmission & Clutch",
    "sellingPrice": 220.00,
    "availableQuantity": 14,
    "rackLocation": "B-04-02",
    "active": true
  }
]
```

#### Status Codes
* `200 OK`: Successful search query.
* `400 Bad Request`: Search query parameter `q` is missing or blank.
* `401 Unauthorized`: Missing or expired JWT token.

---

### Endpoint 2: Sale Checkout (Create Sale)
* **HTTP Method:** `POST`
* **Path:** `/api/v1/sales`
* **Producer:** Member 5 (Sales Module)
* **Purpose:** Atomically commits a counter sale transaction, freezes unit cost snapshots, decrements inventory via `InventoryService`, and issues an invoice.

#### Optional Override Header
* `X-Manager-Override-PIN`: Optional header supplying manager credentials for discounts exceeding cashier policy or credit limit overrides.

#### Request Body (`SaleCreateRequest`)
```json
{
  "customerId": null,
  "paymentMethod": "CASH",
  "amountTendered": 500.00,
  "paymentReference": null,
  "discount": 0.00,
  "items": [
    {
      "productId": 101,
      "quantity": 2,
      "unitSellingPrice": 220.00
    }
  ]
}
```

#### Field Rules:
* `customerId`: `Long`, optional/nullable. Set to `null` for **Walk-in Customers**.
* `paymentMethod`: `String` enum (`CASH`, `CARD`, `UPI`, `BANK_TRANSFER`, `CREDIT`).
* `amountTendered`: `BigDecimal`, required if `paymentMethod == "CASH"`.
* `paymentReference`: `String`, optional; captures card machine authorization code, UPI UTR reference, or bank wire ID.
* `discount`: `BigDecimal`, optional (default `0.00`), cannot exceed subtotal.
* `items`: Array of line items; must contain at least 1 item; `quantity` must be $\ge 1$.

#### Response (`201 Created` - `SaleResponse`)
```json
{
  "id": 1001,
  "saleNumber": "INV-202611-0001",
  "customerId": null,
  "customerName": "Walk-in Customer",
  "saleDate": "2026-11-13T14:30:00Z",
  "status": "COMPLETED",
  "subtotal": 440.00,
  "discount": 0.00,
  "total": 440.00,
  "paymentMethod": "CASH",
  "amountTendered": 500.00,
  "changeDue": 60.00,
  "paymentReference": null,
  "createdById": 5,
  "createdByName": "Sunil Sharma",
  "items": [
    {
      "id": 5001,
      "productId": 101,
      "partNumber": "DH101048",
      "productName": "Clutch Plate Assembly",
      "quantity": 2,
      "unitSellingPrice": 220.00,
      "lineTotal": 440.00
    }
  ]
}
```

#### Status Codes
* `201 Created`: Transaction successfully committed and stock deducted.
* `400 Bad Request`: Payload validation error (e.g. negative amount, zero quantity, tendered amount less than total).
* `401 Unauthorized`: Invalid or missing cashier JWT token.
* `403 Forbidden`: Discount exceeds cashier threshold without manager override, or `CREDIT` attempted on a walk-in sale.
* `409 Conflict`: Insufficient on-hand inventory for one or more requested parts (triggers complete rollback).

---

### Endpoint 3: Sale Details Retrieval
* **HTTP Method:** `GET`
* **Path:** `/api/v1/sales/{id}`
* **Producer:** Member 5 (Sales Module)
* **Purpose:** Retrieves complete metadata and line-item details of a specific sale for viewing, audits, or returns processing.

#### Request Parameters
* Path variable: `id` (`Long`, required) — Primary key ID of the sale.

#### Response (`200 OK` - `SaleDetailResponse`)
```json
{
  "id": 1001,
  "saleNumber": "INV-202611-0001",
  "customerId": 42,
  "customerName": "Metro Auto Repairs",
  "customerPhone": "+91 9876543210",
  "saleDate": "2026-11-13T14:30:00Z",
  "status": "COMPLETED",
  "subtotal": 440.00,
  "discount": 20.00,
  "total": 420.00,
  "paymentMethod": "CREDIT",
  "amountTendered": null,
  "changeDue": null,
  "paymentReference": "PO-9921",
  "createdById": 5,
  "createdByName": "Sunil Sharma",
  "items": [
    {
      "id": 5001,
      "productId": 101,
      "partNumber": "DH101048",
      "productName": "Clutch Plate Assembly",
      "quantity": 2,
      "unitSellingPrice": 220.00,
      "unitCostSnapshot": 160.00,
      "lineTotal": 440.00
    }
  ]
}
```

#### Status Codes
* `200 OK`: Sale record successfully retrieved.
* `401 Unauthorized`: Missing or invalid JWT.
* `404 Not Found`: No sale found with the specified ID.

---

### Endpoint 4: Sales History (Paginated & Filtered)
* **HTTP Method:** `GET`
* **Path:** `/api/v1/sales`
* **Producer:** Member 5 (Sales Module)
* **Purpose:** Queries past sales transactions with multi-criteria filtering and server-side pagination.

#### Request Parameters (Query)
| Parameter | Type | Required | Default | Description |
| :--- | :--- | :---: | :---: | :--- |
| `page` | `Integer` | No | `0` | Zero-based page index. |
| `size` | `Integer` | No | `20` | Page size. |
| `sort` | `String` | No | `saleDate,desc` | Sort field and direction. |
| `startDate` | `String` | No | - | Filter sales from date/timestamp (ISO-8601). |
| `endDate` | `String` | No | - | Filter sales up to date/timestamp (ISO-8601). |
| `status` | `String` | No | - | Filter by status (`COMPLETED`, `CANCELLED`, `REFUNDED`). |
| `paymentMethod` | `String` | No | - | Filter by tender (`CASH`, `CARD`, `UPI`, `CREDIT`). |
| `customerId` | `Long` | No | - | Filter by customer ID (omitted or null for all). |
| `createdById` | `Long` | No | - | Filter by cashier ID for till audits. |
| `saleNumber` | `String` | No | - | Search by exact or prefix invoice reference. |

#### Response (`200 OK` - `Page<SaleSummaryResponse>`)
```json
{
  "content": [
    {
      "id": 1001,
      "saleNumber": "INV-202611-0001",
      "customerName": "Walk-in Customer",
      "saleDate": "2026-11-13T14:30:00Z",
      "status": "COMPLETED",
      "itemCount": 2,
      "total": 440.00,
      "paymentMethod": "CASH",
      "createdByName": "Sunil Sharma"
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

#### Status Codes
* `200 OK`: Paginated sales list returned successfully.
* `401 Unauthorized`: Unauthenticated request.

---

### Endpoint 5: Thermal Receipt Retrieval
* **HTTP Method:** `GET`
* **Path:** `/api/v1/sales/{id}/receipt`
* **Producer:** Member 5 (Sales Module)
* **Purpose:** Delivers structured, formatted receipt data optimized for 80 mm thermal POS receipt rendering and re-printing.

#### Request Parameters
* Path variable: `id` (`Long`, required) — Primary key ID of the sale.

#### Response (`200 OK` - `ReceiptResponse`)
```json
{
  "storeInfo": {
    "storeName": "Bajaj Genuine Spare Parts - Central Depot",
    "address": "45 Commercial Road, Automotive Hub",
    "phone": "+91 80 2345 6789",
    "gstin": "29AAAAA0000A1Z5"
  },
  "receiptInfo": {
    "saleNumber": "INV-202611-0001",
    "saleDate": "2026-11-13T14:30:00Z",
    "cashierName": "Sunil Sharma",
    "customerName": "Walk-in Customer",
    "customerPhone": null
  },
  "items": [
    {
      "partNumber": "DH101048",
      "description": "Clutch Plate Assembly",
      "quantity": 2,
      "unitPrice": 220.00,
      "lineTotal": 440.00
    }
  ],
  "totals": {
    "subtotal": 440.00,
    "discount": 0.00,
    "taxIncluded": 79.20,
    "grandTotal": 440.00
  },
  "payment": {
    "paymentMethod": "CASH",
    "amountTendered": 500.00,
    "changeDue": 60.00,
    "reference": null
  },
  "footerNote": "Thank you for buying Bajaj Genuine Parts! Goods once sold can be exchanged within 7 days in original condition."
}
```

#### Status Codes
* `200 OK`: Receipt data returned successfully.
* `404 Not Found`: Sale ID not found.

---

## 3. Allocation of Cart Operations: React vs Backend APIs

To achieve sub-second counter performance while ensuring financial integrity, cart responsibilities are strictly split between client-side state and backend transaction boundaries:

```
┌────────────────────────────────────────────────────────┐
│               REACT CLIENT-SIDE (UI)                   │
├────────────────────────────────────────────────────────┤
│ • In-Memory Cart State (Array of selected line items)   │
│ • Local Item Addition / Quantity Increment / Decrement │
│ • Instant Item Removal / Clear Cart Action             │
│ • Real-Time Client Total Preview (Subtotal - Discount) │
│ • Optimistic Stock Guard (Checks cart <= cached stock) │
│ • Real-Time Change Calculation (Tendered - Total)      │
│ • Barcode Scanner Input Buffer & Keyboard Traps        │
│ • Thermal Print Modal Trigger (window.print())         │
└───────────────────────────┬────────────────────────────┘
                            │
               POST /api/v1/sales (Checkout)
                            │
┌───────────────────────────▼────────────────────────────┐
│              SPRING BOOT BACKEND (SERVER)              │
├────────────────────────────────────────────────────────┤
│ • Discards Client Prices; Queries Authoritative DB     │
│ • Recalculates Line Totals & Grand Totals (BigDecimal) │
│ • Validates Customer Credit Limits (A/R Ledger)        │
│ • Enforces Manager Overrides on Large Discounts        │
│ • Generates Unique Sequential Invoice Numbers          │
│ • Captures & Freezes unit_cost_snapshot (COGS)         │
│ • Transactional Row-Level Lock & Stock Deduction       │
│ • Database Rollback on Insufficient Stock              │
│ • Persistent Ledger & Audit Trail Writing              │
└────────────────────────────────────────────────────────┘
```

---

## 4. Cross-Member API Contracts Requiring Confirmation

| Member / Module | Endpoint / Interface Under Agreement | Specific Confirmation Required |
| :--- | :--- | :--- |
| **Member 2** (Products) | `GET /api/v1/products/search?q={query}` | 1. Confirm that query searches across `part_number`, `barcode`, and `name`.<br>2. Confirm that discontinued products are excluded (`active = true`).<br>3. Confirm field naming: `sellingPrice` vs `mrp`. |
| **Member 6** (Inventory) | Java Interface: `InventoryService.deductStock(...)` | 1. Confirm method signature: `void deductStock(Long productId, Integer quantity, StockMovementType type, Long referenceId)`.<br>2. Confirm that service throws a runtime exception (`InsufficientStockException`) to trigger Spring rollback.<br>3. Confirm method to fetch current unit acquisition cost for `unit_cost_snapshot`. |
| **Member 10** (Customers) | `GET /api/v1/customers/{id}/credit-status` | 1. Confirm endpoint or service contract to check outstanding balance + credit limit.<br>2. Confirm walk-in agreement: `customer_id = null` requires no record creation in `customers` table. |
| **Member 1** (Auth) | JWT Claims & Header Override | 1. Confirm that `users.id` is available in JWT token claims (`userId`).<br>2. Confirm supervisor override protocol for discounts > 5% or sale voids (e.g. `X-Manager-Override-PIN` header vs manager approval endpoint). |

---

## 5. Document Cross-References

* **Sale Header Field Definitions:** [docs/SALE_HEADER_FIELDS.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALE_HEADER_FIELDS.md)
* **Sale Item Field Definitions:** [docs/SALE_ITEM_FIELDS.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALE_ITEM_FIELDS.md)
* **Sales Workflow Specification:** [docs/SALES_WORKFLOW.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALES_WORKFLOW.md)
* **Module Gap Analysis:** [docs/SALES_MODULE_ANALYSIS.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALES_MODULE_ANALYSIS.md)

