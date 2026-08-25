# Code Test

## Requirements

- Java 21
- Git
- Internet access when Maven downloads dependencies for the first time
- Internet access when calling the exchange-rate endpoints

No external database or API key is required. Maven is included through the wrapper, and H2 runs in memory.

## Run locally

Open PowerShell in the root folder of the project. Run the tests first, then start the application.

### Test Windows
```powershell
.\mvnw.cmd clean test
```
### Run Windows
```powershell
.\mvnw.cmd spring-boot:run
```

On macOS or Linux:

### Test macOS or Linux
```bash
./mvnw clean test
```
### Run macOS or Linux
```bash
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080`. Stop it with `Ctrl+C`.

The H2 database is recreated for each application lifecycle, so all accounts are deleted when the application stops.

## API endpoints

The base API path is `http://localhost:8080/api/v1`. Run the application before using the cURL examples below.

The examples use PowerShell and the Windows `curl.exe` command. A backtick continues a command on the next line and must be the final character on the line, with no spaces after it. The quotation marks inside each JSON body are escaped as `\"` so they are passed correctly to `curl.exe`. On macOS or Linux, use `curl` instead of `curl.exe`, replace each backtick with `\` and remove the backslashes before the JSON quotation marks.

| Operation | Method | Path |
|---|---|---|
| Create account | `POST` | `/api/v1/accounts` |
| Deposit | `POST` | `/api/v1/accounts/{accountNumber}/deposits` |
| Balance | `GET` | `/api/v1/accounts/{accountNumber}/balance` |
| Transfer | `POST` | `/api/v1/transfers` |
| Current DKK/USD rate | `GET` | `/api/v1/exchange-rates/dkk-usd?amount=100.00` |
| Historical DKK/USD rates | `GET` | `/api/v1/exchange-rates/dkk-usd/history?amount=100.00` |

### Create an account

```powershell
curl.exe --request POST "http://localhost:8080/api/v1/accounts" `
  --header "Content-Type: application/json" `
  --data '{\"firstName\":\"John\",\"lastName\":\"Doe\"}'
```

The response uses `201 Created`, includes a `Location` header and returns the generated account number:

```json
{
  "accountNumber": "ACC-550E8400E29B",
  "firstName": "John",
  "lastName": "Doe",
  "balance": 0.00,
  "currency": "DKK"
}
```

Copy the returned `accountNumber` into the remaining examples.

### Deposit

Replace `<account-number>` with a generated account number:

```powershell
curl.exe --request POST "http://localhost:8080/api/v1/accounts/<account-number>/deposits" `
  --header "Content-Type: application/json" `
  --data '{\"amount\":250.00,\"currency\":\"DKK\"}'
```

Example response:

```json
{
  "accountNumber": "ACC-550E8400E29B",
  "balance": 250.00,
  "currency": "DKK"
}
```

### Balance

```powershell
curl.exe "http://localhost:8080/api/v1/accounts/<account-number>/balance"
```

Example response:

```json
{
  "accountNumber": "ACC-550E8400E29B",
  "balance": 250.00,
  "currency": "DKK"
}
```

### Transfer

Create two accounts and deposit money into the sender first. Replace both placeholders with generated account numbers:

```powershell
curl.exe --request POST "http://localhost:8080/api/v1/transfers" `
  --header "Content-Type: application/json" `
  --data '{\"fromAccountNumber\":\"<sender-account-number>\",\"toAccountNumber\":\"<receiver-account-number>\",\"amount\":75.25,\"currency\":\"DKK\"}'
