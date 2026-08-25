package dk.madsjensen.bankaccount.exchange;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
public class ExchangeRateService {

    // The task says to skip 2012.
    private static final List<Integer> HISTORICAL_YEARS = IntStream
            .rangeClosed(2005, 2015)
            .filter(year -> year != 2012)
            .boxed()
            .toList();

    private final FrankfurterClient client;

    public ExchangeRateService(FrankfurterClient client) {
        this.client = client;
    }

    public CurrentRate current(BigDecimal requestedAmount) {
        BigDecimal amount = checkAmount(requestedAmount);
        FrankfurterClient.Rate rate = client.latest();
        // Return both the raw rate and the converted amount so clients can audit the result.
        return new CurrentRate(
                "DKK",
                "USD",
                amount,
                rate.rate(),
                convert(amount, rate.rate()),
                rate.date(),
                "Frankfurter"
        );
    }

    public RateHistory history(BigDecimal requestedAmount) {
        BigDecimal amount = checkAmount(requestedAmount);

        // January 1 is used as the consistent comparison date for every historical year.
        Stream<RatePoint> historical = HISTORICAL_YEARS.stream()
                .map(year -> toPoint(
                        client.on(LocalDate.of(year, 1, 1)),
                        amount,
                        false
                ));
        // Model the latest observation as another point so one pipeline can sort all results.
        Stream<RatePoint> current = Stream.of(0)
                .map(ignored -> toPoint(client.latest(), amount, true));

        // Each rate call is independent, so the calls run in parallel.
        List<RatePoint> rates = Stream.concat(historical, current)
                .parallel()
                .sorted((left, right) -> left.rateDate().compareTo(right.rateDate()))
                .toList();

        return new RateHistory("DKK", "USD", amount, "Frankfurter", rates);
    }

    private static RatePoint toPoint(
            FrankfurterClient.Rate rate,
            BigDecimal amount,
            boolean current
    ) {
        // Keep conversion logic identical for historical and current observations.
        return new RatePoint(
                rate.date().getYear(),
                rate.date(),
                rate.rate(),
                convert(amount, rate.rate()),
                current
        );
    }

    // Service-level validation also protects callers that bypass the HTTP controller.
    private static BigDecimal checkAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Amount must be positive and have at most two decimal places"
            );
        }
        return amount.setScale(2);
    }

    private static BigDecimal convert(BigDecimal amount, BigDecimal rate) {
        // Use banker's rounding for the converted amount.
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_EVEN);
    }

    // Response model for the latest DKK/USD rate and one converted amount.
    public record CurrentRate(
            String baseCurrency,
            String quoteCurrency,
            BigDecimal baseAmount,
            BigDecimal rate,
            BigDecimal convertedAmount,
            LocalDate rateDate,
            String provider
    ) {
    }

    // One dated observation in the history response.
    public record RatePoint(
            int year,
            LocalDate rateDate,
            BigDecimal rate,
            BigDecimal convertedAmount,
            boolean current
    ) {
    }

    // Response model containing the requested amount and the ordered observations.
    public record RateHistory(
            String baseCurrency,
            String quoteCurrency,
            BigDecimal baseAmount,
            String provider,
            List<RatePoint> rates
    ) {
    }
}
