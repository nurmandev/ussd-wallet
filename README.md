# USSD Wallet Backend

This Java 21 / Spring Boot 3.5 application implements customer registration,
wallet creation, transaction PIN security, and PIN-authorized wallet transfers
for the USSD wallet backend. Registration creates the customer, PIN-security
record, wallet, zero-balance projection, and wallet liability ledger account
in one PostgreSQL transaction. Registration does not fund a wallet.

## Requirements

- Java 21
- PostgreSQL 16+
- Maven (or the included Maven wrapper)

## Configuration and startup

Copy `.env.example` to `.env` and set your PostgreSQL connection settings and
a private `PIN_PEPPER` secret. Spring Boot imports this file at startup;
environment variables already set by the shell take precedence. `.env` is
ignored by Git and must never be committed. Generate a unique, random pepper
of at least 32 characters for each deployment and store it in a secrets
manager; losing or changing the pepper makes existing PIN hashes unverifiable.
Do not use the example value in a real environment:

```dotenv
DB_URL=jdbc:postgresql://your-postgres-host:5432/your_database?sslmode=require
DB_USERNAME=your_database_user
DB_PASSWORD=your_database_password
PIN_PEPPER=replace_with_a_random_secret_at_least_32_characters_long
```

Generate a secret with a cryptographically secure random generator, for
example `openssl rand -base64 48`, then place it in the deployment secret
manager rather than source control.

Then start the application:

```sh
./mvnw spring-boot:run
```

Flyway applies the database migrations at startup. Hibernate validates the
schema and does not create or alter tables.

## Docker and Render deployment

The multi-stage `Dockerfile` builds the executable Spring Boot jar and runs it
as a non-root user on Java 21. The container listens on Render's `PORT`
environment variable (default `8080`) and exposes the sanitized
`/actuator/health` endpoint for health checks. Flyway applies migrations when
the app starts.

### Deploy to Render

1. Push this repository to GitHub and select **New + → Blueprint** in Render.
2. Connect the repository and let Render apply [`render.yaml`](./render.yaml).
   It provisions a private PostgreSQL database and a Docker web service.
3. Render generates `PIN_PEPPER` and `USSD_GATEWAY_SECRET`. Keep these values
   private and retain them across redeployments; changing the PIN pepper makes
   existing PINs unverifiable.
4. Wait for the database and service health check, then use the assigned
   `https://...onrender.com` URL.

The blueprint uses paid `starter` web and `basic-256mb` database plans in the
Oregon region; change the region and plans in `render.yaml` before creating
the Blueprint if needed. Database migrations run automatically on application
startup. The Render deployment disables the development simulator.

### Run locally with Docker Compose

Copy `.env.example` to `.env`, set unique `DB_USERNAME`, `DB_PASSWORD`,
`PIN_PEPPER`, and `USSD_GATEWAY_SECRET` values, then run:

```sh
docker compose up --build
```

Compose starts PostgreSQL with a persistent named volume and waits for its
health check before starting the application. Open `http://localhost:8080`.
Stop the containers with `docker compose down`; database data remains in the
volume. To delete local database data as well, run `docker compose down -v`.

The app accepts either the existing `DB_URL` JDBC setting or separate
`DB_HOST`, `DB_PORT`, and `DB_NAME` settings. Never commit `.env` or use
development secrets in a deployed environment.

Optional wallet and registration settings:

