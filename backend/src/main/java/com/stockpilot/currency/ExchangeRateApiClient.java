package com.stockpilot.currency;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ExchangeRateApiClient {

    private final RestClient restClient;

    public ExchangeRateApiClient(RestClient.Builder builder,
                                 @Value("${app.currency.api-url}") String apiUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = builder.baseUrl(apiUrl).requestFactory(requestFactory).build();
    }

    public ExchangeRateSnapshot fetchLatestEuroToRsdRate() {
        JsonNode response = restClient.get()
                .uri("/v6/latest/EUR")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class);
        return parse(response);
    }

    ExchangeRateSnapshot parse(JsonNode response) {
        if (response == null || !"success".equals(response.path("result").asText())) {
            String error = response == null ? "empty response" : response.path("error-type").asText("unknown error");
            throw new IllegalStateException("ExchangeRate-API returned an unsuccessful response: " + error);
        }
        if (!"EUR".equals(response.path("base_code").asText())) {
            throw new IllegalStateException("ExchangeRate-API response is not based on EUR");
        }
        JsonNode rateNode = response.path("rates").path("RSD");
        if (!rateNode.isNumber()) {
            throw new IllegalStateException("ExchangeRate-API response does not contain an RSD rate");
        }
        BigDecimal rate = rateNode.decimalValue();
        if (rate.signum() <= 0) {
            throw new IllegalStateException("ExchangeRate-API returned a non-positive EUR/RSD rate");
        }
        long updatedAt = response.path("time_last_update_unix").asLong(0);
        if (updatedAt <= 0) {
            throw new IllegalStateException("ExchangeRate-API response is missing its update timestamp");
        }
        LocalDate rateDate = Instant.ofEpochSecond(updatedAt).atZone(ZoneOffset.UTC).toLocalDate();
        return new ExchangeRateSnapshot(rateDate, rate, "ExchangeRate-API Open Access");
    }
}
