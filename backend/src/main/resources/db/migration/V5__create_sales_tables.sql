-- ============================================================================
-- Migration: V5__create_sales_tables.sql
-- Module:    Sales Management & Point of Sale (POS) - Member 5
-- Database:  PostgreSQL
-- Matches:   Sale.java, SaleItem.java, SaleStatus.java, PaymentMethod.java
-- ============================================================================

-- Table 1: sales (Master Invoice Header)
CREATE TABLE IF NOT EXISTS sales (
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
        payment_method IN ('CASH', 'CARD', 'QR_PAYMENT', 'UPI', 'BANK_TRANSFER', 'CREDIT')
    ),
    CONSTRAINT ck_sales_subtotal_positive CHECK (subtotal >= 0.00),
    CONSTRAINT ck_sales_discount_limit CHECK (discount >= 0.00 AND discount <= subtotal),
    CONSTRAINT ck_sales_total_positive CHECK (total >= 0.00),
    CONSTRAINT ck_sales_credit_requires_customer CHECK (
        payment_method <> 'CREDIT' OR customer_id IS NOT NULL
    )
);

-- Foreign key: sales.created_by -> users.id (Member 1 Auth & Users)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_schema = CURRENT_SCHEMA() AND table_name = 'users'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.table_constraints 
            WHERE constraint_schema = CURRENT_SCHEMA() AND constraint_name = 'fk_sales_created_by'
        ) THEN
            ALTER TABLE sales 
                ADD CONSTRAINT fk_sales_created_by 
                FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT;
        END IF;
    END IF;
END $$;

-- Foreign key: sales.customer_id -> customers.id (Member 10 Customer Management)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_schema = CURRENT_SCHEMA() AND table_name = 'customers'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.table_constraints 
            WHERE constraint_schema = CURRENT_SCHEMA() AND constraint_name = 'fk_sales_customer'
        ) THEN
            ALTER TABLE sales 
                ADD CONSTRAINT fk_sales_customer 
                FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE RESTRICT;
        END IF;
    END IF;
END $$;

-- Indexes for sales table
CREATE INDEX IF NOT EXISTS idx_sales_sale_number ON sales(sale_number);
CREATE INDEX IF NOT EXISTS idx_sales_sale_date ON sales(sale_date DESC);
CREATE INDEX IF NOT EXISTS idx_sales_customer_id ON sales(customer_id);
CREATE INDEX IF NOT EXISTS idx_sales_created_by ON sales(created_by);
CREATE INDEX IF NOT EXISTS idx_sales_status ON sales(status);


-- Table 2: sale_items (Line Item Detail)
CREATE TABLE IF NOT EXISTS sale_items (
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
    CONSTRAINT fk_sale_items_sale FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE
);

-- Foreign key: sale_items.product_id -> products.id (Member 2 Product Catalog)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_schema = CURRENT_SCHEMA() AND table_name = 'products'
    ) THEN
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.table_constraints 
            WHERE constraint_schema = CURRENT_SCHEMA() AND constraint_name = 'fk_sale_items_product'
        ) THEN
            ALTER TABLE sale_items 
                ADD CONSTRAINT fk_sale_items_product 
                FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT;
        END IF;
    END IF;
END $$;

-- Indexes for sale_items table
CREATE INDEX IF NOT EXISTS idx_sale_items_sale_id ON sale_items(sale_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_product_id ON sale_items(product_id);
