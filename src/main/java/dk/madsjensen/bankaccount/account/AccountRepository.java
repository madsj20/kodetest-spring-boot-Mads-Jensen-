package dk.madsjensen.bankaccount.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

// Account number is the only custom lookup required for this task.
public interface AccountRepository extends JpaRepository<Account, UUID> {

    // Spring Data derives the query from the method name; no SQL is required here.
    Optional<Account> findByAccountNumber(String accountNumber);
}
