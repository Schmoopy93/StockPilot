package com.stockpilot.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ExchangeRateApiClientTest {

    private final ExchangeRateApiClient client = new ExchangeRateApiClient(
            RestClient.builder(), "https://open.er-api.com");

    @Test
    void parsesEuroToRsdRateAndProviderUpdateDate() throws Exception {
        var response = new ObjectMapper().readTree("""
                {
                  "result": "success",
                  "base_code": "EUR",
                  "time_last_update_unix": 1794355352,
                  "rates": {"RSD": 117.626935}
                }
                """);
        ExchangeRateSnapshot rate = client.parse(response);

        assertThat(rate.rateDate()).isEqualTo(LocalDate.parse("2026-11-11"));
        assertThat(rate.eurToRsd()).isEqualByComparingTo("117.626935");
        assertThat(rate.provider()).isEqualTo("ExchangeRate-API Open Access");
    }

    @Test
    void rejectsResponseWithoutRsd() throws Exception {
        var response = new ObjectMapper().readTree("""
                {
                  "result": "success",
                  "base_code": "EUR",
                  "time_last_update_unix": 1794355352,
                  "rates": {"USD": 1.1}
                }
                """);
        assertThatThrownBy(() -> client.parse(response))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not contain an RSD rate");
    }

    @Test
    void rejectsNonSuccessfulResponse() throws Exception {
        var response = new ObjectMapper().readTree("""
                {"result": "error", "error-type": "unsupported-code"}
                """);
        assertThatThrownBy(() -> client.parse(response))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unsupported-code");
    }
}
