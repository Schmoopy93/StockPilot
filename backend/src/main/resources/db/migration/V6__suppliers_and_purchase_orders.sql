CREATE TABLE suppliers (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(200) NOT NULL,
    email VARCHAR(320),
    phone VARCHAR(50),
    notes VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_suppliers_id_organization UNIQUE (id, organization_id)
);

CREATE INDEX idx_suppliers_organization_name
    ON suppliers (organization_id, lower(name));

CREATE TABLE purchase_orders (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    supplier_id UUID NOT NULL,
    order_number VARCHAR(20) NOT NULL,
    status VARCHAR(24) NOT NULL
        CHECK (status IN ('DRAFT', 'ORDERED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED')),
    expected_delivery_date DATE,
    notes VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ordered_at TIMESTAMPTZ,
    CONSTRAINT fk_purchase_orders_supplier_organization
        FOREIGN KEY (supplier_id, organization_id)
        REFERENCES suppliers (id, organization_id),
    CONSTRAINT uq_purchase_orders_id_organization UNIQUE (id, organization_id),
    CONSTRAINT uq_purchase_orders_number_organization UNIQUE (organization_id, order_number)
);

CREATE INDEX idx_purchase_orders_organization_recent
    ON purchase_orders (organization_id, created_at DESC);

CREATE TABLE purchase_order_lines (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    purchase_order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    unit VARCHAR(16) NOT NULL,
    ordered_quantity INTEGER NOT NULL CHECK (ordered_quantity > 0),
    received_quantity INTEGER NOT NULL DEFAULT 0
        CHECK (received_quantity >= 0 AND received_quantity <= ordered_quantity),
    unit_cost NUMERIC(12, 2) NOT NULL CHECK (unit_cost >= 0),
    CONSTRAINT fk_purchase_order_lines_order_organization
        FOREIGN KEY (purchase_order_id, organization_id)
        REFERENCES purchase_orders (id, organization_id),
    CONSTRAINT fk_purchase_order_lines_product_organization
        FOREIGN KEY (product_id, organization_id)
        REFERENCES products (id, organization_id),
    CONSTRAINT uq_purchase_order_lines_order_product UNIQUE (purchase_order_id, product_id)
);

CREATE INDEX idx_purchase_order_lines_order ON purchase_order_lines (purchase_order_id);
