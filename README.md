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

Requirements: Java 17, Maven, and Docker (for PostgreSQL).

From the project root:

```bash
docker compose down
docker compose up -d
mvn spring-boot:run
```

The API listens on `http://localhost:8080`. Hibernate creates `accounts` and `transactions` on startup (`JPA_DDL_AUTO` defaults to `update`), including:

- `balance >= 0` on accounts
- `amount > 0` and type `CREDIT` or `DEBIT` on transactions
- a unique `(account_id, reference_id)` constraint
- a foreign key from each transaction to its account

Swagger UI: `http://localhost:8080/swagger-ui.html`  
OpenAPI JSON: `http://localhost:8080/v3/api-docs`

To use an existing PostgreSQL database instead of Docker:

```bash
export DB_URL='jdbc:postgresql://127.0.0.1:5432/ledger?sslmode=disable'
export DB_USERNAME='ledger'
export DB_PASSWORD='ledger'
export SERVER_PORT=8080
mvn spring-boot:run
```

`src/main/resources/db/schema.sql` is the same schema if you prefer to create the tables yourself. After running that script, start the API with `JPA_DDL_AUTO=validate`.

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

Transaction body:

```json
{
  "type": "CREDIT",
  "amount": 100.00,
  "description": "Opening deposit",
  "referenceId": "REF-1"
}
```

`type` is `CREDIT` or `DEBIT`. `amount` must be greater than 0 and have at most 2 decimal places. `referenceId` is required and unique per account. The same `referenceId` may be used on a different account. `description` is optional.

History query parameters:

| Parameter | Meaning |
| --- | --- |
| `type` | Optional. `CREDIT` or `DEBIT`. Omit it to return both. |
| `page` | Current page. Starts at `1`. Default `1`. |
| `limit` | Page size, from `1` to `100`. Default `10`. |

History response:

```json
{
  "transactions": [],
  "page": 1,
  "pageSize": 10,
  "totalRecords": 0,
  "totalPages": 0
}
```

Transactions are sorted by `createdAt` descending, then by `id` descending.

Transfer body:

```json
{
  "fromAccountId": 1,
  "toAccountId": 2,
  "amount": 30.00,
  "description": "Payment",
  "referenceId": "TRF-1"
}
```

The source account is debited and the destination account is credited with the same amount and the same `referenceId`. Both ledger rows and both balance updates commit together. If either account is missing, the reference is already used on either account, or the source balance is too low, neither balance changes.

| Status | When |
| --- | --- |
| `201` | Account, transaction, or transfer created |
| `200` | Balance or history |
| `400` | Invalid body, type, amount, pagination, or a transfer to the same account |
| `404` | Unknown account |
| `409` | `referenceId` already used on that account |
| `422` | Debit or transfer larger than the source balance |

Error body:

```json
{
  "timestamp": "2026-10-29T08:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/accounts",
  "fieldErrors": [
    { "field": "name", "message": "Account name is required" }
  ]
}
```

`fieldErrors` is omitted when the error is not a field validation error.

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

Worked example from an empty database:

| Step | Account A | Account B |
| --- | ---: | ---: |
| Create | 0 | 0 |
| Credits | 100 | 50 |
| Debit 30 from A | 70 | 50 |
| Credit 10 to B with reference `REF-A2` | 70 | 60 |
| Transfer 30 from A to B | 40 | 90 |

The rejected debit of 150 and the rejected transfer of 1000 do not change these balances.

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
