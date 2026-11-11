ALTER TABLE purchase_orders
    ADD COLUMN last_received_at TIMESTAMPTZ;

ALTER TABLE stock_movements
    ADD COLUMN purchase_order_id UUID,
    ADD COLUMN purchase_order_number VARCHAR(20);

ALTER TABLE stock_movements
    ADD CONSTRAINT fk_stock_movements_purchase_order_organization
        FOREIGN KEY (purchase_order_id, organization_id)
        REFERENCES purchase_orders (id, organization_id);

CREATE INDEX idx_purchase_orders_supplier_recent
    ON purchase_orders (organization_id, supplier_id, created_at DESC);

CREATE INDEX idx_stock_movements_purchase_order
    ON stock_movements (organization_id, purchase_order_id)
    WHERE purchase_order_id IS NOT NULL;
