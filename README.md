# USSD Wallet Registration Backend

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

Optional wallet and registration settings:

| Variable | Default | Description |
| --- | --- | --- |
| `REGISTRATION_INITIAL_CUSTOMER_STATUS` | `PENDING` | `PENDING` by default; `ACTIVE` is an explicit opt-in and must only be used if the registration policy permits unverified activation. |
| `WALLET_NUMBER_PREFIX` | `10` | Numeric wallet number prefix. |
| `WALLET_NUMBER_LENGTH` | `10` | Total wallet number length, including prefix. |
| `WALLET_NUMBER_MAX_RETRIES` | `5` | Maximum candidate attempts when a number already exists or a database uniqueness collision occurs. |
| `WALLET_CURRENCY` | `NGN` | Three-letter wallet currency. |
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
records.
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

## PIN-authorized wallet transfers

`POST /api/v1/wallet/transfers` moves funds between active customer wallets.
The authenticated customer is always the sender; the request cannot select a
sender ID. It validates recipient, amount, wallet/customer state, and currency,
then verifies the supplied PIN before locking balances or making financial
changes. Both wallet rows and both balance rows are locked in deterministic
order. Debit, credit, transfer record, and debit/credit entries commit in one
database transaction. Insufficient funds, a wrong or locked PIN, or any
recording error leaves balances unchanged.

Every request must supply a fresh UUID `idempotencyKey`. Replaying an identical
request with that key returns the original transfer reference without moving
funds a second time; reusing the key for different transfer details is rejected.
PIN verification occurs on every request, including an idempotent retry.

Example request:

```json
{
  "recipientWalletNumber": "1098765432",
  "amount": 5000.00,
  "pin": "4829",
  "idempotencyKey": "90e6bf9e-4e5e-47a8-88e8-c5e53c7a6ebc"
}
```

Successful response:

```json
{
  "success": true,
  "message": "Transfer completed successfully",
  "data": {
    "reference": "TXN-4b9e2f0a-2644-4d86-b1cc-ce9c4a0f2e43",
    "recipientWalletNumber": "1098765432",
    "amount": 5000.00,
    "currency": "NGN",
    "createdAt": "2026-10-29T12:00:00Z"
  }
}
```

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
Session state records transfer recipient, amount, and an idempotency key, but
never stores PINs or OTPs. Inactivity defaults to 120 seconds; scheduled
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
