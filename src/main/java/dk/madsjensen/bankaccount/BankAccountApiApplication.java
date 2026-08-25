package dk.madsjensen.bankaccount;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Marks the root package for component scanning and enables Spring Boot auto-configuration.
@SpringBootApplication
public class BankAccountApiApplication {

	// Application entry point used both by the Maven plugin and a packaged JAR.
	public static void main(String[] args) {
		SpringApplication.run(BankAccountApiApplication.class, args);
	}

}