```

Example response:

```json
{
  "fromAccountNumber": "ACC-550E8400E29B",
  "toAccountNumber": "ACC-6BA7B8109DAD",
  "fromBalance": 174.75,
  "toBalance": 75.25,
  "transferredAmount": 75.25,
  "currency": "DKK"
}
```

## Bonus tasks

Exchange rates come from the public [Frankfurter API](https://frankfurter.dev/), which does not require an API key.

### Current DKK/USD rate

The optional `amount` query parameter defaults to `100.00` DKK:

```powershell
curl.exe "http://localhost:8080/api/v1/exchange-rates/dkk-usd?amount=100.00"
```

Example response values are illustrative because the rate changes over time:

```json
{
  "baseCurrency": "DKK",
  "quoteCurrency": "USD",
  "baseAmount": 100.00,
  "rate": 0.14325,
  "convertedAmount": 14.32,
  "rateDate": "2026-08-24",
  "provider": "Frankfurter"
}
```

### Historical DKK/USD rates

```powershell
curl.exe "http://localhost:8080/api/v1/exchange-rates/dkk-usd/history?amount=100.00"
```

The history endpoint returns:

- January 1 rates for 2005-2015
- No entry for 2012
- The current rate as an extra entry
- Converted values based on 100 DKK by default
- The actual observation date returned by the provider

The independent historical HTTP calls use a parallel stream to reduce response time.

The response structure is:

```json
{
  "baseCurrency": "DKK",
  "quoteCurrency": "USD",
  "baseAmount": 100.00,
  "provider": "Frankfurter",
  "rates": [
    {
      "year": 2005,
      "rateDate": "2005-01-01",
      "rate": 0.17650,
      "convertedAmount": 17.65,
      "current": false
    },
    {
      "year": 2026,
      "rateDate": "2026-08-24",
      "rate": 0.14325,
      "convertedAmount": 14.32,
      "current": true
    }
  ]
}
```

## Validation and error handling

- Only `DKK` is accepted for account operations.
- Amounts must be positive and have no more than two decimal places.
- Sender and receiver must be different accounts.
- A transfer requires sufficient funds.
- Names and account numbers must not be blank.

Expected failures use standard HTTP statuses:

| Status | Meaning |
|---|---|
| `400 Bad Request` | Invalid amount, currency or request data |
| `404 Not Found` | Account number does not exist |
| `409 Conflict` | Insufficient funds, self-transfer or concurrent account update |
| `502 Bad Gateway` | The exchange-rate provider failed or returned invalid data |

Errors use Spring's Problem Details JSON format. Example:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Insufficient funds",
  "instance": "/api/v1/transfers"
}
```

## Design decisions

- Money is represented by `BigDecimal`, never `double`.
- Accounts start at `0.00 DKK`, and only positive amounts with at most two decimals are accepted.
- Transfers use one `@Transactional` service method, so both balance changes succeed or both roll back.
- `@Version` provides basic protection against lost updates from concurrent requests.
- Constructor injection makes component dependencies explicit and testable.
- `FrankfurterClient` isolates the third-party provider from the API and conversion logic.
- H2 keeps the project self-contained; data is reset when the application restarts.
- Request and response records are kept inside the controllers to avoid a large number of tiny files.
- Business failures use ordinary HTTP statuses: `400`, `404` and `409`.
- Exchange-rate calculations use `RoundingMode.HALF_EVEN`.

## Structure

```text
src/main/java/dk/madsjensen/bankaccount/
  account/
    Account.java
    AccountController.java
    AccountRepository.java
    AccountService.java
  common/
    ApiExceptionHandler.java
  exchange/
    ExchangeRateController.java
    ExchangeRateService.java
    FrankfurterClient.java
  BankAccountApiApplication.java
```

The small structure is intentional: this is a timeboxed code challenge, not a production banking platform.

## Tests

The eight tests cover:

- application startup;
- account creation;
- deposits and balance lookup;
- successful transfers;
- rollback when funds are insufficient;
- invalid amounts;
- current exchange-rate conversion;
- the required historical years, including exclusion of 2012.

```powershell
.\mvnw.cmd clean test
```

The exchange-rate unit tests mock the provider, so the normal test run does not require internet access.

## Assumptions and limitations

- Account numbers are generated mock identifiers, not real bank-account numbers.
- Only DKK accounts are supported.
- January 1 is requested for each historical year; actual observation dates depend on the provider.
- Authentication, authorization, an audit log, idempotency and a real ledger are outside this timeboxed solution.
- A production version would use a persistent database and database migrations instead of in-memory H2 and `create-drop`.

All names and account data in examples and tests are fictitious.
