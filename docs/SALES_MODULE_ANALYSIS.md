# Bajaj Spare Parts Inventory & Sales Management System
## Module Analysis: Member 5 – Sales Management & Point of Sale (POS)

**Author:** Member 5 (Sales Management & POS)  
**Date:** November 2026  
**Stack Context:** React + Vite, Java 21 + Spring Boot, PostgreSQL + Flyway  
**Package Root:** `com.bajaj.inventory_backend`  
**API Base:** `/api/v1`  
**File Location:** `docs/SALES_MODULE_ANALYSIS.md`

---

## 1. Existing Project Structure

The project is structured as a multi-tier monorepo containing distinct backend, frontend, and documentation directories.

```
inventory-management-system/
├── backend/
│   ├── pom.xml                                  # Spring Boot 4.1.2 parent, Java 21, JPA, Flyway, PostgreSQL, Security, JJWT 0.13.0
│   ├── mvnw / mvnw.cmd
│   └── src/
│       ├── main/
│       │   ├── java/com/bajaj/inventory_backend/
│       │   │   ├── InventoryBackendApplication.java  # Main Spring Boot entry point
│       │   │   ├── audit/                       # (Stub / init.py)
│       │   │   ├── auth/                        # (Stub on main; implemented on feature/auth-user-management)
│       │   │   ├── category/                    # (Stub / init.py)
│       │   │   ├── common/                      # exception, response, util packages (stubs)
│       │   │   ├── config/                      # PasswordConfig.java, SecurityConfig.java
│       │   │   ├── customer/                    # (Stub / init.py)
│       │   │   ├── dashboard/                   # (Stub / init.py)
│       │   │   ├── entity/                      # User.java, Role.java, RoleName.java
│       │   │   ├── health/                      # HealthController.java (/api/v1/health)
│       │   │   ├── inventory/                   # (Stub / init.py)
│       │   │   ├── product/                     # (Stub / init.py)
│       │   │   ├── purchase/                    # (Stub / init.py)
│       │   │   ├── reports/                     # (Stub / init.py)
│       │   │   ├── repository/                  # UserRepository.java, RoleRepository.java
│       │   │   ├── returns/                     # (Stub / init.py)
│       │   │   ├── sales/                       # MEMBER 5 OWNERSHIP (Current: init.py)
│       │   │   ├── security/                    # JwtAuthenticationFilter.java, CustomUserDetailsService.java
│       │   │   ├── service/                     # JwtService.java
│       │   │   ├── suplier/                     # (Stub / init.py)
│       │   │   └── user/                        # (Stub / init.py)
│       │   └── resources/
│       │       ├── application.properties       # PostgreSQL config, JPA ddl-auto=validate, Flyway enabled, JWT secrets
│       │       └── db/migration/                # Flyway migration scripts (Current on main: init.py)
│       └── test/                                # Spring Boot test scaffolding
├── frontend/
│   ├── package.json                             # React 18, Vite, Axios, React Router v7
│   ├── vite.config.js
│   ├── index.html
│   └── src/
│       ├── App.jsx                              # Root router (Public: /login, Protected: / [Dashboard])
│       ├── main.jsx
│       ├── context/
│       │   └── AuthContext.jsx                  # Auth state management
│       ├── layouts/
│       │   └── MainLayout.jsx                   # App header and navigation shell
│       ├── pages/
│       │   ├── Login.jsx                        # User login view
│       │   └── Dashboard.jsx                    # Dashboard overview
│       ├── routes/
│       │   └── ProtectedRoute.jsx               # Route protection guard
│       └── services/
│           ├── api.js                           # Axios instance with Bearer token interceptor
│           └── healthService.js                 # Backend health check caller
└── docs/
    ├── sales/
    │   └── sales-workflow.md                    # Detailed workflow analysis and field specs
    └── SALES_MODULE_ANALYSIS.md                 # This analysis document
```

---

## 2. Sales Module Responsibilities (Member 5)

The Sales Management / Point of Sale (POS) module is responsible for the complete lifecycle of customer-facing sales transactions at the Bajaj spare parts counter:

