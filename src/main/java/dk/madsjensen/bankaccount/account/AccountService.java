package dk.madsjensen.bankaccount.account;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

// The account rules and transactions are kept together here.
@Service
public class AccountService {

    // Accounts in this API are single-currency accounts.
    private static final String CURRENCY = "DKK";

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Account create(String firstName, String lastName) {
        // Remove UUID separators and keep a short prefix so account numbers are URL-friendly.
        String number = "ACC-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
        // Normalizing surrounding whitespace keeps stored customer names consistent.
        return repository.save(new Account(number, firstName.trim(), lastName.trim()));
    }

    @Transactional
    public Account deposit(String accountNumber, BigDecimal amount, String currency) {
        // Validate input before mutating the managed JPA entity.
        checkCurrency(currency);
        Account account = find(accountNumber);
        account.deposit(checkAmount(amount));
        // JPA dirty checking persists the new balance when the transaction commits.
        return account;
    }

    // readOnly documents the intent and lets the persistence provider avoid write work.
    @Transactional(readOnly = true)
    public Account balance(String accountNumber) {
        return find(accountNumber);
    }

    @Transactional
    public TransferResult transfer(
            String fromAccountNumber,
            String toAccountNumber,
            BigDecimal requestedAmount,
            String currency
    ) {
        // A self-transfer has no useful effect and would obscure the transaction semantics.
        if (fromAccountNumber.equals(toAccountNumber)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sender and receiver must be different accounts"
            );
        }

        checkCurrency(currency);
        BigDecimal amount = checkAmount(requestedAmount);
        Account from = find(fromAccountNumber);
        Account to = find(toAccountNumber);

        if (from.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient funds");
        }

        // Both changes commit together; an exception rolls back both managed entities.
        from.withdraw(amount);
        to.deposit(amount);
        return new TransferResult(from, to, amount);
    }

    // Translate a persistence-level empty result into the API's 404 response.
    private Account find(String accountNumber) {
        return repository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found: " + accountNumber
                ));
    }

    // equalsIgnoreCase accepts common casing variants without accepting another currency.
    private static void checkCurrency(String currency) {
        if (!CURRENCY.equalsIgnoreCase(currency)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only DKK is supported");
        }
    }

    // Central validation keeps deposits and transfers on exactly the same money rules.
    private static BigDecimal checkAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Amount must be greater than zero"
            );
        }
        try {
            // Reject extra decimals instead of rounding the amount.
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Amount can have at most two decimal places"
            );
        }
    }

    // Carries the updated managed entities back to the controller for response mapping.
    public record TransferResult(Account from, Account to, BigDecimal amount) {
    }
}