| Variable | Default | Description |
| --- | --- | --- |
| `REGISTRATION_INITIAL_CUSTOMER_STATUS` | `PENDING` | `PENDING` by default; `ACTIVE` is an explicit opt-in and must only be used if the registration policy permits unverified activation. |
| `WALLET_NUMBER_PREFIX` | `10` | Numeric wallet number prefix. |
| `WALLET_NUMBER_LENGTH` | `10` | Total wallet number length, including prefix. |
| `WALLET_NUMBER_MAX_RETRIES` | `5` | Maximum candidate attempts when a number already exists or a database uniqueness collision occurs. |
| `WALLET_CURRENCY` | `NGN` | Three-letter wallet currency. |
| `WALLET_BALANCE_MAX_REQUESTS_PER_MINUTE` | `30` | Maximum balance enquiries per customer across REST and USSD during a rolling one-minute window. |
| `REGISTRATION_RATE_LIMIT_MAX_REQUESTS` | `10` | Registration requests allowed per client address per window. |
| `REGISTRATION_RATE_LIMIT_WINDOW_SECONDS` | `60` | In-memory rate-limit window. |
| `PIN_PEPPER` | Required | Secret key used to HMAC the PIN before Argon2id hashing; at least 32 characters. |
| `USSD_GATEWAY_SECRET` | Required | Shared HMAC secret used to authenticate trusted gateway callbacks. |
| `USSD_SERVICE_CODE` | `*123#` | Accepted dial code. |
| `USSD_SESSION_TIMEOUT_SECONDS` | `120` | USSD idle timeout. |
| `USSD_SESSION_RETENTION_SECONDS` | `604800` | Retention for completed/expired USSD sessions and callback records. |
| `USSD_MAX_REQUESTS_PER_MINUTE` | `30` | Per-process callback rate limit per normalized phone number. |
| `USSD_SIMULATOR_ENABLED` | `false` | Enables the development simulator; the controller is absent with the `prod` profile. |
| `USSD_SIMULATOR_SECRET` | Required if enabled | Separate local simulator API secret. |
| `TRANSFER_MINIMUM_AMOUNT` | `0.01` | Minimum wallet transfer amount. |
| `TRANSFER_MAXIMUM_AMOUNT` | `1000000.00` | Maximum wallet transfer amount. |
| `TRANSFER_DAILY_AMOUNT_LIMIT` | `2000000.00` | Maximum sum of successful sender transfers per UTC day. |
| `TRANSFER_DAILY_COUNT_LIMIT` | `20` | Maximum successful sender transfers per UTC day. |
| `TRANSFER_INTENT_EXPIRY_SECONDS` | `300` | Time allowed to confirm a validated transfer. |
| `TRANSFER_FEE` | `0.00` | Configured internal transfer fee. Non-zero fees are rejected until fee-ledger posting is implemented. |
| `TRANSFER_INTENT_EXPIRY_SCAN_MILLIS` | `60000` | Background scan interval for marking expired intents. |

The local registration endpoint is intentionally public because customer
authentication is not implemented yet. CSRF is exempted only for this public
registration route; protected routes retain Spring Security's CSRF protection.
It has request validation and a per-process, per-remote-address rate limit. This in-memory limiter is for the
local demo only; production deployments need a shared rate limiter and
appropriate gateway controls. Do not trust proxy-supplied client addresses
unless the proxy is explicitly trusted and configured.

Submitting a phone number does **not** prove ownership. The customer is created
with `PENDING` status by default, and `phone_verified_at` remains unset. The
database has an explicit verification timestamp to support a future OTP or
verified-USSD flow. No default PIN is generated or stored.

## Registration API

`POST /api/v1/customers/register`

Example request:

```sh
curl -X POST http://localhost:8080/api/v1/customers/register \
  -H 'Content-Type: application/json' \
  -d '{"phoneNumber":"08012345678"}'
```

Successful response (`201 Created`):

```json
{
  "success": true,
  "message": "Customer registered and wallet created successfully",
  "data": {
    "customerId": "f57e3d26-a70c-4a63-8a12-b11fba3bc0f7",
    "phoneNumber": "+2348012345678",
    "customerStatus": "PENDING",
    "wallet": {
      "walletId": "c29a8ef4-b7bd-4ea6-9a9e-4d166251de3d",
      "walletNumber": "1028347195",
      "currency": "NGN",
      "status": "ACTIVE",
      "balance": {
        "ledgerBalance": 0.00,
        "availableBalance": 0.00,
        "reservedBalance": 0.00
      }
    },
    "pinSetupRequired": true,
    "registeredAt": "2026-10-28T12:00:00Z"
  }
}
```

Phone numbers are parsed using Google's libphonenumber library and normalized
to Nigerian E.164 form. A duplicate number returns `409 Conflict`; malformed
input returns `400 Bad Request`. Responses use the same `success`, `message`,
and `data` envelope and do not expose credentials or database errors.

Springdoc API documentation is available at
`http://localhost:8080/swagger-ui/index.html`.

## Database records and atomicity

Flyway migration V1 creates `customers`, `customer_security`, `wallets`,
`wallet_balances`, and `ledger_accounts`; V2 adds PIN audit and recovery
authorization records; V3 adds transfers and double-entry transfer records.
V4 adds persistent USSD sessions, session lifecycle events, and callback replay
records; V5 adds USSD optimistic-lock versions; V6 adds balance-enquiry audit
and rate-limit records; V7 adds transfer intents, metadata, transfer audit,
and recipient-lookup rate limits.
Database unique constraints protect
phone numbers, wallet numbers, one-wallet-per-customer, and one-ledger-account
per wallet. If creation of any linked record fails, the transaction rolls back.
The phone-number uniqueness constraint remains authoritative for concurrent
duplicate requests.