1. **POS Counter Interface:** High-speed, keyboard/barcode-friendly interface for cashiers to scan spare parts, build order carts, and review prices.
2. **Dual Customer Profile Handling:**
   * **Walk-in Retail Sales:** Anonymous purchases by bike owners and independent mechanics where `customer_id` is `null`. Fast checkout without mandatory phone or account registration.
   * **Registered Workshop Accounts:** Tagged sales for Bajaj-authorized garages, commercial fleets, and regular mechanics with credit accounts or trade discounts.
3. **Price, Tax & Discount Computation:** Real-time computation of line-item totals, invoice-level discounts, and net payable amounts using exact decimal math (`BigDecimal`).
4. **Multi-Tender Settlement:** Capturing payments across `CASH` (with change calculation), `CARD` (terminal reference capture), `UPI` (UTR reference capture), `BANK_TRANSFER` (commercial wholesale), and `CREDIT` (workshop accounts).
5. **Transactional Integrity & Atomic Stock Depletion:** Coordinating inside a Spring Boot `@Transactional` boundary to save the invoice, freeze the acquisition cost snapshot, and invoke Member 6's centralized inventory service to deduct stock atomically.
6. **Receipt Emission:** Generating sequential invoice numbers (`sale_number`) and generating printable/thermal receipt data for counter printers.
7. **Sales History & Auditing:** Querying past transactions with pagination, date-range filters, cashier audit tracking (`created_by`), and re-printing capabilities.

---

## 3. Available Backend and Frontend Components

### Backend Components

| Component / File | Status | Description / Capability |
| :--- | :--- | :--- |
| **`InventoryBackendApplication.java`** | **Available** | Spring Boot bootstrap with package `com.bajaj.inventory_backend`. |
| **`SecurityConfig.java` & `PasswordConfig.java`** | **Available** | Configures BCrypt password encoder, stateless session, CORS (`http://localhost:5173`), and JWT filter pipeline. |
| **`JwtService.java` & `JwtAuthenticationFilter.java`** | **Available** | Handles JWT extraction, signature validation, and populates Spring `SecurityContextHolder`. |
| **`User.java` & `Role.java`** | **Available** | JPA entities for cashiers and system users; `User` has `id`, `fullName`, `email`, `role`, `active`, `createdAt`. |
| **`UserRepository.java` & `RoleRepository.java`** | **Available** | Spring Data JPA repositories with `@EntityGraph(attributePaths = "role")`. |
| **`com.bajaj.inventory_backend.sales`** | **Empty (Stub)** | Contains only `init.py`. No controllers, services, repositories, or entities exist yet. |
| **`HealthController.java`** | **Available** | Working `/api/v1/health` endpoint returning UP status. |

### Frontend Components

| Component / File | Status | Description / Capability |
| :--- | :--- | :--- |
| **`api.js` (Axios Client)** | **Available** | Central Axios instance configured with `baseURL: import.meta.env.VITE_API_URL` and automatic `Authorization: Bearer <token>` interceptor. |
| **`AuthContext.jsx` & `ProtectedRoute.jsx`** | **Available** | Manages user login state and blocks unauthorized access to protected routes. |
| **`MainLayout.jsx`** | **Basic Shell** | Minimal header navigation with link to `/`. Requires POS and Sales History navigation links. |
| **Sales / POS UI Components** | **Missing** | No POS terminal page, cart components, product search dropdowns, checkout modals, or receipt printing components exist. |

---

## 4. Database Structure and Migration Conventions

### Flyway Migration Standards

* **Configuration:** Defined in `application.properties`:
  * `spring.flyway.enabled=true`
  * `spring.flyway.locations=classpath:db/migration`
  * `spring.jpa.hibernate.ddl-auto=validate` (Hibernate strictly validates against Flyway-applied schema).
