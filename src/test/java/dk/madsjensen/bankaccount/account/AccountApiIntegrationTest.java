package dk.madsjensen.bankaccount.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Starts the real Spring context and exercises HTTP, validation, service and persistence together.
@SpringBootTest
@AutoConfigureMockMvc
class AccountApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService service;

    @Autowired
    private AccountRepository repository;

    // Isolate every test from rows created by the previous test.
    @BeforeEach
    void clearDatabase() {
        repository.deleteAll();
    }

    @Test
    void createsAnAccount() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Ada","lastName":"Lovelace"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.endsWith("/balance")
                ))
                .andExpect(jsonPath("$.accountNumber").value(
                        org.hamcrest.Matchers.startsWith("ACC-")
                ))
                .andExpect(jsonPath("$.balance").value(0.0))
                .andExpect(jsonPath("$.currency").value("DKK"));
    }

    @Test
    void depositsAndReadsTheBalance() throws Exception {
        Account account = service.create("Ada", "Lovelace");

        mockMvc.perform(post(
                        "/api/v1/accounts/{accountNumber}/deposits",
                        account.getAccountNumber()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":125.50,"currency":"DKK"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(125.50));

        mockMvc.perform(get(
                        "/api/v1/accounts/{accountNumber}/balance",
                        account.getAccountNumber()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(125.50));
    }

    @Test
    void transfersMoney() throws Exception {
        Account from = service.create("Ada", "Lovelace");
        Account to = service.create("Grace", "Hopper");
        service.deposit(from.getAccountNumber(), new BigDecimal("100.00"), "DKK");

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fromAccountNumber":"%s",
                                  "toAccountNumber":"%s",
                                  "amount":40.00,
                                  "currency":"DKK"
                                }
                                """.formatted(from.getAccountNumber(), to.getAccountNumber())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromBalance").value(60.00))
                .andExpect(jsonPath("$.toBalance").value(40.00));
    }

    @Test
    void failedTransferDoesNotChangeEitherBalance() throws Exception {
        Account from = service.create("Ada", "Lovelace");
        Account to = service.create("Grace", "Hopper");
        service.deposit(from.getAccountNumber(), new BigDecimal("20.00"), "DKK");

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fromAccountNumber":"%s",
                                  "toAccountNumber":"%s",
                                  "amount":50.00,
                                  "currency":"DKK"
                                }
                                """.formatted(from.getAccountNumber(), to.getAccountNumber())))
                .andExpect(status().isConflict());

        // Verify the transaction was atomic, including the receiver's untouched balance.
        assertEquals(
                new BigDecimal("20.00"),
                service.balance(from.getAccountNumber()).getBalance()
        );
        assertEquals(
                new BigDecimal("0.00"),
                service.balance(to.getAccountNumber()).getBalance()
        );
    }

    @Test
    void rejectsInvalidAmounts() throws Exception {
        Account account = service.create("Ada", "Lovelace");

        mockMvc.perform(post(
                        "/api/v1/accounts/{accountNumber}/deposits",
                        account.getAccountNumber()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":-1.00,"currency":"DKK"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
