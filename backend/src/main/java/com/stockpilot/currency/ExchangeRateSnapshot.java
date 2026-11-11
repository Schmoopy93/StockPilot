package com.stockpilot.currency;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRateSnapshot(LocalDate rateDate, BigDecimal eurToRsd, String provider) {
}
