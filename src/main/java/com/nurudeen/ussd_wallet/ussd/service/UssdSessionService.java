package com.nurudeen.ussd_wallet.ussd.service;

import com.nurudeen.ussd_wallet.common.exception.InsufficientWalletFundsException;
import com.nurudeen.ussd_wallet.common.exception.InvalidTransactionPinException;
import com.nurudeen.ussd_wallet.common.exception.PinNotConfiguredException;
import com.nurudeen.ussd_wallet.common.exception.PinConfirmationMismatchException;
import com.nurudeen.ussd_wallet.common.exception.PinReuseException;
import com.nurudeen.ussd_wallet.common.exception.PinVerificationLockedException;
import com.nurudeen.ussd_wallet.common.exception.WeakPinException;
import com.nurudeen.ussd_wallet.common.exception.WalletNotFoundException;
import com.nurudeen.ussd_wallet.common.exception.WalletTransferConflictException;
import com.nurudeen.ussd_wallet.customer.entity.Customer;
import com.nurudeen.ussd_wallet.customer.enums.CustomerStatus;
import com.nurudeen.ussd_wallet.customer.repository.CustomerRepository;
import com.nurudeen.ussd_wallet.customer.service.PhoneNumberNormalizer;
import com.nurudeen.ussd_wallet.security.entity.CustomerSecurity;
import com.nurudeen.ussd_wallet.security.repository.CustomerSecurityRepository;
import com.nurudeen.ussd_wallet.security.service.TransactionPinManagementService;
import com.nurudeen.ussd_wallet.ussd.config.UssdProperties;
import com.nurudeen.ussd_wallet.ussd.dto.UssdGatewayRequest;
import com.nurudeen.ussd_wallet.ussd.dto.UssdResponse;
import com.nurudeen.ussd_wallet.ussd.dto.UssdTransactionHistoryItem;
import com.nurudeen.ussd_wallet.ussd.session.UssdGatewayCallback;
import com.nurudeen.ussd_wallet.ussd.session.UssdGatewayCallbackRepository;
import com.nurudeen.ussd_wallet.ussd.session.UssdSession;
import com.nurudeen.ussd_wallet.ussd.session.UssdSessionEvent;
import com.nurudeen.ussd_wallet.ussd.session.UssdSessionEventRepository;
import com.nurudeen.ussd_wallet.ussd.session.UssdSessionRepository;
import com.nurudeen.ussd_wallet.ussd.session.UssdSessionStatus;
import com.nurudeen.ussd_wallet.wallet.dto.WalletTransferRequest;
import com.nurudeen.ussd_wallet.wallet.dto.WalletTransferResponse;
import com.nurudeen.ussd_wallet.wallet.dto.WalletResponse;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.repository.WalletRepository;
import com.nurudeen.ussd_wallet.wallet.repository.WalletTransferEntryRepository;
import com.nurudeen.ussd_wallet.wallet.service.WalletQueryService;
import com.nurudeen.ussd_wallet.wallet.service.WalletTransferService;
import org.springframework.data.domain.PageRequest;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UssdSessionService {

    private static final String MAIN_MENU = "main";
    private final UssdSessionRepository sessionRepository;
    private final UssdGatewayCallbackRepository callbackRepository;
    private final UssdSessionEventRepository eventRepository;
    private final CustomerRepository customerRepository;
    private final CustomerSecurityRepository securityRepository;
    private final WalletRepository walletRepository;
    private final WalletQueryService walletQueryService;
    private final WalletTransferService transferService;
    private final WalletTransferEntryRepository transferEntryRepository;
    private final TransactionPinManagementService pinManagementService;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final UssdInputValidator inputValidator;
    private final UssdGatewayRateLimitService rateLimitService;
    private final UssdProperties properties;
    private final Clock clock;

    public UssdSessionService(
            UssdSessionRepository sessionRepository,
            UssdGatewayCallbackRepository callbackRepository,
            UssdSessionEventRepository eventRepository,
            CustomerRepository customerRepository,
            CustomerSecurityRepository securityRepository,
            WalletRepository walletRepository,
            WalletQueryService walletQueryService,
            WalletTransferService transferService,
            WalletTransferEntryRepository transferEntryRepository,
            TransactionPinManagementService pinManagementService,
            PhoneNumberNormalizer phoneNumberNormalizer,
            UssdInputValidator inputValidator,
            UssdGatewayRateLimitService rateLimitService,
            UssdProperties properties,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.callbackRepository = callbackRepository;
        this.eventRepository = eventRepository;
        this.customerRepository = customerRepository;
        this.securityRepository = securityRepository;
        this.walletRepository = walletRepository;
        this.walletQueryService = walletQueryService;
        this.transferService = transferService;
        this.transferEntryRepository = transferEntryRepository;
        this.pinManagementService = pinManagementService;
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.inputValidator = inputValidator;
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public UssdResponse process(UssdGatewayRequest request) {
        return process(request, null);
    }

    @Transactional
    public UssdResponse processSimulator(
            UssdGatewayRequest request, UUID authenticatedCustomerId) {
        Customer customer = customerRepository.findById(authenticatedCustomerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        String normalized = phoneNumberNormalizer.normalize(request.phoneNumber());
        if (!customer.getPhoneNumber().equals(normalized)) {
            throw new IllegalArgumentException("Session phone does not match authenticated customer");
        }
        return process(request, authenticatedCustomerId);
    }

    private UssdResponse process(UssdGatewayRequest request, UUID simulatorCustomerId) {
        String phoneNumber = phoneNumberNormalizer.normalize(request.phoneNumber());
        if (!request.serviceCode().equals(properties.serviceCode())) {
            return end("Invalid service code. Dial " + properties.serviceCode() + ".");
        }
        Instant now = clock.instant();
        UssdSession session = sessionRepository.findBySessionIdForUpdate(request.sessionId())
                .orElse(null);

        if (session == null) {
            if (!safeInput(request.text()).isEmpty()) {
                return end("Session expired. Dial again to start.");
            }
            int created = sessionRepository.createIfAbsent(
                    request.sessionId(),
                    phoneNumber,
                    request.serviceCode(),
                    request.networkCode(),
                    now,
                    now.plusSeconds(properties.sessionTimeoutSeconds()));
            session = sessionRepository.findBySessionIdForUpdate(request.sessionId())
                    .orElseThrow(() -> new IllegalStateException("USSD session could not be created"));
            if (created == 1) {
                recordEvent(session, "SESSION_STARTED", null, "START");
            }
        } else {
            Optional<UssdGatewayCallback> duplicate = callbackRepository.findByIdSessionIdAndIdCallbackId(
                    request.sessionId(), request.callbackId());
            if (duplicate.isPresent()) {
                return response(duplicate.get());
            }
            if (!session.getPhoneNumber().equals(phoneNumber)
                    || !session.getNetworkCode().equals(request.networkCode())
                    || !session.getServiceCode().equals(request.serviceCode())) {
                return end("Session details do not match. Start again.");
            }
            if (session.isExpiredAt(now) && session.isOpen()) {
                session.getSessionData().clear();
                transition(session, "MAIN", "SESSION_EXPIRED", UssdSessionStatus.EXPIRED, now);
                UssdResponse expired = end("Session expired. Dial again to start.");
                saveCallback(session, request.callbackId(), expired);
                return expired;
            }
            if (!session.isOpen()) {
                return end("Session has ended. Dial again to start.");
            }
        }

        if (!rateLimitService.allow(phoneNumber, now)) {
            UssdResponse limited = end("Too many requests. Please try again later.");
            saveCallback(session, request.callbackId(), limited);
            close(session, "RATE_LIMITED", UssdSessionStatus.FAILED, now);
            return limited;
        }

        String input;
        try {
            input = inputValidator.input(request.text() == null ? "" : request.text());
        } catch (IllegalArgumentException exception) {
            UssdResponse invalid = continueWith("Invalid input. Please try again.");
            saveCallback(session, request.callbackId(), invalid);
            return invalid;
        }

        Customer customer = customerRepository.findByPhoneNumber(phoneNumber).orElse(null);
        if (simulatorCustomerId != null
                && (customer == null || !customer.getId().equals(simulatorCustomerId))) {
            return end("Customer identity does not match this session.");
        }
        UssdResponse response;
        try {
            response = runStep(session, customer, input, now);
        } catch (InvalidTransactionPinException exception) {
            response = continueWith("Invalid PIN. Try again or dial 99 to exit.");
        } catch (PinVerificationLockedException exception) {
            response = end("PIN locked for 15 minutes. Please try later.");
            close(session, "PIN_LOCKED", UssdSessionStatus.COMPLETED, now);
        } catch (InsufficientWalletFundsException exception) {
            response = end("Insufficient wallet balance.");
            close(session, "TRANSFER_FAILED", UssdSessionStatus.FAILED, now);
        } catch (PinNotConfiguredException exception) {
            response = end("Set up your transaction PIN before continuing.");
            close(session, "PIN_NOT_CONFIGURED", UssdSessionStatus.FAILED, now);
        } catch (WalletNotFoundException exception) {
            response = continueWith("Wallet not found. Check the wallet number.");
        } catch (WalletTransferConflictException exception) {
            response = end("Transfer could not be completed. Please review the details.");
            close(session, "TRANSFER_REJECTED", UssdSessionStatus.FAILED, now);
        } catch (IllegalArgumentException exception) {
            response = continueWith(exception.getMessage());
        } catch (WeakPinException | PinConfirmationMismatchException | PinReuseException exception) {
            response = continueWith(exception.getMessage());
        }
        if (response.type() == UssdResponse.Type.END && session.isOpen()) {
            close(session, "SESSION_COMPLETED", UssdSessionStatus.COMPLETED, now);
        }
        if (session.isOpen()) {
            session.advance(
                    session.getCurrentMenu(),
                    session.getCurrentStep(),
                    UssdSessionStatus.WAITING_FOR_INPUT,
                    now,
                    now.plusSeconds(properties.sessionTimeoutSeconds()));
        }
        saveCallback(session, request.callbackId(), response);
        return response;
    }

    private UssdResponse runStep(UssdSession session, Customer customer, String input, Instant now) {
        String step = session.getCurrentStep();
        if (input.equalsIgnoreCase("CANCEL")) {
            clearSessionData(session);
            close(session, "SESSION_CANCELLED", UssdSessionStatus.CANCELLED, now);
            return end("Session cancelled.");
        }
        if (step.equals("START")) {
            if (customer == null) {
                transition(session, "UNREGISTERED", "UNREGISTERED", UssdSessionStatus.WAITING_FOR_INPUT, now);
                return continueWith("Welcome to Nuru Wallet\n1. Register\n2. Exit");
            }
            return showMain(session, customer, now);
        }

        if (isBackOrExit(input) && isMenuStep(step)) {
            clearSessionData(session);
            if (input.equals("99")) {
                close(session, "SESSION_CANCELLED", UssdSessionStatus.CANCELLED, now);
                return end("Thank you for using Nuru Wallet.");
            }
            if (input.equals("00") || step.equals("UNREGISTERED")) {
                return showMain(session, customer, now);
            }
            return showMain(session, customer, now);
        }

        return switch (step) {
            case "UNREGISTERED" -> unregistered(session, input, now);
            case "PIN_SETUP" -> setupPin(session, customer, input, now);
            case "MAIN" -> mainSelection(session, customer, input, now);
            case "TRANSFER_RECIPIENT" -> transferRecipient(session, customer, input, now);
            case "TRANSFER_AMOUNT" -> transferAmount(session, input, now);
            case "TRANSFER_CONFIRM" -> confirmTransfer(session, input, now);
            case "TRANSFER_PIN" -> executeTransfer(session, customer, input, now);
            case "PIN_CHANGE" -> changePin(session, customer, input, now);
            case "HISTORY_CHOICE" -> historyChoice(session, customer, input, now);
            case "HISTORY_MORE" -> historyMore(session, customer, input, now);
            case "HELP" -> helpSelection(session, input, now);
            default -> {
                clearSessionData(session);
                yield showMain(session, customer, now);
            }
        };
    }

    private UssdResponse unregistered(UssdSession session, String input, Instant now) {
        if (input.equals("1")) {
            close(session, "REGISTRATION_UNAVAILABLE", UssdSessionStatus.FAILED, now);
            return end("Registration requires verified phone service, which is not configured.");
        }
        if (input.equals("2") || input.equals("99")) {
            close(session, "SESSION_CANCELLED", UssdSessionStatus.CANCELLED, now);
            return end("Thank you for using Nuru Wallet.");
        }
        return continueWith("Choose 1 to register or 2 to exit.");
    }

    private UssdResponse showMain(UssdSession session, Customer customer, Instant now) {
        if (customer == null) {
            transition(session, "UNREGISTERED", "UNREGISTERED", UssdSessionStatus.WAITING_FOR_INPUT, now);
            return continueWith("Welcome to Nuru Wallet\n1. Register\n2. Exit");
        }
        if (customer.getStatus() == CustomerStatus.SUSPENDED
                || customer.getStatus() == CustomerStatus.CLOSED) {
            transition(session, MAIN_MENU, "MAIN", UssdSessionStatus.WAITING_FOR_INPUT, now);
            return continueWith("Wallet access is restricted.\n6. My Account\n7. Help\n8. Exit");
        }
        CustomerSecurity security = securityRepository.findById(customer.getId()).orElse(null);
        boolean pinConfigured = security != null && security.isPinConfigured();
        if (!pinConfigured) {
            transition(session, MAIN_MENU, "PIN_SETUP", UssdSessionStatus.WAITING_FOR_INPUT, now);
            return continueWith("Welcome to Nuru Wallet\nPIN setup required.\nEnter PIN*confirmation:");
        }
        transition(session, MAIN_MENU, "MAIN", UssdSessionStatus.WAITING_FOR_INPUT, now);
        return continueWith("Welcome to Nuru Wallet\n1. Check Balance\n2. Send Money\n"
                + "3. Buy Airtime\n4. Transaction History\n5. Change PIN\n"
                + "6. My Account\n7. Help\n8. Exit");
    }

    private UssdResponse mainSelection(UssdSession session, Customer customer, String input, Instant now) {
        if (customer == null) {
            return showMain(session, null, now);
        }
        return switch (input) {
            case "1" -> balance(customer);
            case "2" -> {
                if (!customer.getStatus().isFinanciallyActive()) {
                    yield continueWith("Your account is not active for transfers.");
                }
                transition(session, "TRANSFER", "TRANSFER_RECIPIENT", UssdSessionStatus.WAITING_FOR_INPUT, now);
                session.getSessionData().clear();
                session.getSessionData().put("idempotencyKey", UUID.randomUUID().toString());
                yield continueWith("Enter recipient wallet number:");
            }
            case "3" -> continueWith("Airtime purchase is not available yet.");
            case "4" -> {
                transition(session, "HISTORY", "HISTORY_CHOICE", UssdSessionStatus.WAITING_FOR_INPUT, now);
                yield continueWith("Transaction History\n1. Last transaction\n2. Last 5 transactions\n0. Back");
            }
            case "5" -> {
                transition(session, "PIN_CHANGE", "PIN_CHANGE", UssdSessionStatus.WAITING_FOR_INPUT, now);
                yield continueWith("Enter current PIN*new PIN*confirm PIN:");
            }
            case "6" -> account(customer);
            case "7" -> {
                transition(session, "HELP", "HELP", UssdSessionStatus.WAITING_FOR_INPUT, now);
                yield continueWith("Nuru Wallet Help\n1. Transfer\n2. Balance\n3. Change PIN\n4. Contact\n0. Back");
            }
            case "8" -> {
                close(session, "SESSION_COMPLETED", UssdSessionStatus.COMPLETED, now);
                yield end("Thank you for using Nuru Wallet.");
            }
            default -> continueWith("Invalid choice. Enter a menu number.");
        };
    }

    private UssdResponse setupPin(UssdSession session, Customer customer, String input, Instant now) {
        String[] parts = input.split("\\*", -1);
        if (parts.length != 2 || !parts[0].matches("[0-9]{4,6}") || !parts[1].matches("[0-9]{4,6}")) {
            return continueWith("Enter 4-6 digit PIN and confirmation separated by *.");
        }
        pinManagementService.setup(customer.getId(), parts[0], parts[1]);
        clearSessionData(session);
        recordEvent(session, "PIN_SETUP", "PIN_SETUP", "MAIN");
        return showMain(session, customer, now);
    }

    private UssdResponse transferRecipient(UssdSession session, Customer customer, String input, Instant now) {
        String walletNumber = inputValidator.walletNumber(input);
        Wallet recipient = walletRepository.findByWalletNumber(walletNumber)
                .orElseThrow(WalletNotFoundException::new);
        if (recipient.getCustomerId().equals(customer.getId())) {
            return continueWith("You cannot transfer to your own wallet. Enter another wallet:");
        }
        if (recipient.getStatus() != com.nurudeen.ussd_wallet.wallet.enums.WalletStatus.ACTIVE
                || !recipient.getCustomerStatus().isFinanciallyActive()) {
            return continueWith("Recipient wallet is unavailable. Enter another wallet:");
        }
        session.getSessionData().put("recipientWalletNumber", walletNumber);
        session.getSessionData().put("recipientName", recipientName(recipient));
        transition(session, "TRANSFER", "TRANSFER_AMOUNT", UssdSessionStatus.WAITING_FOR_INPUT, now);
        return continueWith("Enter transfer amount in NGN:");
    }

    private UssdResponse transferAmount(UssdSession session, String input, Instant now) {
        BigDecimal amount = inputValidator.amount(input);
        session.getSessionData().put("amount", amount.toPlainString());
        transition(session, "TRANSFER", "TRANSFER_CONFIRM", UssdSessionStatus.WAITING_FOR_INPUT, now);
        return continueWith("Confirm transfer\nTo: " + safeDisplay(session.getSessionData().get("recipientName"))
                + "\nWallet: " + session.getSessionData().get("recipientWalletNumber")
                + "\nAmount: NGN " + formatAmount(amount) + "\n1. Confirm\n2. Cancel");
    }

    private UssdResponse confirmTransfer(UssdSession session, String input, Instant now) {
        if (input.equals("2")) {
            clearSessionData(session);
            close(session, "TRANSFER_CANCELLED", UssdSessionStatus.CANCELLED, now);
            return end("Transfer cancelled.");
        }
        if (!input.equals("1")) {
            return continueWith("Enter 1 to confirm or 2 to cancel.");
        }
        transition(session, "TRANSFER", "TRANSFER_PIN", UssdSessionStatus.WAITING_FOR_INPUT, now);
        return continueWith("Enter your transaction PIN:");
    }

    private UssdResponse executeTransfer(UssdSession session, Customer customer, String pin, Instant now) {
        if (!pin.matches("[0-9]{4,6}")) {
            return continueWith("Enter your 4-6 digit transaction PIN.");
        }
        String walletNumber = requireSessionValue(session, "recipientWalletNumber");
        BigDecimal amount = new BigDecimal(requireSessionValue(session, "amount"));
        UUID idempotencyKey = UUID.fromString(requireSessionValue(session, "idempotencyKey"));
        WalletTransferResponse transfer = transferService.transfer(
                customer.getId(),
                new WalletTransferRequest(walletNumber, amount, pin, idempotencyKey));
        clearSessionData(session);
        close(session, "TRANSFER_COMPLETED", UssdSessionStatus.COMPLETED, now);
        return end("Transfer successful!\nAmount: " + transfer.currency() + " "
                + formatAmount(transfer.amount()) + "\nReference: " + transfer.reference());
    }

    private UssdResponse changePin(UssdSession session, Customer customer, String input, Instant now) {
        String[] parts = input.split("\\*", -1);
        if (parts.length != 3
                || !parts[0].matches("[0-9]{4,6}")
                || !parts[1].matches("[0-9]{4,6}")
                || !parts[2].matches("[0-9]{4,6}")) {
            return continueWith("Enter current, new and confirm PIN separated by *.");
        }
        pinManagementService.change(customer.getId(), parts[0], parts[1], parts[2]);
        clearSessionData(session);
        close(session, "PIN_CHANGED", UssdSessionStatus.COMPLETED, now);
        return end("Transaction PIN changed successfully.");
    }

    private UssdResponse historyChoice(
            UssdSession session, Customer customer, String input, Instant now) {
        if (input.equals("0")) {
            return showMain(session, customer, now);
        }
        if (!input.equals("1") && !input.equals("2")) {
            return continueWith("Choose 1 for the last transaction, 2 for the last 5, or 0 to go back.");
        }
        int limit = input.equals("1") ? 1 : 5;
        var records = transferEntryRepository.findRecentForCustomer(
                customer.getId(), PageRequest.of(0, limit));
        if (records.isEmpty()) {
            close(session, "HISTORY_EMPTY", UssdSessionStatus.COMPLETED, now);
            return end("No transactions found.");
        }
        if (limit == 1 || records.size() <= 2) {
            close(session, "HISTORY_VIEWED", UssdSessionStatus.COMPLETED, now);
            return end(formatHistory(records, 0, records.size()));
        }
        session.getSessionData().put("historyOffset", "0");
        transition(session, "HISTORY", "HISTORY_MORE", UssdSessionStatus.WAITING_FOR_INPUT, now);
        return continueWith(formatHistory(records, 0, 2) + "\n9. More\n0. Main menu");
    }

    private UssdResponse historyMore(
            UssdSession session, Customer customer, String input, Instant now) {
        if (input.equals("0")) {
            clearSessionData(session);
            return showMain(session, customer, now);
        }
        if (!input.equals("9")) {
            return continueWith("Enter 9 for more transactions or 0 for the main menu.");
        }
        var records = transferEntryRepository.findRecentForCustomer(
                customer.getId(), PageRequest.of(0, 5));
        int offset = Integer.parseInt(requireSessionValue(session, "historyOffset")) + 2;
        int endIndex = Math.min(offset + 2, records.size());
        if (offset >= records.size()) {
            close(session, "HISTORY_VIEWED", UssdSessionStatus.COMPLETED, now);
            return end("No more transactions.");
        }
        clearSessionData(session);
        if (endIndex == records.size()) {
            close(session, "HISTORY_VIEWED", UssdSessionStatus.COMPLETED, now);
            return end(formatHistory(records, offset, endIndex));
        }
        session.getSessionData().put("historyOffset", Integer.toString(offset));
        return continueWith(formatHistory(records, offset, endIndex) + "\n9. More\n0. Main menu");
    }

    private String formatHistory(List<UssdTransactionHistoryItem> records, int start, int endIndex) {
        StringBuilder message = new StringBuilder("Recent transactions\n");
        for (int index = start; index < endIndex; index++) {
            UssdTransactionHistoryItem record = records.get(index);
            message.append(record.direction())
                    .append(' ')
                    .append(record.currency())
                    .append(' ')
                    .append(formatAmount(record.amount()))
                    .append('\n')
                    .append(record.reference())
                    .append('\n');
        }
        return message.toString().stripTrailing();
    }

    private UssdResponse helpSelection(UssdSession session, String input, Instant now) {
        String message = switch (input) {
            case "1" -> "Transfer: choose Send Money, enter wallet and amount, confirm, then PIN.";
            case "2" -> "Balance: choose Check Balance from the main menu.";
            case "3" -> "Change PIN: enter current, new and confirmation PIN separated by *.";
            case "4" -> "Contact your wallet provider for support.";
            case "0", "00" -> null;
            default -> "Invalid choice. 1 Transfer 2 Balance 3 Change PIN 4 Contact 0 Back";
        };
        if (message == null) {
            return showMain(session, customerRepository.findByPhoneNumber(session.getPhoneNumber()).orElse(null), now);
        }
        return continueWith(message);
    }

    private UssdResponse balance(Customer customer) {
        WalletResponse wallet = walletQueryService.getForCustomer(customer.getId());
        if (wallet.balance() == null) {
            return end("Wallet balance is currently unavailable.");
        }
        return end("Your Wallet Balance:\n" + wallet.currency() + " "
                + formatAmount(wallet.balance().availableBalance()));
    }

    private UssdResponse account(Customer customer) {
        Wallet wallet = walletRepository.findByCustomer_Id(customer.getId())
                .orElseThrow(WalletNotFoundException::new);
        String name = ((customer.getFirstName() == null ? "" : customer.getFirstName()) + " "
                + (customer.getLastName() == null ? "" : customer.getLastName())).strip();
        if (name.isBlank()) {
            name = "Customer";
        }
        String number = wallet.getWalletNumber();
        String maskedWallet = "*".repeat(Math.max(0, number.length() - 4))
                + number.substring(Math.max(0, number.length() - 4));
        String phone = customer.getPhoneNumber();
        String maskedPhone = phone.length() <= 4
                ? "****"
                : phone.substring(0, Math.min(4, phone.length())) + "****"
                        + phone.substring(phone.length() - 2);
        return continueWith("My Account\n" + safeDisplay(name)
                + "\nPhone: " + maskedPhone + "\nWallet: " + maskedWallet
                + "\nStatus: " + customer.getStatus());
    }

    private boolean isBackOrExit(String input) {
        return input.equals("0") || input.equals("00") || input.equals("99");
    }

    private boolean isMenuStep(String step) {
        return step.equals("MAIN") || step.equals("UNREGISTERED") || step.equals("HELP");
    }

    private void transition(
            UssdSession session,
            String menu,
            String step,
            UssdSessionStatus status,
            Instant now) {
        String oldStep = session.getCurrentStep();
        session.advance(menu, step, status, now, now.plusSeconds(properties.sessionTimeoutSeconds()));
        recordEvent(session, "NAVIGATION", oldStep, step);
    }

    private void close(UssdSession session, String event, UssdSessionStatus status, Instant now) {
        String oldStep = session.getCurrentStep();
        session.advance(session.getCurrentMenu(), oldStep, status, now, now);
        recordEvent(session, event, oldStep, status.name());
    }

    private void clearSessionData(UssdSession session) {
        session.getSessionData().clear();
    }

    private String requireSessionValue(UssdSession session, String key) {
        String value = session.getSessionData().get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("USSD workflow state is incomplete");
        }
        return value;
    }

    private void saveCallback(UssdSession session, String callbackId, UssdResponse response) {
        callbackRepository.save(new UssdGatewayCallback(
                session.getSessionId(), callbackId, response.type().name(), response.message()));
    }

    private UssdResponse response(UssdGatewayCallback callback) {
        return new UssdResponse(
                UssdResponse.Type.valueOf(callback.getResponseType()), callback.getResponseText());
    }

    private void recordEvent(UssdSession session, String event, String fromStep, String toStep) {
        eventRepository.save(new UssdSessionEvent(session.getSessionId(), event, fromStep, toStep));
    }

    private UssdResponse end(String message) {
        return new UssdResponse(UssdResponse.Type.END, limitMessage(message));
    }

    private UssdResponse continueWith(String message) {
        return new UssdResponse(UssdResponse.Type.CONTINUE, limitMessage(message));
    }

    private String limitMessage(String message) {
        if (message == null) {
            throw new IllegalArgumentException("USSD response cannot be empty");
        }
        return message.length() <= 160 ? message : message.substring(0, 160);
    }

    private String safeInput(String input) {
        return input == null ? "" : input.strip();
    }

    private String safeDisplay(String input) {
        return input == null ? "" : input.replaceAll("[\\p{Cntrl}]", "").replaceAll("[*#]", " ");
    }

    private String recipientName(Wallet wallet) {
        Customer recipient = wallet.getCustomerEntity();
        String name = ((recipient.getFirstName() == null ? "" : recipient.getFirstName()) + " "
                + (recipient.getLastName() == null ? "" : recipient.getLastName())).strip();
        return name.isBlank() ? "Nuru Wallet customer" : name;
    }

    private String formatAmount(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }
}
