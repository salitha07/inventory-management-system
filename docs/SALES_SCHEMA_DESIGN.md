# Bajaj Spare Parts Inventory & Sales Management System
## Sales Database Schema & Migration Status (Task 10 Tracking)

**Module:** Sales Management & Point of Sale (POS) — Member 5  
**System:** Bajaj Spare Parts Inventory & Sales Management System  
**Flyway Target Migration:** `V5__create_sales_tables.sql`  
**Current Status:** **BLOCKED (Awaiting Upstream Database Migrations)**  
**File Location:** `docs/SALES_SCHEMA_DESIGN.md`

---

## 1. Task 10 Status: BLOCKED

Task 10 (implementing Flyway migration `V5__create_sales_tables.sql`) is currently **BLOCKED** due to missing required upstream database migrations and shared tables.

### Upstream Dependencies Required:
1. **Member 1 (Auth & Users):**
   * Table: `users`
   * Migration: `V1__create_roles_and_users.sql`
   * Requirement: `sales.created_by` foreign key references `users(id)`.
   * Status: Exists on unmerged branch `feature/auth-user-management`, but not yet merged into `main` or `feature/sales-management`.
2. **Member 2 (Products & Catalog):**
   * Table: `products`
   * Migration: Expected `V2` (or catalog schema migration)
   * Requirement: `sale_items.product_id` foreign key references `products(id)`.
   * Status: Migration not yet authored or committed by Member 2.
3. **Member 10 (Customer Management):**
   * Table: `customers`
   * Migration: Expected `V3` / `V4` (or customer schema migration)
   * Requirement: `sales.customer_id` foreign key references `customers(id)`.
   * Status: Migration not yet authored or committed by Member 10.

---

## 2. Approved Proposed Database Schema

Once upstream migrations are merged, `V5__create_sales_tables.sql` will be applied with the following approved DDL:

```sql
-- ============================================================================
-- Migration: V5__create_sales_tables.sql
-- Module:    Sales Management & Point of Sale (POS) - Member 5
-- Database:  PostgreSQL
-- ============================================================================

-- Table 1: sales (Master Invoice Header)
CREATE TABLE sales (
    id BIGSERIAL PRIMARY KEY,
    sale_number VARCHAR(32) NOT NULL,
    customer_id BIGINT NULL,
    sale_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    subtotal NUMERIC(12,2) NOT NULL,
    discount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    total NUMERIC(12,2) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_sales_sale_number UNIQUE (sale_number),
    CONSTRAINT ck_sales_status CHECK (
        status IN ('DRAFT', 'COMPLETED', 'CANCELLED', 'REFUNDED', 'PARTIALLY_REFUNDED')
    ),
    CONSTRAINT ck_sales_payment_method CHECK (
        payment_method IN ('CASH', 'CARD', 'UPI', 'BANK_TRANSFER', 'CREDIT')
    ),
    CONSTRAINT ck_sales_subtotal_positive CHECK (subtotal >= 0.00),
    CONSTRAINT ck_sales_discount_positive CHECK (discount >= 0.00),
    CONSTRAINT ck_sales_discount_limit CHECK (discount <= subtotal),
    CONSTRAINT ck_sales_total_positive CHECK (total >= 0.00),
    CONSTRAINT ck_sales_credit_requires_customer CHECK (
        payment_method <> 'CREDIT' OR customer_id IS NOT NULL
    ),
    CONSTRAINT fk_sales_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_sales_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT
);

CREATE INDEX idx_sales_sale_number ON sales(sale_number);
CREATE INDEX idx_sales_sale_date ON sales(sale_date DESC);
CREATE INDEX idx_sales_customer_id ON sales(customer_id);
CREATE INDEX idx_sales_created_by ON sales(created_by);
CREATE INDEX idx_sales_status ON sales(status);

-- Table 2: sale_items (Line Item Detail)
CREATE TABLE sale_items (
    id BIGSERIAL PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_selling_price NUMERIC(12,2) NOT NULL,
    unit_cost_snapshot NUMERIC(12,2) NOT NULL,
    line_total NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_sale_items_quantity_positive CHECK (quantity >= 1),
    CONSTRAINT ck_sale_items_selling_price_positive CHECK (unit_selling_price >= 0.00),
    CONSTRAINT ck_sale_items_cost_snapshot_positive CHECK (unit_cost_snapshot >= 0.00),
    CONSTRAINT ck_sale_items_line_total_positive CHECK (line_total >= 0.00),
    CONSTRAINT fk_sale_items_sale FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
    CONSTRAINT fk_sale_items_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
);

CREATE INDEX idx_sale_items_sale_id ON sale_items(sale_id);
CREATE INDEX idx_sale_items_product_id ON sale_items(product_id);
```

---

## 3. Resolution Plan
1. Member 1 merges `feature/auth-user-management` creating `roles` and `users`.
2. Member 2 merges catalog migration creating `products`.
3. Member 10 merges customer migration creating `customers`.
4. Member 5 executes `V5__create_sales_tables.sql` without foreign key conflicts.