## Automatic wallet creation

Successful registration creates exactly one wallet for the customer, then
initializes its `wallet_balances` row with zero `BigDecimal` values and creates
one `LIABILITY` ledger account. No ledger transaction, ledger entry, or funding
record is created. The numeric wallet number has a configurable prefix and total
length (`10` plus eight random digits by default); its digits are generated
with `SecureRandom`. Existing-number checks are retried, while database unique
constraints remain authoritative for concurrent collisions. A collision retry
restarts the complete registration transaction after rollback.

The wallet row has a unique customer foreign key, and the balance and ledger
account each have a unique wallet association. Wallet transfers acquire
wallet/customer and balance row locks in a stable order and retain optimistic
balance versioning.

## Transaction PIN security

Transaction PINs are configured separately after customer registration. PINs
must be 4–6 ASCII digits, the confirmation must match, and common/repeated/
sequential values are rejected. A keyed HMAC-SHA-256 using the required
deployment pepper is passed to Argon2id; Argon2id generates a new random salt
for each hash. The application stores only the resulting Argon2id hash; PINs,
pepper material, and hashes are never included in responses or security audit
events.

The existing `customer_security` record is extended to reference its wallet
and to persist failed attempts, lockout expiry, and last successful
verification. A PostgreSQL row lock serializes verification attempts. Five
consecutive failures lock verification for 15 minutes by default. Successful
verification resets the counter; once the lock expires, a new attempt can
proceed. PIN setup, change, verification, status, and recovery are only
available to an authenticated `CustomerPrincipal`; a client-supplied customer
or wallet ID is never used to select the owner.

Configuration can be overridden with environment variables:

| Variable | Default | Description |
| --- | --- | --- |
| `PIN_MAX_FAILED_ATTEMPTS` | `5` | Consecutive PIN failures before locking. |
| `PIN_LOCKOUT_MINUTES` | `15` | Temporary lockout duration. |
| `PIN_ARGON2_SALT_LENGTH` | `16` | Per-hash random salt length in bytes. |
| `PIN_ARGON2_HASH_LENGTH` | `32` | Argon2 output length in bytes. |
| `PIN_ARGON2_PARALLELISM` | `1` | Argon2 parallelism. |
| `PIN_ARGON2_MEMORY_KIB` | `65536` | Argon2 memory cost in KiB. |
| `PIN_ARGON2_ITERATIONS` | `3` | Argon2 iteration cost. |
| `PIN_RECOVERY_TOKEN_MINUTES` | `5` | Lifetime of a one-time reset authorization. |
| `PIN_PEPPER` | Required | Unique secret for HMAC pre-hashing before Argon2id. |

