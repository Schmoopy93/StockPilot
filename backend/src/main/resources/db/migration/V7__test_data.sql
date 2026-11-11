-- Additional test data for the demo organization
-- Organization: 20000000-0000-4000-8000-000000000001

-- ── Products ─────────────────────────────────────────────────────────────────
INSERT INTO products (id, sku, name, category, unit, minimum_stock, lead_time_days, unit_cost, active, organization_id) VALUES
('00000000-0000-4000-8000-000000000010', 'COF-002', 'Kafa bez kofeina 500 g',   'Kafa',            'kom', 10,  6, 11.90, TRUE,  '20000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000000011', 'TEA-020', 'Crni caj 100 g',            'Caj',             'kom',  8,  7,  3.50, TRUE,  '20000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000000012', 'TEA-030', 'Kamilica 30 kesica',        'Caj',             'kom',  5,  7,  2.80, TRUE,  '20000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000000013', 'CUP-100', 'Papirne case 100 ml',       'Ambalaza',        'pak', 15,  3,  2.40, TRUE,  '20000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000000014', 'LID-250', 'Poklopci za case 250 ml',   'Ambalaza',        'pak', 20,  3,  1.90, TRUE,  '20000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000000015', 'STR-001', 'Slamcice 200 kom',          'Ambalaza',        'pak', 10,  4,  1.20, TRUE,  '20000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000000016', 'SYR-VAN', 'Sirup vanila 1 l',          'Sirupi',          'kom',  4,  8,  7.30, TRUE,  '20000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000000017', 'SYR-CAR', 'Sirup karamel 1 l',         'Sirupi',          'kom',  4,  8,  7.30, TRUE,  '20000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000000018', 'SYR-HAZ', 'Sirup ljesnjak 1 l',        'Sirupi',          'kom',  3,  8,  7.30, FALSE, '20000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000000019', 'NAP-001', 'Salvete 500 kom',           'Potrosni materijal','pak',10,  5,  2.10, TRUE,  '20000000-0000-4000-8000-000000000001'),
('00000000-0000-4000-8000-000000000020', 'CLN-001', 'Sredstvo za ciscenje 5 l',  'Potrosni materijal','kom', 3, 10,  8.50, TRUE,  '20000000-0000-4000-8000-000000000001');

