package dk.madsjensen.bankaccount.exchange;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;

// Calls the public Frankfurter API for DKK/USD rates.
@Component
public class FrankfurterClient {

    // RestClient is immutable after construction and safe to reuse between requests.
    private final RestClient restClient;

    public FrankfurterClient(
            RestClient.Builder builder,
            @Value("${exchange-rate.base-url}") String baseUrl
    ) {
        // Keeping the base URL in configuration makes the client easy to redirect in tests.
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    // A request without a date asks the provider for its newest available observation.
    public Rate latest() {
        return get(null);
    }

    // Historical lookups use the same endpoint with an explicit date query parameter.
    public Rate on(LocalDate date) {
        return get(date);
    }

    private Rate get(LocalDate date) {
        try {
            Rate rate = restClient.get()
                    .uri(uriBuilder -> {
                        var uri = uriBuilder.path("/v2/rate/DKK/USD");
                        // Without a date the API returns the latest rate.
                        if (date != null) {
                            uri.queryParam("date", date);
                        }
                        return uri.build();
                    })
                    .retrieve()
                    .body(Rate.class);

            // Treat missing or non-positive values as an upstream failure, not valid data.
            if (rate == null || rate.rate() == null || rate.rate().signum() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "The exchange-rate provider returned invalid data"
                );
            }
            return rate;
        } catch (RestClientException exception) {
            // Hide provider-specific client errors behind a stable API-facing status.
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not retrieve exchange rates",
                    exception
            );
        }
    }

    // The record shape matches the compact JSON returned by Frankfurter's v2 endpoint.
    public record Rate(LocalDate date, String base, String quote, BigDecimal rate) {
    }
}
