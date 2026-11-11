CREATE TABLE nbs_exchange_rates (
    rate_date DATE PRIMARY KEY,
    eur_to_rsd NUMERIC(12, 6) NOT NULL CHECK (eur_to_rsd > 0),
    fetched_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE currency_conversion_state (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    legacy_prices_converted BOOLEAN NOT NULL DEFAULT FALSE,
    conversion_target_date DATE NOT NULL,
    converted_at TIMESTAMPTZ,
    conversion_rate NUMERIC(12, 6),
    conversion_rate_date DATE
);

INSERT INTO currency_conversion_state (id, legacy_prices_converted, conversion_target_date)
VALUES (1, FALSE, (now() AT TIME ZONE 'Europe/Belgrade')::date);
