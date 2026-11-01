COMPLETE USSD DIGITAL WALLET APPLICATION FLOW
================================================

PROJECT OVERVIEW
----------------
Build a USSD-first digital wallet using Java 21, Spring Boot 3.x and PostgreSQL.
The first release is a localhost demonstration using synthetic customers,
wallets, bank records and mock payment providers. It must not move real money.

Core capabilities:
- Customer registration by phone number.
- Automatic wallet creation and unique wallet number.
- Secure transaction PIN setup and verification.
- USSD menus and session management. 
- Balance enquiry.
- Internal wallet-to-wallet transfers.
- Simulated external bank transfers.
***
- Simulated airtime and data purchases.
- Transaction history.
- Protected admin-only demo funding.
- Admin monitoring and audit logs.

1. APPLICATION ARCHITECTURE
---------------------------
Customer phone (dials *123#)
    |
USSD Gateway / Local USSD Simulator
    |
Spring Boot Application
    |-- USSD Engine
    |-- Session Management
    |-- Customer Service
    |-- Wallet Service
    |-- PIN and Security Service
    |-- Transfer Service
    |-- Bank Service
    |-- Transaction Service
    |-- Ledger Service
    |-- Payment Service
    |-- Provider Adapters
    |-- Notification Service
    |-- Admin Service
    |-- Audit Service
    |
PostgreSQL

Use a modular monolith for the first release, not microservices.

2. CUSTOMER REGISTRATION AND WALLET CREATION
--------------------------------------------
1. Customer dials *123#.
2. Gateway sends phone number and session information to Spring Boot.
3. Backend checks whether the phone number is registered.
4. If new, display:

   CON Welcome to Motion Wallet
   1. Register
   2. Exit

5. Collect full name.
6. Ask customer to create a four-digit transaction PIN.
7. Ask customer to confirm the PIN.
8. Validate input and check phone number uniqueness.
9. Create customer with generated UUID and securely hashed PIN.
10. Create wallet with unique wallet number.

Example:
Customer ID: generated UUID
Name: John Doe
Phone: 08030000000
Wallet number: MG1000000001
Currency: NGN
Opening balance: NGN 0.00
Status: ACTIVE

Return:
   END Registration successful!
   Welcome, John.
   Your Wallet Number: MG1000000001
   Dial *123# to continue.

Phone number must be unique. UUID is the internal identity; wallet number is
the customer-facing identifier. Never store the PIN in plaintext.

3. MAIN USSD MENU
-----------------
   CON Welcome to Motion Wallet
   1. Check Balance
   2. Send Money
   3. Buy Airtime
   4. Buy Data
   5. Transaction History
   6. Fund Wallet
   7. My Account
   8. Help

CON means continue the session; END terminates it. Adapt response syntax to
the selected USSD gateway.

4. CHECK BALANCE
---------------
1. Customer selects Check Balance.
2. Backend requests transaction PIN.
3. Backend identifies customer from validated session and phone number.
4. Verify PIN hash and wallet status.
5. Retrieve available balance.

Example:
   END Wallet Balance
   Available: NGN 25,000.00

Do not expose PINs in logs, responses or database records.

5. INTERNAL WALLET-TO-WALLET TRANSFER
-------------------------------------
This should be the first transfer feature because both wallets belong to
your application.

Menu:
   CON Send Money
   1. Motion Wallet
   2. Other Banks
   3. Back

Customer selects Motion Wallet.
   CON Enter recipient wallet number:

Customer enters MG1000000002. Backend looks up the wallet and recipient name.

If invalid:
   END Invalid wallet number.

If valid:
   CON Recipient Details
   Name: Jane Doe
   Wallet: MG1000000002
   Enter amount:

Customer enters 5000.
   CON Transfer Confirmation
   Recipient: Jane Doe
   Amount: NGN 5,000.00
   Fee: NGN 0.00
   1. Confirm
   2. Cancel

On confirmation, request transaction PIN. Backend must:
- Verify PIN.
- Confirm both wallets are active.
- Check available balance and transaction limits.
- Create a unique transaction reference.
- Enforce idempotency to prevent duplicate transfers.
- Debit sender and credit recipient atomically.
- Write balanced ledger entries.
- Mark the transaction successful.

Success:
   END Transfer Successful!
   You sent NGN 5,000.00
   To: Jane Doe
   Reference: TXN123456789

Insufficient balance:
   END Transaction Failed!
   Insufficient wallet balance.

Use database transactions, row-level locking or an equivalent concurrency
strategy, and consistent lock ordering. Prevent concurrent spending and
duplicate processing.

6. EXTERNAL BANK TRANSFER (DEMO)
--------------------------------
This is simulated in localhost. Do not claim to be connected to real banks.

Example synthetic bank menu:
   CON Select Bank
   1. OPay
   2. Moniepoint
   3. Access Bank
   4. GTBank
   5. UBA
   6. Zenith Bank
   7. FirstBank
   8. Other Banks

The names are demo seed data; verify current institution names and codes
before any live integration.

Flow:
1. Customer selects bank.
2. Customer enters account number.
3. Backend calls MockAccountEnquiryProvider.
4. Mock provider looks up synthetic data and returns account name.
5. Display bank, account number and beneficiary name.
6. Customer enters amount.
7. Display transfer summary and request confirmation.
8. Request transaction PIN.
9. Validate PIN, balance, wallet status and limits.
10. Create external transfer transaction.
11. Call MockTransferProvider.
12. Simulate provider outcome and update transaction state.
13. Record ledger postings according to the outcome.

Example mock enquiry:
Account number: 0123456789
Bank: OPay
Account name: Jane Doe

Success response:
   END Bank Transfer Successful!
   Bank: OPay
   Beneficiary: Jane Doe
   Amount: NGN 10,000.00
   Reference: TXN123456789

An authorized bank/payment provider is required for real transfers. Account
enquiry resolves an account number supplied by the customer; it does not
provide unrestricted access to all customers' private bank details.

External transfer timeouts must not trigger blind retries. Query provider
status first. Support PENDING or UNKNOWN until the outcome is confirmed.
If a previously reserved/debited transfer is later confirmed failed, reverse
the appropriate postings.

7. BUY AIRTIME
--------------
1. Customer selects Buy Airtime.
2. Display network list:
   CON Select Network
   1. MTN
   2. Airtel
   3. Glo
   4. 9mobile
3. Customer selects network.
4. Request destination phone number.
5. Request amount.
6. Display network, phone and amount for confirmation.
7. Request PIN.
8. Validate PIN and balance.
9. Call MockAirtimeProvider.
10. Record transaction and ledger result; return success or failure.

8. BUY DATA
-----------
1. Customer selects Buy Data.
2. Customer selects network.
3. Display configurable demo plans, for example:
   1. 100MB - NGN 100
   2. 500MB - NGN 500
   3. 1GB - NGN 1,000
   4. 2GB - NGN 2,000
4. Customer selects plan and enters destination phone number.
5. Display purchase summary.
6. Request confirmation and PIN.
7. Validate and call MockDataProvider.
8. Record result and show response.

Plans and prices should be configurable in the database or seed data, not
hardcoded in the USSD engine.

9. TRANSACTION HISTORY
----------------------
Menu:
   CON Transaction History
   1. Last transaction
   2. Last 5 transactions
   3. Last 10 transactions

Example:
   CON Recent Transactions
   1. Received NGN 5,000
   2. Sent NGN 2,000
   3. Airtime NGN 1,000
   0. Back

Selecting an item displays transaction type, amount, status, date and
reference. Customers must only see their own transactions.

10. FUND WALLET (DEMO MODE)
---------------------------
Funding is an admin-controlled test operation, not a real deposit.
1. Admin logs into protected admin application.
2. Admin selects a demo wallet and enters amount and reason.
3. Backend validates admin permission.
4. Backend creates a funding transaction.
5. Backend writes corresponding ledger postings.
6. Balance projection is updated through the accounting operation.
7. Customer checks balance through USSD.

Never allow unrestricted customer self-funding. Never directly edit balances
without ledger entries. Disable demo funding outside development/demo mode.

11. MY ACCOUNT AND PIN MANAGEMENT
---------------------------------
Menu:
   CON My Account
   1. My Details
   2. Change PIN
   3. Wallet Number
   4. Account Status

Change PIN flow:
1. Request current PIN.
2. Request new PIN.
3. Confirm new PIN.
4. Verify current PIN and new PIN policy.
5. Hash new PIN and update security record.

Apply rate limits and temporary lockouts after repeated incorrect PINs.

12. HELP
--------
Example:
   END Motion Wallet Support
   Contact: [configured demo support contact]

Use a configured contact rather than presenting a fabricated live support
service.

13. USSD SESSION MANAGEMENT
---------------------------
USSD is session-based. The backend must remember the current menu state.

Example state progression:
1. MAIN_MENU: input 2
2. TRANSFER_TYPE: input 1
3. RECIPIENT_WALLET: input MG1000000002
4. TRANSFER_AMOUNT: input 5000
5. TRANSFER_CONFIRMATION: input 1
6. ENTER_PIN: PIN supplied
7. COMPLETED: session ends

Suggested session fields:
- session_id
- phone_number
- customer_id
- current_state
- session_data
- status
- created_at
- updated_at
- expires_at

Implement session creation, continuation, termination, expiry, invalid-input
handling, back navigation, duplicate request protection and abandoned-session
handling. Do not store PINs in session_data. Clear sensitive transient values
when no longer needed.

The gateway sends a session identifier with each request. Build an adapter
that normalizes provider-specific payloads into an internal request model.
Different gateways may use different payload formats.

14. SPRING BOOT MODULES
----------------------
ussd       - receives USSD requests and creates CON/END responses.
session    - session state and expiry.
customer   - registration and customer profiles.
wallet     - wallet creation, status and balance operations.
security   - PIN hashing, verification, rate limits and lockouts.
transfer   - internal and external transfer orchestration.
bank       - bank directory and account name enquiry.
transaction- transaction references, states and history.
ledger     - double-entry accounting and immutable postings.
payment    - airtime, data and future bill payments.
provider   - mock banking and telecom provider implementations.
notification - customer notifications.
admin      - demo funding, customer management and monitoring.
audit      - security and administrative audit records.

15. PROPOSED JAVA PACKAGE STRUCTURE
-----------------------------------
src/main/java/com/motionwallet/
  MotionWalletApplication.java
  config/
    SecurityConfig.java
    DatabaseConfig.java
    OpenApiConfig.java
  ussd/
    UssdController.java
    UssdRequest.java
    UssdResponse.java
    UssdService.java
  session/
    Session.java
    SessionRepository.java
    SessionService.java
  customer/
  wallet/
  security/
  transfer/
  bank/
  transaction/
  ledger/
  payment/
  provider/
  notification/
  admin/
  audit/
  common/
    exception/
    response/
    util/

Each business module should contain its own entities, repositories, services,
DTOs, validation and controllers where appropriate. Keep business logic out
of controllers.

16. DATABASE DESIGN
-------------------
Create these PostgreSQL tables:

customers
  Customer identity, phone number and profile.

wallets
  Wallet number, customer relationship, currency and status.

wallet_balances
  Available and ledger balance, if maintained as a balance projection.

ussd_sessions
  Session ID, phone number, customer, state, session data, status and expiry.

transactions
  Transaction type, amount, fee, reference, status and timestamps.

ledger_accounts
  Accounting accounts for double-entry bookkeeping.

ledger_entries
  Immutable debit and credit postings.

banks
  Supported financial institutions and demo codes.

beneficiaries
  Saved customer beneficiaries.

account_enquiries
  Account verification requests and results.

transaction_limits
  Configurable transaction limits.

pin_attempts
  PIN failure tracking and lockout information.

notifications
  Customer notifications.

audit_logs
  Security and administrative activity.

payment_products
  Airtime and data products.

provider_requests
  External provider request/response references and status.

idempotency_keys
  Duplicate request protection.

Database requirements:
- Use UUIDs for internal identifiers where appropriate.
- Add unique constraints for phone numbers, wallet numbers and references.
- Add foreign keys and indexes for common lookups.
- Use NUMERIC(19,2) for NGN amounts, never FLOAT or DOUBLE for money.
- Use database transactions for financial operations.
- Keep ledger postings immutable.
- Correct postings through reversal entries.
- Treat wallet balance fields as projections, not the source of truth.

17. DOUBLE-ENTRY LEDGER EXAMPLE
-------------------------------
For a successful NGN 5,000 internal transfer:

Ledger account                  Debit       Credit
---------------------------------------------------
Sender wallet liability         5,000.00       -
Recipient wallet liability          -      5,000.00
---------------------------------------------------
Total                           5,000.00   5,000.00

Total debits must equal total credits for every posted transaction.

18. TRANSACTION LIFECYCLE
------------------------
INITIATED   - Customer starts an operation.
VALIDATING  - PIN, wallet, amount and limits are checked.
PENDING     - External provider has not confirmed the outcome.
PROCESSING  - Operation is being executed.
SUCCESS     - Operation completed successfully.
FAILED      - Operation failed.
REVERSED    - A completed posting was reversed using compensating entries.
UNKNOWN     - Provider outcome is not yet known and requires status enquiry.

Never blindly resubmit an external transfer after a timeout.

19. REST API ENDPOINTS
----------------------
USSD:
  POST /api/v1/ussd
  POST /api/v1/ussd/simulate

Customers:
  POST /api/v1/customers/register
  GET  /api/v1/customers/{id}

Wallets:
  GET  /api/v1/wallets/{walletNumber}
  GET  /api/v1/wallets/{walletNumber}/balance

Transfers:
  POST /api/v1/transfers/internal
  POST /api/v1/transfers/external
  GET  /api/v1/transfers/{reference}

Banks:
  GET  /api/v1/banks
  POST /api/v1/banks/verify-account

Transactions:
  GET  /api/v1/transactions

Payments:
  POST /api/v1/payments/airtime
  POST /api/v1/payments/data

Admin:
  POST /api/v1/admin/wallets/{id}/fund

Protect admin endpoints with Spring Security and role-based authorization.
USSD operations must be associated with a validated session, phone number
and transaction PIN wherever required.

20. LOCALHOST USSD SIMULATOR
----------------------------
Build a simple simulator that behaves like a USSD gateway.

It should:
- Let tester enter a phone number.
- Start a session when tester dials the demo code.
- Send session ID, phone number, service code and input to Spring Boot.
- Display the backend CON or END response.
- Send each subsequent menu selection as a separate request.
- Preserve session until it ends or expires.
- Support multiple test phone numbers.
- Allow sessions to be restarted or abandoned.

Example:
POST /api/v1/ussd/simulate
Content-Type: application/json

{
  "sessionId": "SESSION-001",
  "phoneNumber": "08030000000",
  "serviceCode": "*123#",
  "text": ""
}

Example subsequent request:
{
  "sessionId": "SESSION-001",
  "phoneNumber": "08030000000",
  "serviceCode": "*123#",
  "text": "2*1*MG1000000002"
}

The text field may contain accumulated USSD input. Normalize it in the
gateway adapter; do not assume all providers send identical payloads.

A localhost server cannot normally receive callbacks from a real telecom
gateway. Real mobile testing requires a publicly reachable HTTPS backend,
an approved USSD gateway integration and the relevant provider arrangements.

21. ADMIN APPLICATION
---------------------
Admin dashboard sections:
- Customers: view and manage demo customers.
- Wallets: view wallet numbers, statuses and balances.
- Transactions: monitor transfers and payments.
- Banks: manage synthetic bank directory.
- Demo Funding: credit test wallets through protected ledger operations.
- USSD Sessions: inspect active, completed, failed and expired sessions.
- Audit Logs: inspect sensitive administrative and security actions.

Admin workflow:
1. Admin logs in.
2. Views dashboard statistics.
3. Creates or views customers.
4. Opens a customer profile.
5. Inspects wallet and transaction history.
6. Funds a test wallet through the demo-only operation.
7. Inspects the funding transaction and ledger entries.
8. Monitors failed and pending transactions.
9. Inspects USSD sessions and request logs.

Demo funding must be disabled outside development/demo mode.

22. NON-FUNCTIONAL AND SECURITY REQUIREMENTS
--------------------------------------------
Security:
- Hash PINs with BCrypt or Argon2id.
- Never store or log plaintext PINs.
- Rate-limit PIN verification.
- Temporarily restrict accounts after repeated failures.
- Validate all backend inputs.
- Enforce authorization on admin operations.
- Protect sensitive data in logs.
- Use secure session handling.
- Use HTTPS for real gateway integrations.
- Keep secrets in environment variables or a secret manager.

Financial correctness:
- Use database transactions for internal transfers.
- Use row-level locking or a proven concurrency strategy.
- Enforce balanced ledger postings.
- Use idempotency to prevent duplicate processing.
- Never directly edit wallet balances.
- Use reversal postings for corrections.
- Reconcile external provider transactions.

Reliability:
- Handle gateway retries and session expiry.
- Handle provider timeouts and unknown outcomes.
- Record provider request references.
- Provide transaction status enquiry.
- Log operational errors without exposing secrets.

23. TESTING REQUIREMENTS
-----------------------
Unit tests:
- Customer registration.
- Wallet creation.
- PIN hashing and verification.
- USSD menu navigation.
- Balance enquiry.
- Transfer validation.
- Transaction state transitions.
- Ledger debit/credit balancing.

Integration tests:
- PostgreSQL repository operations.
- Customer and wallet creation.
- Internal transfer atomicity.
- Insufficient balance rejection.
- Duplicate request protection.
- USSD session continuation and expiry.
- Mock account enquiry.
- Mock external transfer outcomes.

Security tests:
- Incorrect PIN and repeated PIN failures.
- Suspended wallet.
- Unauthorized admin funding.
- Invalid customer session.
- Malformed USSD input.

Concurrency tests:
- Two simultaneous transfers from one wallet.
- Confirm available balance cannot become negative.
- Confirm ledger debits and credits remain balanced.
- Confirm duplicate requests do not create duplicate transactions.

End-to-end tests:
- Register two customers.
- Fund Customer A.
- Transfer money from A to B.
- Check both balances.
- Check transaction histories.
- Inspect ledger.
- Simulate bank transfer.
- Simulate airtime purchase.

24. RECOMMENDED TECHNOLOGY STACK
-------------------------------
Backend:
- Java 21
- Spring Boot 3.x
- Spring Web
- Spring Data JPA
- Hibernate
- Spring Security
- Spring Validation
- Spring Boot Actuator

Database:
- PostgreSQL 16 or later
- Flyway migrations

API documentation:
- Springdoc OpenAPI / Swagger UI

Build:
- Maven

Testing:
- JUnit 5
- Mockito
- Testcontainers

Development:
- Local PostgreSQL
- Development application profile
- Synthetic seed data
- Mock provider implementations
- Localhost USSD simulator

Keep the backend in Java/Spring Boot and PostgreSQL.

25. DEVELOPMENT ROADMAP
-----------------------
PHASE 1 - PROJECT FOUNDATION
- Create Spring Boot project.
- Configure PostgreSQL and Flyway.
- Add common error handling and API documentation.
- Verify application startup.

PHASE 2 - CUSTOMER AND WALLET
- Implement customer registration and unique phone number.
- Implement PIN hashing.
- Implement wallet number generation and wallet creation.
- Add database constraints and tests.

PHASE 3 - LEDGER AND FUNDING
- Design ledger accounts.
- Implement immutable ledger entries.
- Implement balanced postings.
- Implement admin-only demo funding.
- Test balance projections against ledger records.

PHASE 4 - USSD ENGINE
- Define request and response DTOs.
- Implement gateway adapter.
- Implement session persistence and expiry.
- Implement menu state transitions.
- Implement CON/END responses, invalid input and back navigation.

PHASE 5 - LOCALHOST SIMULATOR
- Build simple simulator interface.
- Connect to Spring Boot simulation endpoint.
- Test registration and navigation.
- Test multiple simulated customers.

PHASE 6 - INTERNAL TRANSFERS
- Implement recipient wallet lookup.
- Implement transfer confirmation and PIN verification.
- Implement balance and limit checks.
- Implement atomic debit/credit and idempotency.
- Test concurrency and duplicate requests.

PHASE 7 - BANK TRANSFERS
- Seed demo bank directory.
- Add mock account enquiry.
- Add mock external transfer provider.
- Implement pending, success, failed and unknown outcomes.
- Implement provider status enquiry and safe reconciliation.

PHASE 8 - PAYMENTS AND HISTORY
- Implement airtime and data simulation.
- Implement transaction history and detail lookup.

PHASE 9 - ADMIN AND AUDIT
- Implement admin authentication and roles.
- Implement customer, wallet and transaction views.
- Implement USSD session monitoring and audit logs.
- Restrict demo funding to development mode.

PHASE 10 - FINAL TESTING
- Run unit, integration, security and concurrency tests.
- Complete end-to-end demo scenario.
- Document setup and test instructions.

26. COMPLETE DEMONSTRATION SCENARIO
-----------------------------------
1. Admin starts Spring Boot and PostgreSQL.
2. Two demo customers are registered.
3. Customer A receives wallet MG1000000001.
4. Customer B receives wallet MG1000000002.
5. Admin funds Customer A with NGN 50,000 through demo funding.
6. Customer A dials *123# in the simulator.
7. Customer A checks balance: NGN 50,000.
8. Customer A sends NGN 5,000 to Customer B.
9. Backend validates PIN and processes the transfer.
10. Customer A balance becomes NGN 45,000.
11. Customer B balance becomes NGN 5,000.
12. Both customers see the transaction in their histories.
13. Admin inspects the transaction and balanced ledger entries.
14. Customer A simulates an OPay transfer and receives a mock result.
15. Customer A purchases simulated airtime.
16. Admin inspects transactions, customer records and USSD sessions.

The demo is accepted when this scenario works without manual database
balance edits and without moving real funds.

27. LIVE-DEPLOYMENT DISTINCTION
-------------------------------
The localhost version is a simulation. Before live deployment, obtain
appropriate commercial and technical arrangements with the relevant USSD
gateway, payment providers and financial institutions.

A live Nigerian financial service may require legal, regulatory, licensing,
KYC/AML, consumer-protection, privacy and security assessments appropriate
to its actual business model. Building the software alone does not authorize
the service to hold or transfer real customer funds.

28. FINAL IMPLEMENTATION RECOMMENDATION
---------------------------------------
Build the Spring Boot backend and USSD session engine first. Connect the
localhost simulator next. Implement customer registration, wallet creation,
accounting and internal transfers before simulated bank transfers, airtime
and data purchases.

The next engineering deliverables should be:
1. Complete database entity relationship diagram (ERD).
2. PostgreSQL schema and Flyway migration scripts.
3. USSD state-machine specification.
4. API request and response contracts.
5. Ledger posting rules.
6. Spring Boot implementation plan and test cases.