Authenticated API endpoints:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/wallet/pin/setup` | Set a PIN once: `{"pin":"4829","confirmPin":"4829"}` |
| `POST` | `/api/v1/wallet/pin/verify` | Verify: `{"pin":"4829"}` |
| `POST` | `/api/v1/wallet/pin/change` | Change with current, new, and confirmation PINs. |
| `GET` | `/api/v1/wallet/pin/status` | Read configured/locked/lock-expiry status only. |
| `POST` | `/api/v1/wallet/pin/reset/request` | Request OTP-based recovery. |
| `POST` | `/api/v1/wallet/pin/reset/verify` | Verify OTP and receive a short-lived reset authorization. |
| `POST` | `/api/v1/wallet/pin/reset/complete` | Set a new PIN using that one-time authorization. |

PIN failures return a generic invalid-PIN response; a locked account returns
HTTP `423 Locked`. Setup/change/reset requests never return PIN material.
Audit events record the customer, wallet, event type, and time only. PIN
changes and resets invalidate outstanding recovery authorizations and call the
OTP provider's challenge-invalidation operation.

PIN reset is deliberately **disabled unless an OTP provider is integrated**.
The `OtpVerificationService` contract requires a provider to bind challenges to
the authenticated customer and `TRANSACTION_PIN_RECOVERY` purpose, persist
expiry and attempt limits, rate-limit requests, consume a valid OTP atomically,
and never log or return the OTP. Until then the reset request returns `503
Service Unavailable`; the application does not issue demo OTPs or pretend
identity verification is active. After a provider verifies an OTP, the API
issues a random, short-lived, one-time recovery token and stores only its
SHA-256 digest.

The only authentication currently configured in this demo is Spring Security
HTTP Basic; there is no customer credential/token issuance module that creates
a trusted `CustomerPrincipal`. Consequently these endpoints reject ordinary
principals. A production customer authentication provider must be integrated
before customers or a USSD gateway can use them. The transfer service invokes
`PinVerificationService` before balance locks or financial changes, but does
not replace customer authentication or a wallet-funding/deposit integration.

## Internal wallet transfers

The two-stage API is rooted at `/api/v1/transfers`. All endpoints require a
trusted `CustomerPrincipal`; the authenticated customer is always the sender.
Use these steps:

1. `GET /api/v1/wallet/lookup/{walletNumber}` checks recipient eligibility and
   returns a masked name, wallet number, status, and currency only.
2. `POST /api/v1/transfers/validate` with the recipient wallet number, amount,
   optional description, and a client-generated UUID `idempotencyKey` creates
   an immutable, expiring intent. It does not reserve funds.
3. Show the returned recipient, amount, fee, and total debit for confirmation.
4. `POST /api/v1/transfers/confirm` with the intent ID, the same idempotency
   key, and the transaction PIN revalidates current wallet state, available
   balance, limits, and PIN before posting.
5. `GET /api/v1/transfers/history?page=0&size=20` lists only transactions
   involving the authenticated wallet. `GET /api/v1/transfers/{reference}` and
   `GET /api/v1/transfers/{reference}/receipt` return receipt details only to
   one of the transaction's wallet owners.
6. `DELETE /api/v1/transfers/intents/{intentId}` cancels an uncompleted intent.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/wallet/lookup/{walletNumber}` | Check recipient eligibility and retrieve limited recipient details. |
| `POST` | `/api/v1/transfers/validate` | Validate a transfer and create a confirmation intent. |
| `POST` | `/api/v1/transfers/confirm` | Verify the transaction PIN and execute the intent. |
| `DELETE` | `/api/v1/transfers/intents/{intentId}` | Cancel a pending intent. |
| `GET` | `/api/v1/transfers/history?page=0&size=20` | List completed transfers sent or received by the customer. |
| `GET` | `/api/v1/transfers/{reference}` | Retrieve a transfer receipt. |
| `GET` | `/api/v1/transfers/{reference}/receipt` | Retrieve the same transfer receipt explicitly. |

Example validation request:

```sh
curl -X POST http://localhost:8080/api/v1/transfers/validate \
  -H 'Content-Type: application/json' \
  -H 'Authorization: <authenticated customer credentials>' \
  -d '{
    "recipientWalletNumber": "1098765432",
    "amount": 5000.00,
    "description": "Personal transfer",
    "idempotencyKey": "90e6bf9e-4e5e-47a8-88e8-c5e53c7a6ebc"
  }'
```

The response is wrapped in the standard `success`, `message`, and `data`
envelope. Keep the returned `transferIntentId`, review the masked recipient
name and total debit with the customer, then submit the intent ID, transaction
PIN, and the same idempotency key:

```sh
curl -X POST http://localhost:8080/api/v1/transfers/confirm \
  -H 'Content-Type: application/json' \
  -H 'Authorization: <authenticated customer credentials>' \
  -d '{
    "transferIntentId": "<transfer intent UUID>",
    "transactionPin": "4829",
    "idempotencyKey": "90e6bf9e-4e5e-47a8-88e8-c5e53c7a6ebc"
  }'
```

Treat the PIN as sensitive input: send it only over TLS and never log or
persist it. A completed confirmation retry returns the original transfer
result. Reusing an idempotency key with different intent details is rejected.
Recipient lookup is rate-limited to 30 requests per customer per minute.

The request cannot set a sender ID, fee, total debit, status, or balance.
Transfer fees default to zero; nonzero fees are rejected until a fee/revenue
ledger posting path is available. The limit examples are configurable
development controls, not regulatory advice. Daily completed transfer amount
and count are checked again during posting, while the sender wallet row is
locked, so concurrent validations cannot authorize overspending or bypass the
daily controls. A transfer intent does not reserve funds; balance and wallet
state are rechecked when confirmed.

