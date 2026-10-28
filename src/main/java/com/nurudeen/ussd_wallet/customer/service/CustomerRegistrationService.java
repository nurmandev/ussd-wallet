package com.nurudeen.ussd_wallet.customer.service;

import com.nurudeen.ussd_wallet.common.exception.DuplicateCustomerException;
import com.nurudeen.ussd_wallet.common.exception.ResourceCreationException;
import com.nurudeen.ussd_wallet.common.exception.WalletNumberCollisionException;
import com.nurudeen.ussd_wallet.config.RegistrationProperties;
import com.nurudeen.ussd_wallet.customer.dto.CustomerRegistrationResponse;
import com.nurudeen.ussd_wallet.customer.entity.Customer;
import com.nurudeen.ussd_wallet.customer.repository.CustomerRepository;
import com.nurudeen.ussd_wallet.ledger.service.LedgerAccountCreationService;
import com.nurudeen.ussd_wallet.security.entity.CustomerSecurity;
import com.nurudeen.ussd_wallet.security.repository.CustomerSecurityRepository;
import com.nurudeen.ussd_wallet.wallet.entity.Wallet;
import com.nurudeen.ussd_wallet.wallet.entity.WalletBalance;
import com.nurudeen.ussd_wallet.wallet.dto.WalletResponse;
import com.nurudeen.ussd_wallet.wallet.repository.WalletBalanceRepository;
import com.nurudeen.ussd_wallet.config.WalletProperties;
import com.nurudeen.ussd_wallet.wallet.service.WalletCreationService;
import java.util.Objects;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CustomerRegistrationService {

    private static final String PHONE_UNIQUE_CONSTRAINT = "uk_customers_phone_number";
    private static final String WALLET_NUMBER_UNIQUE_CONSTRAINT = "uk_wallets_wallet_number";

    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final CustomerRepository customerRepository;
    private final CustomerSecurityRepository customerSecurityRepository;
    private final WalletBalanceRepository walletBalanceRepository;
    private final LedgerAccountCreationService ledgerAccountCreationService;
    private final WalletCreationService walletCreationService;
    private final RegistrationProperties registrationProperties;
    private final WalletProperties walletProperties;
    private final TransactionTemplate transactionTemplate;

    public CustomerRegistrationService(
            PhoneNumberNormalizer phoneNumberNormalizer,
            CustomerRepository customerRepository,
            CustomerSecurityRepository customerSecurityRepository,
            WalletBalanceRepository walletBalanceRepository,
            LedgerAccountCreationService ledgerAccountCreationService,
            WalletCreationService walletCreationService,
            RegistrationProperties registrationProperties,
            WalletProperties walletProperties,
            PlatformTransactionManager transactionManager) {
        this.phoneNumberNormalizer = phoneNumberNormalizer;
        this.customerRepository = customerRepository;
        this.customerSecurityRepository = customerSecurityRepository;
        this.walletBalanceRepository = walletBalanceRepository;
        this.ledgerAccountCreationService = ledgerAccountCreationService;
        this.walletCreationService = walletCreationService;
        this.registrationProperties = registrationProperties;
        this.walletProperties = walletProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public CustomerRegistrationResponse register(String inputPhoneNumber) {
        String normalizedPhoneNumber = phoneNumberNormalizer.normalize(inputPhoneNumber);
        int maxAttempts = walletProperties.number().maxRetries();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                return Objects.requireNonNull(transactionTemplate.execute(
                        status -> registerInTransaction(normalizedPhoneNumber)));
            } catch (WalletNumberCollisionException exception) {
                if (attempt + 1 == maxAttempts) {
                    throw new ResourceCreationException(exception);
                }
            } catch (DataIntegrityViolationException exception) {
                String constraintName = findConstraintName(exception);
                if (PHONE_UNIQUE_CONSTRAINT.equalsIgnoreCase(constraintName)) {
                    throw new DuplicateCustomerException();
                }
                if (WALLET_NUMBER_UNIQUE_CONSTRAINT.equalsIgnoreCase(constraintName)
                        && attempt + 1 < maxAttempts) {
                    continue;
                }
                throw new ResourceCreationException(exception);
            }
        }
        throw new ResourceCreationException();
    }

    private CustomerRegistrationResponse registerInTransaction(String phoneNumber) {
        if (customerRepository.existsByPhoneNumber(phoneNumber)) {
            throw new DuplicateCustomerException();
        }

        Customer customer = customerRepository.saveAndFlush(
                new Customer(phoneNumber, registrationProperties.initialCustomerStatus()));
        customerSecurityRepository.saveAndFlush(new CustomerSecurity(customer));

        Wallet wallet = walletCreationService.createWallet(customer);
        WalletBalance balance = walletBalanceRepository.saveAndFlush(new WalletBalance(wallet));
        ledgerAccountCreationService.createCustomerWalletLiabilityAccount(wallet);

        return new CustomerRegistrationResponse(
                customer.getId(),
                customer.getPhoneNumber(),
                customer.getStatus(),
                WalletResponse.from(wallet, balance),
                true,
                customer.getCreatedAt());
    }

    private String findConstraintName(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation) {
                return constraintViolation.getConstraintName();
            }
            current = current.getCause();
        }
        return null;
    }
}
