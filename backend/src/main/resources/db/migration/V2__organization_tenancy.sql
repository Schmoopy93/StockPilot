CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_users_email_ci ON users (lower(email));

CREATE TABLE organization_memberships (
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'STAFF', 'READ_ONLY')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, user_id)
);

CREATE INDEX idx_memberships_user ON organization_memberships(user_id);

INSERT INTO organizations (id, name)
VALUES ('20000000-0000-4000-8000-000000000001', 'StockPilot Demo');

ALTER TABLE products ADD COLUMN organization_id UUID;
UPDATE products
SET organization_id = '20000000-0000-4000-8000-000000000001';
ALTER TABLE products ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE products DROP CONSTRAINT products_sku_key;
ALTER TABLE products
    ADD CONSTRAINT fk_products_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id),
    ADD CONSTRAINT uq_products_id_organization
        UNIQUE (id, organization_id);
CREATE UNIQUE INDEX uq_products_organization_sku
    ON products (organization_id, lower(sku));
CREATE INDEX idx_products_organization_name
    ON products (organization_id, name);

ALTER TABLE stock_movements ADD COLUMN organization_id UUID;
UPDATE stock_movements movements
SET organization_id = products.organization_id
FROM products
WHERE products.id = movements.product_id;
ALTER TABLE stock_movements ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE stock_movements
    ADD CONSTRAINT fk_movements_product_organization
        FOREIGN KEY (product_id, organization_id)
        REFERENCES products (id, organization_id);
CREATE INDEX idx_movements_organization_recent
    ON stock_movements (organization_id, occurred_at DESC);
CREATE INDEX idx_movements_organization_product_time
    ON stock_movements (organization_id, product_id, occurred_at);
