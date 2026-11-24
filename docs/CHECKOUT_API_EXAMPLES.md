# Bajaj Spare Parts Inventory & Sales Management System
## POS Checkout API Request & Response Examples

**Module:** Sales Management & Point of Sale (POS) — Member 5  
**System:** Bajaj Spare Parts Inventory & Sales Management System  
**Technology Stack:** React + Vite, Java 21 + Spring Boot, PostgreSQL + Flyway  
**Package Root:** `com.bajaj.inventory_backend`  
**Endpoint:** `POST /api/v1/sales`  
**Conventions:** JSON: `camelCase` | Monetary Values: Two-decimal numeric scalars (`NUMERIC(12,2)`)  
**File Location:** `docs/CHECKOUT_API_EXAMPLES.md`

---

## 1. Client-Submitted vs. Server-Calculated Fields

Under the zero-trust architecture, the client submits intent and inputs, while the server authoritatively validates, queries master records, calculates financial totals, and assigns identifiers.

| Field Name | Submitted by Client? | Calculated / Assigned by Server? | Responsibility & Authority Notes |
| :--- | :---: | :---: | :--- |
| `customerId` | **Yes** | No | Optional. Set to `null` for anonymous walk-in retail counter buyers; set to positive `Long` for registered workshops. |
| `paymentMethod` | **Yes** | No | Selected tender: `CASH`, `CARD`, `UPI`, `BANK_TRANSFER`, or `CREDIT`. |
| `amountTendered` | **Yes** | No | Submitted for `CASH` payments to compute change due. |
| `paymentReference`| **Yes** | No | Optional. Captures bank POS card slip auth code, 12-digit UPI UTR, or bank wire ID. |
| `discount` | **Yes** | Evaluated | Header discount requested by cashier. Server validates that $0.00 \le \text{discount} \le \text{subtotal}$ and checks manager threshold. |
| `items[].productId` | **Yes** | No | Identifies the catalog spare part to purchase. |
| `items[].quantity` | **Yes** | No | Number of units requested (strictly whole positive integer $\ge 1$). |
| `items[].unitSellingPrice` | *Ignored* | **Yes** | Client may transmit preview, but **server re-queries catalog** from `products.selling_price` to prevent price tampering. |
| `items[].unitCostSnapshot` | **No** | **Yes** | Server retrieves current inventory procurement cost and freezes it for Member 7 COGS reporting. |
| `items[].lineTotal` | **No** | **Yes** | Re-computed on server: $\text{quantity} \times \text{unitSellingPrice}$. |
| `subtotal` | **No** | **Yes** | Re-computed on server: $\sum \text{lineTotal}$. |
| `total` | **No** | **Yes** | Re-computed on server: $\text{subtotal} - \text{discount}$. |
| `changeDue` | **No** | **Yes** | Computed for cash sales: $\text{amountTendered} - \text{total}$. |
| `saleNumber` | **No** | **Yes** | Minted by server sequential generator (e.g., `INV-202611-0001`). |
| `saleDate` | **No** | **Yes** | Server-assigned timestamp (`Instant.now()`). |
| `status` | **No** | **Yes** | Initialized by server to `COMPLETED` (or `PENDING` if awaiting bank transfer clearance). |
| `createdById` | **No** | **Yes** | Resolved strictly from cashier's authenticated JWT security context. |

---

## 2. Successful Checkout Examples

### Example 1: Walk-in Customer (Cash Tender with Change Calculation)
* **Scenario:** An individual motorcycle owner walks into the shop counter, purchases two Pulsar 150 clutch plates and one Bajaj 4T DTS-i 10W-30 engine oil can, and pays with a ₹1,000 note.
* **Customer Representation:** `customerId: null` (Walk-in Customer; no registration needed).

#### Request (`POST /api/v1/sales`)
```json
{
  "customerId": null,
  "paymentMethod": "CASH",
  "amountTendered": 1000.00,
  "paymentReference": null,
  "discount": 0.00,
  "items": [
    {
      "productId": 101,
      "quantity": 2
    },
    {
      "productId": 205,
      "quantity": 1
    }
  ]
}
```