Posting remains centralized in `WalletTransferService`. It verifies PIN through
the existing lockout-aware PIN service, locks both wallets in sorted UUID order
and both balance rows, debits and credits in the same PostgreSQL transaction,
and creates one DEBIT and one CREDIT entry tied to the same reference. A
database uniqueness constraint on sender and idempotency key prevents duplicate
posting; reuse with different details is rejected. Successful transfer rows
are the posted transaction history; failed attempts are not presented as
completed transactions. A post-commit notification hook is installed and logs
only the transfer reference; no SMS/email provider is configured.

USSD uses the same transfer-intent service and posting service: recipient
lookup, recipient confirmation, amount, review, PIN, and execution. PINs are
never placed in persisted USSD session data. Duplicate callbacks return their
stored USSD response, and the transfer idempotency constraint is a second
protection against repeat debits.

Example intent request:

```json
{
  "recipientWalletNumber": "1098765432",
  "amount": 5000.00,
  "description": "Personal transfer",
  "idempotencyKey": "90e6bf9e-4e5e-47a8-88e8-c5e53c7a6ebc"
}
```

`POST /api/v1/wallet/transfers` remains as the existing authenticated,
PIN-protected synchronous transfer endpoint for compatibility. New REST clients
should use the intent workflow.

## Wallet retrieval

`GET /api/v1/wallets/me` returns the wallet associated with the authenticated
`CustomerPrincipal`. It accepts no customer ID from the request and does not
permit public lookup by customer UUID. Customer authentication/token issuance
is not implemented yet; the endpoint remains protected by Spring Security and
requires an authentication mechanism that supplies the trusted
`CustomerPrincipal`. A non-customer principal is rejected, and an authenticated
customer without a wallet receives `404 Not Found`.

