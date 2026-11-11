CREATE TABLE supplier_products (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    supplier_id UUID NOT NULL,
    product_id UUID NOT NULL,
    supplier_sku VARCHAR(100),
    unit_cost NUMERIC(12, 2) NOT NULL CHECK (unit_cost >= 0),
    minimum_order_quantity INTEGER NOT NULL DEFAULT 1 CHECK (minimum_order_quantity > 0),
    lead_time_days INTEGER CHECK (lead_time_days IS NULL OR lead_time_days >= 0),
    preferred_supplier BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_supplier_products_supplier_organization
        FOREIGN KEY (supplier_id, organization_id)
        REFERENCES suppliers (id, organization_id),
    CONSTRAINT fk_supplier_products_product_organization
        FOREIGN KEY (product_id, organization_id)
        REFERENCES products (id, organization_id),
    CONSTRAINT uq_supplier_products_pair UNIQUE (organization_id, supplier_id, product_id),
    CONSTRAINT uq_supplier_products_id_organization UNIQUE (id, organization_id)
);

CREATE UNIQUE INDEX uq_supplier_products_preferred
    ON supplier_products (organization_id, product_id)
    WHERE preferred_supplier = TRUE;

CREATE INDEX idx_supplier_products_supplier
    ON supplier_products (organization_id, supplier_id, product_id);

CREATE INDEX idx_supplier_products_product
    ON supplier_products (organization_id, product_id);
