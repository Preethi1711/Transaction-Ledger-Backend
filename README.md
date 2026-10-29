# Transaction Ledger API

Backend API for a transaction ledger. It creates accounts, records immutable credit and debit transactions, returns the current balance and transaction history, and transfers money between two accounts in one database transaction.

## Stack

- Java 17
- Spring Boot 3 (Web, Data JPA, Bean Validation)
- PostgreSQL
- Maven
- JUnit and Mockito
- Swagger UI for the API documentation

Tests run against an in-memory H2 database and do not need PostgreSQL.

## Run locally

Requirements: Java 17 and Maven. Use either Docker or Supabase for PostgreSQL. Do not set both at once. If `DB_URL` is set in the shell, the API uses that database instead of the Docker one.

The API listens on `http://localhost:8080`.

Swagger UI: `http://localhost:8080/swagger-ui.html`  
OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### Docker Compose

From the project root. This starts PostgreSQL from `docker-compose.yml` (database `ledger`, user `ledger`, password `ledger`).

```bash
docker compose down
docker compose up -d
mvn spring-boot:run
```

### Supabase

Create a `.env` file in the project root. Do not commit it. Use the connection values from the Supabase project settings.

```bash
DB_URL=jdbc:postgresql://<host>:5432/postgres?sslmode=require
DB_USERNAME=<supabase-user>
DB_PASSWORD=<supabase-password>
```

Load that file, then start the API. Docker is not required for this option.

```bash
set -a
source .env
set +a
mvn spring-boot:run
```

## Tests

```bash
mvn test
```

## API

There is no update or delete endpoint. A transaction's type, amount, description, reference id, account id, and creation time stay as they were written.

| Method | Path | Result |
| --- | --- | --- |
| `POST` | `/api/accounts` | `201` account created. Body: `{ "name": "Account A" }`. Balance is always `0`. |
| `GET` | `/api/accounts/{accountId}/balance` | `200` `{ "accountId", "balance" }` |
| `POST` | `/api/accounts/{accountId}/transactions` | `201` credit or debit |
| `GET` | `/api/accounts/{accountId}/transactions` | `200` history, newest first |
| `POST` | `/api/transfers` | `201` debit one account and credit another |

## Curl checks

Start the API first. These calls assume an empty database, so the first two accounts are ids `1` and `2`. Each command prints the JSON body and the HTTP status.

