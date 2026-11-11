package com.stockpilot.currency;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientException;

@RestController
@RequestMapping("/api/currency")
public class CurrencyController {

    private final CurrencyService currency;

    public CurrencyController(CurrencyService currency) {
        this.currency = currency;
    }

    @GetMapping
    public CurrencyStatus status() {
        return currency.currentStatus();
    }

    @PostMapping("/refresh")
    public CurrencyStatus refresh() {
        try {
            return currency.refreshFromProvider();
        } catch (RestClientException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Besplatni kurs EUR/RSD trenutno nije dostupan; sačuvane cene i kurs nisu promenjeni.",
                    exception);
        }
    }
}
