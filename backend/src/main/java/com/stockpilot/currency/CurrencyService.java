package com.stockpilot.currency;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class CurrencyService {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyService.class);

    private final ExchangeRateApiClient exchangeRateApi;
    private final CurrencyRatePersistenceService persistence;

    public CurrencyService(ExchangeRateApiClient exchangeRateApi, CurrencyRatePersistenceService persistence) {
        this.exchangeRateApi = exchangeRateApi;
        this.persistence = persistence;
    }

    public CurrencyStatus currentStatus() {
        return persistence.currentStatus();
    }

    public CurrencyStatus refreshFromProvider() {
        CurrencyStatus status = currentStatus();
        if (status.legacyPricesConverted()
                && status.rateUpdatedAt() != null
                && status.rateUpdatedAt().isAfter(Instant.now().minus(Duration.ofHours(20)))) {
            return status;
        }
        ExchangeRateSnapshot rate = exchangeRateApi.fetchLatestEuroToRsdRate();
        persistence.saveRateAndConvertLegacyPrices(rate);
        return currentStatus();
    }

    public void requireRsdPricesReady() {
        if (!currentStatus().legacyPricesConverted()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Prices are waiting for an EUR/RSD rate before RSD price entry is enabled");
        }
    }

    @Scheduled(fixedDelayString = "${app.currency.refresh-delay:86400000}",
            initialDelayString = "${app.currency.initial-delay:10000}")
    public void scheduledRefresh() {
        try {
            refreshFromProvider();
        } catch (RestClientException | IllegalStateException exception) {
            logger.warn("Free exchange-rate refresh failed; stored rates and currency state were not changed: {}",
                    exception.getMessage());
        }
    }
}
