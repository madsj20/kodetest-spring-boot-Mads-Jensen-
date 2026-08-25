package dk.madsjensen.bankaccount.exchange;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Unit tests mock the remote provider so conversion rules stay fast and deterministic.
class ExchangeRateServiceTest {

    @Mock
    private FrankfurterClient client;

    private ExchangeRateService service;

    @BeforeEach
    void setUp() {
        // Construct the service directly; no Spring context is needed for these pure rules.
        MockitoAnnotations.openMocks(this);
        service = new ExchangeRateService(client);
    }

    @Test
    void convertsTheCurrentRate() {
        when(client.latest()).thenReturn(rate(
                LocalDate.of(2026, 8, 24),
                "0.14325"
        ));

        ExchangeRateService.CurrentRate result =
                service.current(new BigDecimal("100.00"));

        assertEquals(new BigDecimal("14.32"), result.convertedAmount());
        assertEquals("DKK", result.baseCurrency());
        assertEquals("USD", result.quoteCurrency());
    }

    @Test
    void returnsRequiredHistoricalYearsAndCurrentRate() {
        // Echo the requested date back from the mock to simulate each historical response.
        when(client.on(any(LocalDate.class))).thenAnswer(invocation -> {
            LocalDate date = invocation.getArgument(0);
            return rate(date, "0.15000");
        });
        when(client.latest()).thenReturn(rate(
                LocalDate.of(2026, 8, 24),
                "0.16000"
        ));

        ExchangeRateService.RateHistory result =
                service.history(new BigDecimal("100.00"));
        List<Integer> years = result.rates().stream()
                .map(ExchangeRateService.RatePoint::year)
                .toList();

        // The result must be complete, omit 2012 and remain chronological after parallel calls.
        assertEquals(11, result.rates().size());
        assertFalse(years.contains(2012));
        assertTrue(result.rates().stream().anyMatch(
                ExchangeRateService.RatePoint::current
        ));
        assertEquals(
                List.of(2005, 2006, 2007, 2008, 2009, 2010, 2011, 2013, 2014, 2015, 2026),
                years
        );
    }

    private static FrankfurterClient.Rate rate(LocalDate date, String value) {
        return new FrankfurterClient.Rate(
                date,
                "DKK",
                "USD",
                new BigDecimal(value)
        );
    }
}