* **Script Naming Convention:** `V<Version>__<Description>.sql` (e.g., `V1__create_roles_and_users.sql`).
* **Data Type Conventions:**
  * Primary Keys: `BIGSERIAL PRIMARY KEY` (maps to Java `Long`).
  * Foreign Keys: `BIGINT NOT NULL` or `BIGINT NULL` with explicit constraint naming: `CONSTRAINT fk_<source_table>_<target_table> FOREIGN KEY (...) REFERENCES ...`.
  * Timestamps: `TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP` (maps to Java `Instant`).
  * Currency & Financial Numbers: `NUMERIC(12,2)` (maps to Java `java.math.BigDecimal`).
  * Text / Enumerations: `VARCHAR(20)` to `VARCHAR(50)`.
  * Booleans: `BOOLEAN NOT NULL DEFAULT TRUE`.

### Target Sales Database Schema

The Sales module requires two core relational tables:

```
┌──────────────────────────────────────────────┐
│                    sales                     │
├──────────────────────────────────────────────┤
│ id                 BIGSERIAL (PK)            │
│ sale_number        VARCHAR(32) NOT NULL (UQ) │
│ customer_id        BIGINT NULL (FK)          │───► customers(id) [Nullable for Walk-in]
│ sale_date          TIMESTAMPTZ NOT NULL      │
│ status             VARCHAR(20) NOT NULL      │
│ subtotal           NUMERIC(12,2) NOT NULL    │
│ discount           NUMERIC(12,2) NOT NULL    │
│ total              NUMERIC(12,2) NOT NULL    │
│ payment_method     VARCHAR(30) NOT NULL      │
│ created_by         BIGINT NOT NULL (FK)      │───► users(id) [Cashier audit trail]
│ created_at         TIMESTAMPTZ NOT NULL      │
│ updated_at         TIMESTAMPTZ NOT NULL      │
└──────────────────────┬───────────────────────┘
                       │ 1
                       │ has many
                       │ N
┌──────────────────────▼───────────────────────┐
│                  sale_items                  │
├──────────────────────────────────────────────┤
│ id                  BIGSERIAL (PK)           │
│ sale_id             BIGINT NOT NULL (FK)     │───► sales(id)
│ product_id          BIGINT NOT NULL (FK)     │───► products(id)
│ quantity            INTEGER NOT NULL         │
│ unit_selling_price  NUMERIC(12,2) NOT NULL   │
│ unit_cost_snapshot  NUMERIC(12,2) NOT NULL   │ [Frozen COGS for Member 7]
│ line_total          NUMERIC(12,2) NOT NULL   │
│ created_at          TIMESTAMPTZ NOT NULL      │
└──────────────────────────────────────────────┘
```

---

## 5. Dependencies on Other Members

| Member / Module | Critical Dependency on Member | What Member 5 Requires |
| :--- | :--- | :--- |
| **Member 1** (Auth & Users) | Authenticated cashier credentials & permissions | • Resolving `users.id` from JWT context for `created_by`.<br>• Finalized `RoleName` enum (`CASHIER`, `MANAGER`, `ADMIN`).<br>• Supervisor override mechanism for voiding completed sales or granting manager discounts. |
| **Member 2** (Products & Catalog) | Spare parts master data & pricing | • Catalog search endpoint (`/api/v1/products/search?q=...`) supporting part number, name, and barcode.<br>• Official selling prices (`selling_price`) and active status flags (`is_active = true`). |
| **Member 6** (Inventory & Stock) | Centralized stock deduction & cost prices | • Centralized inventory service method `deductStock(productId, quantity, referenceType, referenceId)` participating in Member 5's `@Transactional` boundary.<br>• Throwing `InsufficientStockException` on stock deficit to trigger complete rollback.<br>• Providing current unit acquisition cost to stamp `unit_cost_snapshot`. |
| **Member 7** (Reporting & Analytics) | Consumes sales transaction data | • Relies on `sales` and `sale_items` (specifically `unit_cost_snapshot` and `line_total`) to compute accurate Cost of Goods Sold (COGS), gross margins, and revenue analytics. |
| **Member 10** (Customer Management) | Customer accounts & store credit | • Walk-in agreement (`customer_id = NULL`).<br>• Customer validation and credit limit balance verification for `CREDIT` payment tenders. |

---

