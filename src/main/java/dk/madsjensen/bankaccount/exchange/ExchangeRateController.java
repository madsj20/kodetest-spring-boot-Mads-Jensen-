package dk.madsjensen.bankaccount.exchange;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

// Exposes read-only endpoints for current and historical DKK/USD conversions.
@Validated
@RestController
@RequestMapping("/api/v1/exchange-rates/dkk-usd")
public class ExchangeRateController {

    private final ExchangeRateService service;

    public ExchangeRateController(ExchangeRateService service) {
        this.service = service;
    }

    // GET /api/v1/exchange-rates/dkk-usd returns the newest provider rate.
    @GetMapping
    public ExchangeRateService.CurrentRate current(
            // The amount is optional, but supplied values must follow the API's money rules.
            @RequestParam(defaultValue = "100.00")
            @DecimalMin("0.01")
            @Digits(integer = 17, fraction = 2)
            BigDecimal amount
    ) {
        return service.current(amount);
    }

    // GET /history returns the required yearly observations plus the current rate.
    @GetMapping("/history")
    public ExchangeRateService.RateHistory history(
            // Method validation runs because the controller is annotated with @Validated.
            @RequestParam(defaultValue = "100.00")
            @DecimalMin("0.01")
            @Digits(integer = 17, fraction = 2)
            BigDecimal amount
    ) {
        return service.history(amount);
    }
}
