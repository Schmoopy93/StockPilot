CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(100),
    unit VARCHAR(16) NOT NULL,
    minimum_stock INTEGER NOT NULL CHECK (minimum_stock >= 0),
    lead_time_days INTEGER NOT NULL CHECK (lead_time_days >= 0),
    unit_cost NUMERIC(12, 2) NOT NULL CHECK (unit_cost >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE stock_movements (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id),
    type VARCHAR(16) NOT NULL CHECK (type IN ('INBOUND', 'OUTBOUND', 'ADJUSTMENT')),
    quantity INTEGER NOT NULL CHECK (quantity <> 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    note VARCHAR(300)
);

CREATE INDEX idx_stock_movements_product_time ON stock_movements(product_id, occurred_at);
CREATE INDEX idx_stock_movements_recent ON stock_movements(occurred_at DESC);

INSERT INTO products (id, sku, name, category, unit, minimum_stock, lead_time_days, unit_cost, active) VALUES
('00000000-0000-4000-8000-000000000001', 'COF-001', 'Espresso kafa 1 kg', 'Kafa', 'kom', 12, 5, 14.50, TRUE),
('00000000-0000-4000-8000-000000000002', 'TEA-010', 'Zeleni caj 100 g', 'Caj', 'kom', 8, 7, 4.20, TRUE),
('00000000-0000-4000-8000-000000000003', 'CUP-250', 'Papirne case 250 ml', 'Ambalaza', 'pak', 20, 3, 3.80, TRUE),
('00000000-0000-4000-8000-000000000004', 'SUG-001', 'Secer 1 kg', 'Dodaci', 'kom', 10, 4, 1.15, TRUE),
('00000000-0000-4000-8000-000000000005', 'MIL-001', 'Mleko 1 l', 'Mlecni proizvodi', 'kom', 16, 1, 0.95, TRUE),
('00000000-0000-4000-8000-000000000006', 'CHO-020', 'Topla cokolada 500 g', 'Dodaci', 'kom', 6, 6, 5.60, TRUE);

INSERT INTO stock_movements (id, product_id, type, quantity, occurred_at, note) VALUES
('10000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001', 'INBOUND', 45, now() - interval '24 days', 'Pocetno stanje'),
('10000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000001', 'OUTBOUND', 32, now() - interval '2 days', 'Prodaja'),
('10000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000002', 'INBOUND', 30, now() - interval '20 days', 'Pocetno stanje'),
('10000000-0000-4000-8000-000000000004', '00000000-0000-4000-8000-000000000002', 'OUTBOUND', 18, now() - interval '1 day', 'Prodaja'),
('10000000-0000-4000-8000-000000000005', '00000000-0000-4000-8000-000000000003', 'INBOUND', 80, now() - interval '18 days', 'Pocetno stanje'),
('10000000-0000-4000-8000-000000000006', '00000000-0000-4000-8000-000000000003', 'OUTBOUND', 67, now() - interval '3 days', 'Prodaja'),
('10000000-0000-4000-8000-000000000007', '00000000-0000-4000-8000-000000000004', 'INBOUND', 40, now() - interval '22 days', 'Pocetno stanje'),
('10000000-0000-4000-8000-000000000008', '00000000-0000-4000-8000-000000000004', 'OUTBOUND', 31, now() - interval '2 days', 'Prodaja'),
('10000000-0000-4000-8000-000000000009', '00000000-0000-4000-8000-000000000005', 'INBOUND', 50, now() - interval '12 days', 'Pocetno stanje'),
('10000000-0000-4000-8000-000000000010', '00000000-0000-4000-8000-000000000005', 'OUTBOUND', 43, now() - interval '1 day', 'Prodaja'),
('10000000-0000-4000-8000-000000000011', '00000000-0000-4000-8000-000000000006', 'INBOUND', 18, now() - interval '16 days', 'Pocetno stanje'),
('10000000-0000-4000-8000-000000000012', '00000000-0000-4000-8000-000000000006', 'OUTBOUND', 9, now() - interval '2 days', 'Prodaja');