## 6. Missing Functionality and Integration Requirements

### Backend Gaps (To Be Implemented in Member 5 Scope)

1. **Entities & Enums (`com.bajaj.inventory_backend.entity` & `sales.entity`):**
   * `Sale`: Master entity mapped to `sales`.
   * `SaleItem`: Detail entity mapped to `sale_items`.
   * `SaleStatus`: Enum (`DRAFT`, `COMPLETED`, `CANCELLED`, `REFUNDED`, `PARTIALLY_REFUNDED`).
   * `PaymentMethod`: Enum (`CASH`, `CARD`, `UPI`, `BANK_TRANSFER`, `CREDIT`).
2. **Database Migration (`backend/src/main/resources/db/migration/`):**
   * Flyway script creating `sales` and `sale_items` with appropriate indices (`sale_number`, `customer_id`, `created_by`, `sale_date`).
3. **Data Access Layer (`repository`):**
   * `SaleRepository`: Extends `JpaRepository<Sale, Long>` with pagination and date-range queries.
   * `SaleItemRepository`: Line-item lookup and batch persistence.
4. **DTO Layer (`sales.dto`):**
   * Request DTOs: `SaleCreateRequest`, `SaleItemRequest`, `SaleStatusUpdateRequest`.
   * Response DTOs: `SaleResponse`, `SaleItemResponse`, `SalesSummaryResponse`.
5. **Business Logic Layer (`sales.service`):**
   * `SalesService` & `SalesServiceImpl`:
     * Validates input payload and rejects client-provided unit prices.
     * Re-queries authoritative database prices and calculates all totals using `BigDecimal`.
     * Obtains acquisition unit cost and stamps `unit_cost_snapshot`.
     * Generates unique, sequential `sale_number`.
     * Invokes Member 6's centralized inventory deduction inside the `@Transactional` boundary.
6. **REST Controller (`sales.controller`):**
   * `SalesController` exposing `/api/v1/sales`:
     * `POST /api/v1/sales`: Process counter checkout.
     * `GET /api/v1/sales`: Paginated search and historical audit query.
     * `GET /api/v1/sales/{id}`: Detailed invoice retrieval.
     * `PATCH /api/v1/sales/{id}/status`: Manager-authorized status transition (e.g., voiding).

### Frontend Gaps (To Be Implemented in Member 5 Scope)

1. **POS Terminal Screen (`frontend/src/pages/POS.jsx`):**
   * Debounced product search input supporting part numbers and barcode scanner auto-submit.
   * Active cart table displaying part details, quantity counters, unit prices, line discounts, and line totals.
   * Order summary panel showing calculated subtotal, discounts, and final total.
2. **Customer Selector & Tender Modal:**
   * Toggle between "Walk-in Customer" and "Registered Customer" search.
   * Tender modal supporting Cash (tendered and change calculator), Card (auth code), UPI (UTR reference), and Credit.
3. **Receipt Preview & Thermal Printing (`frontend/src/components/sales/ReceiptModal.jsx`):**
   * Clean printable receipt layout matching standard 80mm thermal POS receipts.
4. **Sales History Page (`frontend/src/pages/SalesHistory.jsx`):**
   * Paginated table with date-range picker, status filter, and receipt re-print action.
5. **API Client Service (`frontend/src/services/salesService.js`):**
   * Axios functions wrapping all `/api/v1/sales` endpoints.
6. **Navigation Integration:**
   * Adding "Point of Sale" and "Sales History" route links into `MainLayout.jsx` and `App.jsx`.

---

## 7. Next Steps for Member 5

1. Confirm open integration questions with Members 1, 2, 6, and 10 as specified in [sales-workflow.md](file:///c:/Users/hesha/Downloads/Inventory%20management%20system/inventory-management-system/docs/sales/sales-workflow.md#L182-L225).
2. Author the Flyway database migration for `sales` and `sale_items` once migration version numbering is coordinated.
3. Implement backend domain entities, repositories, service orchestrations, and REST controllers.
4. Implement frontend React POS interface, cart state management, and thermal receipt view.

