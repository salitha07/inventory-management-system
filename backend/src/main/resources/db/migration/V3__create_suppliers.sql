CREATE TABLE suppliers (
    id BIGSERIAL PRIMARY KEY,
    supplier_name VARCHAR(150) NOT NULL,
    contact_person VARCHAR(120),
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(150),
    address VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_suppliers_supplier_name
    ON suppliers (supplier_name);

CREATE INDEX idx_suppliers_is_active
    ON suppliers (is_active);