-- ── Stock movements for new products (last 30 days) ───────────────────────────
INSERT INTO stock_movements (id, product_id, type, quantity, occurred_at, note, organization_id) VALUES
-- COF-002
('20000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000010', 'INBOUND',   40, now() - interval '28 days', 'Pocetno stanje',   '20000000-0000-4000-8000-000000000001'),
('20000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000010', 'OUTBOUND',  12, now() - interval '20 days', 'Prodaja',          '20000000-0000-4000-8000-000000000001'),
('20000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000010', 'OUTBOUND',  10, now() - interval '10 days', 'Prodaja',          '20000000-0000-4000-8000-000000000001'),
('20000000-0000-4000-8000-000000000004', '00000000-0000-4000-8000-000000000010', 'OUTBOUND',   8, now() - interval '2 days',  'Prodaja',          '20000000-0000-4000-8000-000000000001'),
-- TEA-020
('20000000-0000-4000-8000-000000000005', '00000000-0000-4000-8000-000000000011', 'INBOUND',   35, now() - interval '25 days', 'Pocetno stanje',   '20000000-0000-4000-8000-000000000001'),
('20000000-0000-4000-8000-000000000006', '00000000-0000-4000-8000-000000000011', 'OUTBOUND',  20, now() - interval '15 days', 'Prodaja',          '20000000-0000-4000-8000-000000000001'),
('20000000-0000-4000-8000-000000000007', '00000000-0000-4000-8000-000000000011', 'OUTBOUND',  10, now() - interval '5 days',  'Prodaja',          '20000000-0000-4000-8000-000000000001'),
-- TEA-030
('20000000-0000-4000-8000-000000000008', '00000000-0000-4000-8000-000000000012', 'INBOUND',   20, now() - interval '22 days', 'Pocetno stanje',   '20000000-0000-4000-8000-000000000001'),
('20000000-0000-4000-8000-000000000009', '00000000-0000-4000-8000-000000000012', 'OUTBOUND',  14, now() - interval '8 days',  'Prodaja',          '20000000-0000-4000-8000-000000000001'),
-- CUP-100
('20000000-0000-4000-8000-000000000010', '00000000-0000-4000-8000-000000000013', 'INBOUND',  100, now() - interval '20 days', 'Pocetno stanje',   '20000000-0000-4000-8000-000000000001'),
('20000000-0000-4000-8000-000000000011', '00000000-0000-4000-8000-000000000013', 'OUTBOUND',  75, now() - interval '4 days',  'Prodaja',          '20000000-0000-4000-8000-000000000001'),
-- LID-250
('20000000-0000-4000-8000-000000000012', '00000000-0000-4000-8000-000000000014', 'INBOUND',   80, now() - interval '18 days', 'Pocetno stanje',   '20000000-0000-4000-8000-000000000001'),
('20000000-0000-4000-8000-000000000013', '00000000-0000-4000-8000-000000000014', 'OUTBOUND',  55, now() - interval '3 days',  'Prodaja',          '20000000-0000-4000-8000-000000000001'),
-- STR-001
('20000000-0000-4000-8000-000000000014', '00000000-0000-4000-8000-000000000015', 'INBOUND',   30, now() - interval '15 days', 'Pocetno stanje',   '20000000-0000-4000-8000-000000000001'),
('20000000-0000-4000-8000-000000000015', '00000000-0000-4000-8000-000000000015', 'OUTBOUND',  18, now() - interval '6 days',  'Prodaja',          '20000000-0000-4000-8000-000000000001'),
-- SYR-VAN
('20000000-0000-4000-8000-000000000016', '00000000-0000-4000-8000-000000000016', 'INBOUND',   12, now() - interval '27 days', 'Pocetno stanje',   '20000000-0000-4000-8000-000000000001'),
('20000000-0000-4000-8000-000000000017', '00000000-0000-4000-8000-000000000016', 'OUTBOUND',   7, now() - interval '9 days',  'Prodaja',          '20000000-0000-4000-8000-000000000001'),
-- SYR-CAR
('20000000-0000-4000-8000-000000000018', '00000000-0000-4000-8000-000000000017', 'INBOUND',   10, now() - interval '27 days', 'Pocetno stanje',   '20000000-0000-4000-8000-000000000001'),
('20000000-0000-4000-8000-000000000019', '00000000-0000-4000-8000-000000000017', 'OUTBOUND',   6, now() - interval '7 days',  'Prodaja',          '20000000-0000-4000-8000-000000000001'),
-- NAP-001
('20000000-0000-4000-8000-000000000020', '00000000-0000-4000-8000-000000000019', 'INBOUND',   25, now() - interval '14 days', 'Pocetno stanje',   '20000000-0000-4000-8000-000000000001'),
('20000000-0000-4000-8000-000000000021', '00000000-0000-4000-8000-000000000019', 'OUTBOUND',  14, now() - interval '3 days',  'Prodaja',          '20000000-0000-4000-8000-000000000001'),
-- CLN-001
('20000000-0000-4000-8000-000000000022', '00000000-0000-4000-8000-000000000020', 'INBOUND',    8, now() - interval '30 days', 'Pocetno stanje',   '20000000-0000-4000-8000-000000000001'),
('20000000-0000-4000-8000-000000000023', '00000000-0000-4000-8000-000000000020', 'OUTBOUND',   5, now() - interval '12 days', 'Potrosnja',        '20000000-0000-4000-8000-000000000001'),
('20000000-0000-4000-8000-000000000024', '00000000-0000-4000-8000-000000000020', 'ADJUSTMENT', -1, now() - interval '1 day', 'Otpis - isteklo',  '20000000-0000-4000-8000-000000000001');

-- ── Suppliers ─────────────────────────────────────────────────────────────────
INSERT INTO suppliers (id, organization_id, name, email, phone, notes, active) VALUES
('30000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001',
 'Kafa & Co d.o.o.',       'nabavka@kafaco.rs',    '+381 11 123 4567', 'Dobavljac kafe i caja. Minimalna narudzba 10 kg.', TRUE),
('30000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000001',
 'PaperPack d.o.o.',       'orders@paperpack.rs',  '+381 21 987 6543', 'Ambalaza i potrosni materijal. Isporuka 3 dana.', TRUE),
('30000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000001',
 'SweetSyrups uvoz-izvoz', 'info@sweetsyrups.rs',  '+381 63 555 0011', 'Sirupi i dodaci. Rok placanja 30 dana.', TRUE);

-- ── Purchase orders ───────────────────────────────────────────────────────────
-- PO-001: RECEIVED (kafa i caj od Kafa & Co)
INSERT INTO purchase_orders (id, organization_id, supplier_id, order_number, status, expected_delivery_date, notes, created_at, ordered_at) VALUES
('40000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001',
 '30000000-0000-4000-8000-000000000001', 'PO-2025-001', 'RECEIVED',
 (now() - interval '20 days')::date, 'Redovna mesecna narudzba',
 now() - interval '26 days', now() - interval '25 days');

INSERT INTO purchase_order_lines (id, organization_id, purchase_order_id, product_id, product_name, sku, unit, ordered_quantity, received_quantity, unit_cost) VALUES
('50000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001',
 '40000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001',
 'Espresso kafa 1 kg', 'COF-001', 'kom', 45, 45, 14.50),
