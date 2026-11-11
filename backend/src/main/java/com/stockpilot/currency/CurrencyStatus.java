package com.stockpilot.currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CurrencyStatus(
        String baseCurrency,
        BigDecimal eurToRsd,
        LocalDate rateDate,
        Instant rateUpdatedAt,
        boolean legacyPricesConverted,
        Instant legacyPricesConvertedAt,
        LocalDate conversionStartedDate,
        BigDecimal conversionRate,
        LocalDate conversionRateDate,
        String rateProvider,
        String rateProviderUrl) {
}
