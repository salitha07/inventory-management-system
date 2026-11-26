
-- =====================================================
-- V2: Create Categories and Products Tables
-- Bajaj Spare Parts Inventory Management System
-- =====================================================

-- Create categories table
CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create products table
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    product_code VARCHAR(50) NOT NULL UNIQUE,
    product_name VARCHAR(150) NOT NULL,
    description TEXT,
    category_id BIGINT NOT NULL,
    cost_price NUMERIC(12,2) NOT NULL,
    selling_price NUMERIC(12,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_products_category
        FOREIGN KEY (category_id)
        REFERENCES categories(id),

    CONSTRAINT chk_products_cost_price
        CHECK (cost_price >= 0),

    CONSTRAINT chk_products_selling_price
        CHECK (selling_price > 0)
);

-- Create indexes for frequently searched columns
CREATE INDEX idx_products_category_id
    ON products(category_id);

CREATE INDEX idx_products_product_name
    ON products(product_name);

CREATE INDEX idx_products_active
    ON products(active);

CREATE INDEX idx_categories_active
    ON categories(active);
