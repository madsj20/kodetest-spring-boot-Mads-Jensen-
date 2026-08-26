package dk.madsjensen.bankaccount.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;

// Spring uses this class to receive HTTP requests about accounts.
@RestController
// All endpoints in this class start with /api/v1.
@RequestMapping("/api/v1")
public class AccountController {

    // The service handles business rules and database work.
    private final AccountService service;

    // Spring automatically gives the controller an AccountService here.
    public AccountController(AccountService service) {
        this.service = service;
    }

    // Handles POST /api/v1/accounts.
    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        // @RequestBody converts the incoming JSON to CreateAccountRequest.
        // @Valid checks the rules on the request before this method runs.

        // The service creates the account, which is then mapped to the JSON response model.
        AccountResponse response = AccountResponse.from(
                service.create(request.firstName(), request.lastName())
        );

        // A 201 response should tell the client where the new resource can be found.
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{accountNumber}/balance")
                .buildAndExpand(response.accountNumber())
                .toUri();

        // Return 201 Created, the Location header and the new account as JSON.
        return ResponseEntity.created(location).body(response);
    }

    // Handles POST /api/v1/accounts/{accountNumber}/deposits.
    @PostMapping("/accounts/{accountNumber}/deposits")
    public BalanceResponse deposit(
            // @PathVariable gets the account number from the URL.
            @PathVariable String accountNumber,
            // The amount and currency come from the JSON request body.
            @Valid @RequestBody DepositRequest request
    ) {
        // The service validates and saves the deposit.
        return BalanceResponse.from(
                service.deposit(accountNumber, request.amount(), request.currency())
        );
    }

    // Handles GET /api/v1/accounts/{accountNumber}/balance.
    @GetMapping("/accounts/{accountNumber}/balance")
    public BalanceResponse balance(@PathVariable String accountNumber) {
        // A GET request only reads data, so there is no request body.
        return BalanceResponse.from(service.balance(accountNumber));
    }

    // Handles POST /api/v1/transfers.
    @PostMapping("/transfers")
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        // The service performs both balance changes inside one transaction.
        AccountService.TransferResult result = service.transfer(
                request.fromAccountNumber(),
                request.toAccountNumber(),
                request.amount(),
                request.currency()
        );

        // Build a response with the updated balances for both accounts.
        return new TransferResponse(
                result.from().getAccountNumber(),
                result.from().getBalance(),
                result.to().getAccountNumber(),
                result.to().getBalance(),
                result.amount(),
                "DKK"
        );
    }

    // DTOs (Data Transfer Objects) are used to define the JSON structure for requests and responses.

    // Request model for the create-account JSON body.
    public record CreateAccountRequest(
            // Names are required and limited so they fit the database columns.
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName
    ) {
    }

    // Request model for the deposit JSON body.
    public record DepositRequest(
            // Money must be positive and can have no more than two decimals.
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotBlank String currency
    ) {
    }

    // Request model for the transfer JSON body.
    public record TransferRequest(
            @NotBlank String fromAccountNumber,
            @NotBlank String toAccountNumber,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotBlank String currency
    ) {
    }

    // Response model returned after an account has been created.
    public record AccountResponse(
            String accountNumber,
            String firstName,
            String lastName,
            BigDecimal balance,
            String currency
    ) {
        // Convert the database entity to the fields exposed by the API.
        static AccountResponse from(Account account) {
            return new AccountResponse(
                    account.getAccountNumber(),
                    account.getFirstName(),
                    account.getLastName(),
                    account.getBalance(),
                    "DKK"
            );
        }
    }

    // Smaller response model used when only the balance is needed.
    public record BalanceResponse(String accountNumber, BigDecimal balance, String currency) {
        // Do not return the complete Account entity to the client.
        static BalanceResponse from(Account account) {
            return new BalanceResponse(account.getAccountNumber(), account.getBalance(), "DKK");
        }
    }

    // Response model containing the result of a completed transfer.
    public record TransferResponse(
            String fromAccountNumber,
            BigDecimal fromBalance,
            String toAccountNumber,
            BigDecimal toBalance,
            BigDecimal transferredAmount,
            String currency
    ) {
    }
}