('50000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000001',
 '40000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000010',
 'Kafa bez kofeina 500 g', 'COF-002', 'kom', 40, 40, 11.90),
('50000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000001',
 '40000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000002',
 'Zeleni caj 100 g', 'TEA-010', 'kom', 30, 30, 4.20);

-- PO-002: PARTIALLY_RECEIVED (ambalaza od PaperPack)
INSERT INTO purchase_orders (id, organization_id, supplier_id, order_number, status, expected_delivery_date, notes, created_at, ordered_at) VALUES
('40000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000001',
 '30000000-0000-4000-8000-000000000002', 'PO-2025-002', 'PARTIALLY_RECEIVED',
 (now() + interval '2 days')::date, 'Hitna narudzba ambalaze',
 now() - interval '8 days', now() - interval '7 days');

INSERT INTO purchase_order_lines (id, organization_id, purchase_order_id, product_id, product_name, sku, unit, ordered_quantity, received_quantity, unit_cost) VALUES
('50000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000001',
 '40000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000003',
 'Papirne case 250 ml', 'CUP-250', 'pak', 100, 60, 3.80),
('50000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000001',
 '40000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000014',
 'Poklopci za case 250 ml', 'LID-250', 'pak', 80, 80, 1.90),
('50000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000001',
 '40000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000019',
 'Salvete 500 kom', 'NAP-001', 'pak', 20, 0, 2.10);

-- PO-003: ORDERED (sirupi od SweetSyrups)
INSERT INTO purchase_orders (id, organization_id, supplier_id, order_number, status, expected_delivery_date, notes, created_at, ordered_at) VALUES
('40000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000001',
 '30000000-0000-4000-8000-000000000003', 'PO-2025-003', 'ORDERED',
 (now() + interval '6 days')::date, NULL,
 now() - interval '2 days', now() - interval '1 day');

INSERT INTO purchase_order_lines (id, organization_id, purchase_order_id, product_id, product_name, sku, unit, ordered_quantity, received_quantity, unit_cost) VALUES
('50000000-0000-4000-8000-000000000007', '20000000-0000-4000-8000-000000000001',
 '40000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000016',
 'Sirup vanila 1 l', 'SYR-VAN', 'kom', 8, 0, 7.30),
('50000000-0000-4000-8000-000000000008', '20000000-0000-4000-8000-000000000001',
 '40000000-0000-4000-8000-000000000003', '00000000-0000-4000-8000-000000000017',
 'Sirup karamel 1 l', 'SYR-CAR', 'kom', 8, 0, 7.30);

-- PO-004: DRAFT (kafa - priprema za sledecu narudzbu)
INSERT INTO purchase_orders (id, organization_id, supplier_id, order_number, status, expected_delivery_date, notes, created_at, ordered_at) VALUES
('40000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000001',
 '30000000-0000-4000-8000-000000000001', 'PO-2025-004', 'DRAFT',
 NULL, 'Generisano iz preporuka za dopunu',
 now(), NULL);

INSERT INTO purchase_order_lines (id, organization_id, purchase_order_id, product_id, product_name, sku, unit, ordered_quantity, received_quantity, unit_cost) VALUES
('50000000-0000-4000-8000-000000000009', '20000000-0000-4000-8000-000000000001',
 '40000000-0000-4000-8000-000000000004', '00000000-0000-4000-8000-000000000001',
 'Espresso kafa 1 kg', 'COF-001', 'kom', 30, 0, 14.50),
('50000000-0000-4000-8000-000000000010', '20000000-0000-4000-8000-000000000001',
 '40000000-0000-4000-8000-000000000004', '00000000-0000-4000-8000-000000000005',
 'Mleko 1 l', 'MIL-001', 'kom', 50, 0, 0.95);

-- PO-005: CANCELLED
INSERT INTO purchase_orders (id, organization_id, supplier_id, order_number, status, expected_delivery_date, notes, created_at, ordered_at) VALUES
('40000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000001',
 '30000000-0000-4000-8000-000000000002', 'PO-2025-005', 'CANCELLED',
 NULL, 'Otkazano - dobavljac nije mogao da isporuci',
 now() - interval '15 days', now() - interval '14 days');

INSERT INTO purchase_order_lines (id, organization_id, purchase_order_id, product_id, product_name, sku, unit, ordered_quantity, received_quantity, unit_cost) VALUES
('50000000-0000-4000-8000-000000000011', '20000000-0000-4000-8000-000000000001',
 '40000000-0000-4000-8000-000000000005', '00000000-0000-4000-8000-000000000015',
 'Slamcice 200 kom', 'STR-001', 'pak', 20, 0, 1.20);