Example request (requires the application's authenticated customer context):

```sh
curl http://localhost:8080/api/v1/wallets/me \
  -H 'Authorization: Bearer <customer-access-token>'
```

Successful response (`200 OK`):

```json
{
  "success": true,
  "message": "Wallet retrieved successfully",
  "data": {
    "walletId": "c29a8ef4-b7bd-4ea6-9a9e-4d166251de3d",
    "walletNumber": "1028347195",
    "currency": "NGN",
    "status": "ACTIVE",
    "balance": {
      "ledgerBalance": 0.00,
      "availableBalance": 0.00,
      "reservedBalance": 0.00
    }
  }
}
```

## Wallet balance enquiry

`GET /api/v1/wallet/balance` retrieves the authenticated customer's balance.
The customer identity comes only from the trusted `CustomerPrincipal`; the
request cannot supply a wallet/customer ID. The endpoint returns the
`ApiResponse` envelope and includes the wallet number, currency, ledger and
available balances, amount reserved for pending operations, wallet status,
and balance projection update time.

The wallet's `wallet_balances` row is the authoritative projection maintained
atomically by the existing transfer service. Completed transfers immediately
change the ledger and available balances. Pending balance is the existing
`reserved_balance`: it is already excluded from `available_balance`, so the
enquiry does not subtract it again. The current application only posts
internal transfers synchronously and does not have an asynchronous transaction
status or separate pending-transaction table; pending deposits/withdrawals and
provider transactions are therefore not included until those workflows exist.

The request is rate-limited per customer across both channels using a
PostgreSQL-backed one-minute counter, so opening new USSD sessions does not
bypass the customer limit. Successful, unavailable, missing-wallet, and
rate-limited enquiries are recorded in `wallet_balance_enquiry_audit` without
recording PINs, tokens, or the balance amounts. A frozen/closed wallet or a
customer that is not financially active cannot read the balance.

Example request (requires an authentication mechanism that supplies the
trusted customer principal):

```sh
curl http://localhost:8080/api/v1/wallet/balance \
  -H 'X-Correlation-ID: balance-request-1' \
  -H 'Authorization: <authenticated customer credentials>'
```

Successful response:

```json
{
  "success": true,
  "message": "Balance retrieved successfully",
  "data": {
    "walletNumber": "1028347195",
    "currency": "NGN",
    "ledgerBalance": 45500.00,
    "availableBalance": 40500.00,
    "pendingAmount": 5000.00,
    "walletStatus": "ACTIVE",
    "lastUpdated": "2026-10-30T12:00:00Z"
  }
}
```

Balance enquiries do not ask for or verify the transaction PIN. The USSD
gateway path uses the HMAC-authenticated gateway callback, matches its
normalized phone number to the registered customer, and checks the active
session before retrieving the latest balance. The response includes available,
ledger, and pending amounts and offers return-to-menu/exit choices. Duplicate
gateway callbacks continue to receive the original persisted response.

The current deployment still has no customer credential/token issuance
provider. Consequently the REST endpoint accepts only a future trusted
`CustomerPrincipal` and will not treat an arbitrary phone number or customer
ID as proof of identity. Integrate customer authentication before enabling
customer REST access in production.

## USSD gateway and session engine

`POST /api/v1/ussd` accepts a normalized JSON gateway callback and returns
plain-text `CON ...` or `END ...` as `text/plain`. Each request contains
`sessionId`, `callbackId`, `serviceCode`, `phoneNumber`, `networkCode`, and the
current `text` input. A provider adapter should translate its native callback
format into this internal request. The gateway must sign requests using
HMAC-SHA-256 over the UTF-8 fields, newline-separated in this exact order:
timestamp epoch seconds, session ID, callback ID, service code, phone number,
network code, text (empty when absent). Send the timestamp in
`X-USSD-Timestamp` and lowercase hex signature in `X-USSD-Signature`. Requests
outside the five-minute signature window are rejected. The phone number is
trusted only as an assertion from a gateway holding the shared secret; never
expose that secret to a handset or an untrusted proxy. The controller does not
log callback bodies.

V4 persists session state and safe workflow data in PostgreSQL, serializes
callbacks for the same session with row locks, and stores the original
`CON`/`END` result per callback ID so a gateway retry gets the same response.
Session state records transfer recipient, amount, transfer intent ID, and
idempotency key, but never stores PINs or OTPs. V7 adds immutable expiring
transfer intents, transfer description/fee metadata, and financial audit rows. Inactivity defaults to 120 seconds; scheduled
cleanup marks sessions expired, clears unfinished workflow data, and removes
expired session history after the configured retention period. A phone-number
rate limiter is applied in the running process; production multi-instance
deployments must put this limit at a shared gateway or shared rate-limit
service.

Available menus include balance inquiry, PIN setup/change, internal transfers,
recent completed internal transfers, account summary, help, and exit. Transfer
PIN verification delegates to the existing wallet transfer service. The
development registration menu fails closed because verified phone ownership
and an OTP delivery provider are not implemented. Airtime, data, bank
transfers, and provider-backed transactions are not reported as successful;
they remain unavailable until their provider and transaction modules exist.
History currently reflects committed internal wallet transfers only.

### Local development simulator

The simulator endpoints are disabled by default and are excluded when the
Spring profile `prod` is active. Enable them only in a local environment with
`USSD_SIMULATOR_ENABLED=true` and a separate random
`USSD_SIMULATOR_SECRET` (at least 32 characters). Every call requires that
secret in `X-USSD-Simulator-Secret`; simulator requests are bound to an
already-registered customer's normalized phone number and still pass through
the normal session and PIN/transfer services.

Start a session:

```sh
curl -X POST http://localhost:8080/api/v1/ussd/simulator/start \
  -H 'Content-Type: application/json' \
  -H 'X-USSD-Simulator-Secret: <local simulator secret>' \
  -d '{"serviceCode":"*123#","phoneNumber":"08012345678"}'
```

The response contains a `sessionId` and a `CON ...`/`END ...` message. Send
each following input as a new callback with a fresh callback ID:

```sh
curl -X POST http://localhost:8080/api/v1/ussd/simulator/input \
  -H 'Content-Type: application/json' \
  -H 'X-USSD-Simulator-Secret: <local simulator secret>' \
  -d '{"sessionId":"<session id>","callbackId":"cb-2","phoneNumber":"08012345678","text":"1"}'
```

Use `/api/v1/ussd/simulator/end` to cancel an active session. Do not enable the
simulator in a publicly reachable environment; it is a local testing gateway,
not customer authentication.

## Tests

Run unit tests:

```sh
./mvnw test
```

PostgreSQL integration tests use Testcontainers and are automatically skipped
when Docker is unavailable. They cover Flyway/JPA schema startup, registration
and wallet records, PIN setup and lockout, recovery-token ownership and reuse,
transfer PIN enforcement, atomic double-entry posting, idempotency, gateway
authentication, callback replay, USSD transfer flow, and session expiry.
Focused unit tests cover Argon2id hashing, PIN policy, lockout, PIN changes,
transfer balance behavior, gateway signatures, input validation, and refusal
to move funds after failed authorization.