```bash
BASE=http://localhost:8080

# 1. Create accounts. A client-supplied balance is ignored; the balance is 0.
curl -sS -w '\nHTTP %{http_code}\n' -X POST "$BASE/api/accounts" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Account A","balance":999}'

curl -sS -w '\nHTTP %{http_code}\n' -X POST "$BASE/api/accounts" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Account B"}'

# 2. Credit 100 to Account A, then credit 50 to Account B.
curl -sS -w '\nHTTP %{http_code}\n' -X POST "$BASE/api/accounts/1/transactions" \
  -H 'Content-Type: application/json' \
  -d '{"type":"CREDIT","amount":100.00,"description":"Opening deposit","referenceId":"REF-A1"}'

curl -sS -w '\nHTTP %{http_code}\n' -X POST "$BASE/api/accounts/2/transactions" \
  -H 'Content-Type: application/json' \
  -d '{"type":"CREDIT","amount":50.00,"description":"Opening deposit","referenceId":"REF-B1"}'

# 3. Debit 30 from Account A. Balance becomes 70.
curl -sS -w '\nHTTP %{http_code}\n' -X POST "$BASE/api/accounts/1/transactions" \
  -H 'Content-Type: application/json' \
  -d '{"type":"DEBIT","amount":30.00,"description":"Withdrawal","referenceId":"REF-A2"}'

# 4. Reject a debit larger than the balance. Balance stays 70. Expect HTTP 422.
curl -sS -w '\nHTTP %{http_code}\n' -X POST "$BASE/api/accounts/1/transactions" \
  -H 'Content-Type: application/json' \
  -d '{"type":"DEBIT","amount":150.00,"description":"Too much","referenceId":"REF-A3"}'

# 5. Reject a duplicate reference on the same account. Balance stays 70. Expect HTTP 409.
curl -sS -w '\nHTTP %{http_code}\n' -X POST "$BASE/api/accounts/1/transactions" \
  -H 'Content-Type: application/json' \
  -d '{"type":"CREDIT","amount":10.00,"description":"Duplicate","referenceId":"REF-A1"}'

# 6. The same reference is allowed on a different account. Expect HTTP 201.
curl -sS -w '\nHTTP %{http_code}\n' -X POST "$BASE/api/accounts/2/transactions" \
  -H 'Content-Type: application/json' \
  -d '{"type":"CREDIT","amount":10.00,"description":"Shared reference","referenceId":"REF-A2"}'

# 7. Current balances. Account A is 70. Account B is 60.
curl -sS -w '\nHTTP %{http_code}\n' "$BASE/api/accounts/1/balance"
curl -sS -w '\nHTTP %{http_code}\n' "$BASE/api/accounts/2/balance"

# 8. History, newest first. Optional type filter and pagination.
curl -sS -w '\nHTTP %{http_code}\n' "$BASE/api/accounts/1/transactions?page=1&limit=10"
curl -sS -w '\nHTTP %{http_code}\n' "$BASE/api/accounts/1/transactions?type=CREDIT&page=1&limit=10"
curl -sS -w '\nHTTP %{http_code}\n' "$BASE/api/accounts/1/transactions?type=DEBIT&page=1&limit=10"

# 9. Invalid pagination and an unknown account. Expect HTTP 400 and HTTP 404.
curl -sS -w '\nHTTP %{http_code}\n' "$BASE/api/accounts/1/transactions?page=0&limit=10"
curl -sS -w '\nHTTP %{http_code}\n' "$BASE/api/accounts/1/transactions?page=1&limit=0"
curl -sS -w '\nHTTP %{http_code}\n' "$BASE/api/accounts/999999/balance"

# 10. Invalid transaction data. Expect HTTP 400.
curl -sS -w '\nHTTP %{http_code}\n' -X POST "$BASE/api/accounts" \
  -H 'Content-Type: application/json' \
  -d '{}'

curl -sS -w '\nHTTP %{http_code}\n' -X POST "$BASE/api/accounts/1/transactions" \
  -H 'Content-Type: application/json' \
  -d '{"type":"TRANSFER","amount":10,"referenceId":"BAD-TYPE"}'

curl -sS -w '\nHTTP %{http_code}\n' -X POST "$BASE/api/accounts/1/transactions" \
  -H 'Content-Type: application/json' \
  -d '{"type":"CREDIT","amount":0,"referenceId":"BAD-AMOUNT"}'

# 11. Transfer 30 from Account A (70) to Account B (60).
#     Account A becomes 40. Account B becomes 90. Expect HTTP 201.
curl -sS -w '\nHTTP %{http_code}\n' -X POST "$BASE/api/transfers" \
  -H 'Content-Type: application/json' \
  -d '{"fromAccountId":1,"toAccountId":2,"amount":30.00,"description":"Payment","referenceId":"TRF-1"}'

# 12. A transfer that cannot be completed does not change either balance. Expect HTTP 422.
curl -sS -w '\nHTTP %{http_code}\n' -X POST "$BASE/api/transfers" \
  -H 'Content-Type: application/json' \
  -d '{"fromAccountId":1,"toAccountId":2,"amount":1000.00,"description":"Too much","referenceId":"TRF-2"}'

curl -sS -w '\nHTTP %{http_code}\n' "$BASE/api/accounts/1/balance"
curl -sS -w '\nHTTP %{http_code}\n' "$BASE/api/accounts/2/balance"
```

If the database already has accounts, replace `1` and `2` with the `id` values returned by the create calls.

## Assumptions

- JSON uses camelCase (`createdAt`, `accountId`, `referenceId`, `fromAccountId`). The database columns use the assessment names (`created_at`, `account_id`, `reference_id`).
- Account ids and transaction ids are generated by the database. The client does not send them.
- A `balance` field sent when creating an account is ignored. The server always stores `0`.
- `description` may be omitted. When it is sent, it is stored and cannot be changed later.
- Money uses a decimal with up to 2 fraction digits. The stored balance is never negative.
- `page` starts at 1. `limit` defaults to 10 and cannot exceed 100.
- A duplicate `referenceId` returns `409`, does not insert a row, and does not change the balance.
- A transfer stores one `DEBIT` on the source account and one `CREDIT` on the destination account. Both rows use the caller's `referenceId`. That reference must be unused on both accounts.
- A transfer from an account to itself is rejected.
- The API has no authentication. The assessment does not require users or sessions.
- Account name is at most 100 characters. `referenceId` is at most 25 characters.
