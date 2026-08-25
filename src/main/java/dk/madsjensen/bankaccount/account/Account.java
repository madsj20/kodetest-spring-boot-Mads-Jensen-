package dk.madsjensen.bankaccount.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

// Balance changes stay inside this package so they go through AccountService.
@Entity
@Table(name = "accounts")
public class Account {

    // Internal database identifier; clients use accountNumber instead.
    @Id
    @GeneratedValue
    private UUID id;

    // The unique, human-readable identifier exposed by the REST API.
    @Column(nullable = false, unique = true, length = 16)
    private String accountNumber;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    // Precision and scale mirror the service's validation of monetary amounts.
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    // This stops concurrent balance updates from overwriting each other.
    @Version
    private long version;

    // JPA needs an empty constructor.
    protected Account() {
    }

    Account(String accountNumber, String firstName, String lastName) {
        this.accountNumber = accountNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        // New accounts always start with a zero balance in the account currency.
        this.balance = new BigDecimal("0.00");
    }

    // Big Decimal is used for money to avoid floating point rounding errors.
    void deposit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    // Package visibility ensures withdrawals are initiated by the account service.
    void withdraw(BigDecimal amount) {
        balance = balance.subtract(amount);
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}