#### Response (`201 Created`)
```json
{
  "id": 1042,
  "saleNumber": "INV-202611-0042",
  "customerId": null,
  "customerName": "Walk-in Customer",
  "customerPhone": null,
  "saleDate": "2026-11-13T14:45:10Z",
  "status": "COMPLETED",
  "subtotal": 870.00,
  "discount": 0.00,
  "total": 870.00,
  "paymentMethod": "CASH",
  "amountTendered": 1000.00,
  "changeDue": 130.00,
  "paymentReference": null,
  "createdById": 5,
  "createdByName": "Sunil Sharma",
  "items": [
    {
      "id": 8201,
      "productId": 101,
      "partNumber": "DH101048",
      "productName": "Clutch Plate Assembly",
      "quantity": 2,
      "unitSellingPrice": 220.00,
      "lineTotal": 440.00
    },
    {
      "id": 8202,
      "productId": 205,
      "partNumber": "36JZ0001",
      "productName": "Bajaj DTS-i 4T 10W-30 Engine Oil (1L)",
      "quantity": 1,
      "unitSellingPrice": 430.00,
      "lineTotal": 430.00
    }
  ]
}
```

---

### Example 2: Registered Garage Account (Credit Payment with Goodwill Discount)
* **Scenario:** "Metro Auto Repairs" (registered customer ID 42) purchases bulk brake shoes on 30-day store credit terms. The counter manager applies a ₹50 goodwill discount.
* **Customer Representation:** `customerId: 42`.
* **Validation:** Server verifies that `Metro Auto Repairs` has an active account and that the new total does not breach their ₹50,000 credit limit.

#### Request (`POST /api/v1/sales`)
```json
{
  "customerId": 42,
  "paymentMethod": "CREDIT",
  "amountTendered": null,
  "paymentReference": "PO-GARAGE-882",
  "discount": 50.00,
  "items": [
    {
      "productId": 312,
      "quantity": 5
    }
  ]
}
```

#### Response (`201 Created`)
```json
{
  "id": 1043,
  "saleNumber": "INV-202611-0043",
  "customerId": 42,
  "customerName": "Metro Auto Repairs",
  "customerPhone": "+91 9876543210",
  "saleDate": "2026-11-13T15:10:22Z",
  "status": "COMPLETED",
  "subtotal": 1250.00,
  "discount": 50.00,
  "total": 1200.00,
  "paymentMethod": "CREDIT",
  "amountTendered": null,
  "changeDue": null,
  "paymentReference": "PO-GARAGE-882",
  "createdById": 5,
  "createdByName": "Sunil Sharma",
  "items": [
    {
      "id": 8203,
      "productId": 312,
      "partNumber": "DK151026",
      "productName": "Brake Shoe Set Rear",
      "quantity": 5,
      "unitSellingPrice": 250.00,
      "lineTotal": 1250.00
    }
  ]
}
```

---

### Example 3: Electronic Settlement (UPI QR Code Payment)
* **Scenario:** A customer settles a ₹380 spare parts purchase via UPI QR code. The cashier verifies soundbox receipt and inputs the 12-digit UTR reference.

#### Request (`POST /api/v1/sales`)
```json
{
  "customerId": null,
  "paymentMethod": "UPI",
  "amountTendered": 380.00,
  "paymentReference": "431829012384",
  "discount": 0.00,
  "items": [
    {
      "productId": 401,
      "quantity": 1
    }
  ]
}
```

#### Response (`201 Created`)
```json
{
  "id": 1044,
  "saleNumber": "INV-202611-0044",
  "customerId": null,
  "customerName": "Walk-in Customer",
  "customerPhone": null,
  "saleDate": "2026-11-13T15:35:00Z",
  "status": "COMPLETED",
  "subtotal": 380.00,
  "discount": 0.00,
  "total": 380.00,
  "paymentMethod": "UPI",
  "amountTendered": 380.00,
  "changeDue": 0.00,
  "paymentReference": "431829012384",
  "createdById": 5,
  "createdByName": "Sunil Sharma",
  "items": [
    {
      "id": 8204,
      "productId": 401,
      "partNumber": "DJ181005",
      "productName": "Drive Chain Sprocket Kit",
      "quantity": 1,
      "unitSellingPrice": 380.00,
      "lineTotal": 380.00
    }
  ]
}
```

