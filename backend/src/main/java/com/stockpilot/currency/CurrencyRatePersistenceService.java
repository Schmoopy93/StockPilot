package com.stockpilot.currency;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrencyRatePersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyRatePersistenceService.class);
    private static final int CURRENCY_STATE_ID = 1;

    private final JdbcTemplate jdbc;

    public CurrencyRatePersistenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public CurrencyStatus currentStatus() {
        return jdbc.queryForObject("""
                select rate.eur_to_rsd, rate.rate_date, rate.fetched_at, rate.provider,
                       state.legacy_prices_converted, state.converted_at,
                       state.conversion_started_date, state.conversion_rate,
                       state.conversion_rate_date
                from currency_conversion_state state
                left join lateral (
                    select eur_to_rsd, rate_date, fetched_at, provider
                    from exchange_rates
                    order by rate_date desc
                    limit 1
                ) rate on true
                where state.id = ?
                """, this::toStatus, CURRENCY_STATE_ID);
    }

    @Transactional
    public void saveRateAndConvertLegacyPrices(ExchangeRateSnapshot rate) {
        jdbc.update("""
                insert into exchange_rates (rate_date, eur_to_rsd, fetched_at, provider)
                values (?, ?, now(), ?)
                on conflict (rate_date) do update
                set eur_to_rsd = excluded.eur_to_rsd, fetched_at = excluded.fetched_at,
                    provider = excluded.provider
                """, rate.rateDate(), rate.eurToRsd(), rate.provider());

        Boolean converted = jdbc.queryForObject("""
                select legacy_prices_converted
                from currency_conversion_state
                where id = ?
                for update
                """, Boolean.class, CURRENCY_STATE_ID);
        if (!Boolean.TRUE.equals(converted)) {
            convertLegacyPrices(rate.eurToRsd());
            jdbc.update("""
                    update currency_conversion_state
                    set legacy_prices_converted = true,
                        converted_at = now(),
                        conversion_rate = ?,
                        conversion_rate_date = ?
                    where id = ? and legacy_prices_converted = false
                    """, rate.eurToRsd(), rate.rateDate(), CURRENCY_STATE_ID);
            logger.info("Converted legacy EUR prices to RSD at {} rate {} dated {}",
                    rate.provider(),
                    rate.eurToRsd(), rate.rateDate());
        }
    }

    private void convertLegacyPrices(BigDecimal eurToRsd) {
        List<String> costTables = List.of("products", "supplier_products", "purchase_order_lines");
        for (String table : costTables) {
            jdbc.update("update " + table + " set unit_cost = round(unit_cost * ?, 2)", eurToRsd);
        }
    }

    private CurrencyStatus toStatus(ResultSet result, int row) throws SQLException {
        BigDecimal rate = result.getBigDecimal("eur_to_rsd");
        java.sql.Date date = result.getDate("rate_date");
        java.sql.Timestamp fetchedAt = result.getTimestamp("fetched_at");
        java.sql.Timestamp convertedAt = result.getTimestamp("converted_at");
        String provider = result.getString("provider");
        return new CurrencyStatus("RSD", rate,
                date == null ? null : date.toLocalDate(),
                fetchedAt == null ? null : fetchedAt.toInstant(),
                result.getBoolean("legacy_prices_converted"),
                convertedAt == null ? null : convertedAt.toInstant(),
                result.getDate("conversion_started_date").toLocalDate(),
                result.getBigDecimal("conversion_rate"),
                result.getDate("conversion_rate_date") == null
                        ? null : result.getDate("conversion_rate_date").toLocalDate(),
                provider,
                provider == null ? null : provider.equals("NBS")
                        ? "https://www.nbs.rs" : "https://www.exchangerate-api.com");
    }
}
