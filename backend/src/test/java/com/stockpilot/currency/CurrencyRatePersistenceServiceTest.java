package com.stockpilot.currency;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class CurrencyRatePersistenceServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void convertsLegacyPricesOnlyOnce() {
        CurrencyRatePersistenceService persistence = new CurrencyRatePersistenceService(jdbc);
        ExchangeRateSnapshot rate = new ExchangeRateSnapshot(LocalDate.parse("2026-03-16"),
                new BigDecimal("117.2500"), "ExchangeRate-API Open Access");
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(1)))
                .thenReturn(false, true);

        persistence.saveRateAndConvertLegacyPrices(rate);
        persistence.saveRateAndConvertLegacyPrices(rate);

        verify(jdbc, times(1)).update(startsWith("update products"), any(BigDecimal.class));
        verify(jdbc, times(1)).update(startsWith("update supplier_products"), any(BigDecimal.class));
        verify(jdbc, times(1)).update(startsWith("update purchase_order_lines"), any(BigDecimal.class));
        verify(jdbc, times(1)).update(startsWith("update currency_conversion_state"),
                any(BigDecimal.class), any(LocalDate.class), eq(1));
    }
}
