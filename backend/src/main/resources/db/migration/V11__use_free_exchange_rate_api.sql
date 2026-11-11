ALTER TABLE nbs_exchange_rates RENAME TO exchange_rates;

ALTER TABLE exchange_rates
    ADD COLUMN provider VARCHAR(100) NOT NULL DEFAULT 'NBS';

ALTER TABLE exchange_rates
    ALTER COLUMN provider SET DEFAULT 'ExchangeRate-API Open Access';

ALTER TABLE currency_conversion_state
    RENAME COLUMN conversion_target_date TO conversion_started_date;
