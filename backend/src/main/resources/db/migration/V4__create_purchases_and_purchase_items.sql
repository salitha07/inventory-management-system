-- V4: Create Purchases and Purchase Items Tables
-- Bajaj Spare Parts Inventory Management System
-- Member 4 - Purchase Management

CREATE TABLE purchases (
    id BIGSERIAL PRIMARY KEY,
    purchase_number VARCHAR(50) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL,
    supplier_invoice_number VARCHAR(100) NOT NULL,
    purchase_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    subtotal NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,

    CONSTRAINT fk_purchases_supplier
        FOREIGN KEY (supplier_id) REFERENCES suppliers(id),

    CONSTRAINT fk_purchases_user
        FOREIGN KEY (created_by) REFERENCES users(id),

    CONSTRAINT chk_purchases_status
        CHECK (status IN ('DRAFT', 'RECEIVED', 'CANCELLED')),

    CONSTRAINT chk_purchases_subtotal
        CHECK (subtotal >= 0),

    CONSTRAINT chk_purchases_discount
        CHECK (discount >= 0 AND discount <= subtotal),

    CONSTRAINT chk_purchases_total
        CHECK (total >= 0 AND total = subtotal - discount)
);

CREATE TABLE purchase_items (
    id BIGSERIAL PRIMARY KEY,
    purchase_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    unit_cost NUMERIC(12,2) NOT NULL,
    line_total NUMERIC(12,2) NOT NULL,

    CONSTRAINT fk_purchase_items_purchase
        FOREIGN KEY (purchase_id) REFERENCES purchases(id),

    CONSTRAINT fk_purchase_items_product
        FOREIGN KEY (product_id) REFERENCES products(id),

    CONSTRAINT chk_purchase_items_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_purchase_items_unit_cost
        CHECK (unit_cost >= 0),

    CONSTRAINT chk_purchase_items_line_total
        CHECK (line_total >= 0 AND line_total = quantity * unit_cost)
);

CREATE INDEX idx_purchases_supplier_id
    ON purchases(supplier_id);

CREATE INDEX idx_purchases_purchase_date
    ON purchases(purchase_date);

CREATE INDEX idx_purchases_status
    ON purchases(status);

CREATE INDEX idx_purchases_created_by
    ON purchases(created_by);

CREATE INDEX idx_purchase_items_purchase_id
    ON purchase_items(purchase_id);

CREATE INDEX idx_purchase_items_product_id
    ON purchase_items(product_id);