---

## 3. Failed Checkout Examples

### Failure Example 1: Insufficient Available Stock (409 Conflict)
* **Scenario:** Cashier attempts to checkout 10 units of an alternator stator coil (`productId: 504`), but only 3 units exist in active stock.
* **Server Action:** `InventoryService.deductStock(...)` throws `InsufficientStockException`. The transaction automatically rolls back. Zero records are inserted.

#### Request (`POST /api/v1/sales`)
```json
{
  "customerId": null,
  "paymentMethod": "CASH",
  "amountTendered": 15000.00,
  "discount": 0.00,
  "items": [
    {
      "productId": 504,
      "quantity": 10
    }
  ]
}
```

#### Response (`409 Conflict`)
```json
{
  "timestamp": "2026-11-13T15:40:12Z",
  "status": 409,
  "error": "Conflict",
  "message": "Insufficient stock for product ID 504 ('Alternator Stator Coil'). Requested: 10, Available: 3",
  "path": "/api/v1/sales"
}
```

---

### Failure Example 2: Walk-in Attempting Store Credit (403 Forbidden)
* **Scenario:** Cashier selects `paymentMethod: "CREDIT"` but leaves `customerId: null`.
* **Server Action:** The backend rejects the request because unverified walk-in customers cannot borrow store credit without a registered account profile.

#### Request (`POST /api/v1/sales`)
```json
{
  "customerId": null,
  "paymentMethod": "CREDIT",
  "discount": 0.00,
  "items": [
    {
      "productId": 101,
      "quantity": 1
    }
  ]
}
```

#### Response (`403 Forbidden`)
```json
{
  "timestamp": "2026-11-13T15:42:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Payment method 'CREDIT' is prohibited for walk-in sales. A registered customer account is required.",
  "path": "/api/v1/sales"
}
```

---

### Failure Example 3: Cash Tender Less Than Payable Total (400 Bad Request)
* **Scenario:** The sale total is ₹870.00, but the cashier enters `amountTendered: 500.00`.
* **Server Action:** Rejects the transaction due to insufficient cash collected.

#### Request (`POST /api/v1/sales`)
```json
{
  "customerId": null,
  "paymentMethod": "CASH",
  "amountTendered": 500.00,
  "discount": 0.00,
  "items": [
    {
      "productId": 101,
      "quantity": 2
    },
    {
      "productId": 205,
      "quantity": 1
    }
  ]
}
```

#### Response (`400 Bad Request`)
```json
{
  "timestamp": "2026-11-13T15:45:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Amount tendered (500.00) is less than the required total payable amount (870.00).",
  "path": "/api/v1/sales"
}
```

---

### Failure Example 4: Payload Validation Error (Empty Cart or Negative Quantity)
* **Scenario:** The client submits a checkout payload with an empty cart or invalid product quantities.
* **Server Action:** Spring `@Valid` interceptor fails validation before business logic executes.

#### Request (`POST /api/v1/sales`)
```json
{
  "customerId": null,
  "paymentMethod": "CASH",
  "amountTendered": 100.00,
  "discount": -10.00,
  "items": [
    {
      "productId": 101,
      "quantity": 0
    }
  ]
}
```

#### Response (`400 Bad Request`)
```json
{
  "timestamp": "2026-11-13T15:47:30Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for 2 field(s)",
  "validationErrors": [
    {
      "field": "discount",
      "rejectedValue": -10.00,
      "message": "Discount cannot be negative"
    },
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

## 4. Document Cross-References

* **REST API Contract Specification:** [docs/POS_API_CONTRACT.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/POS_API_CONTRACT.md)
* **Sale Header Field Definitions:** [docs/SALE_HEADER_FIELDS.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALE_HEADER_FIELDS.md)
* **Sale Item Field Definitions:** [docs/SALE_ITEM_FIELDS.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALE_ITEM_FIELDS.md)
* **Sales Workflow Specification:** [docs/SALES_WORKFLOW.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/SALES_WORKFLOW.md)

