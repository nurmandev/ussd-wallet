package com.nurudeen.ussd_wallet.externalbank.service;

import com.nurudeen.ussd_wallet.externalbank.entity.Bank;
import com.nurudeen.ussd_wallet.externalbank.entity.ExternalTransfer;
import com.nurudeen.ussd_wallet.externalbank.enums.BankResponseCode;
import com.nurudeen.ussd_wallet.externalbank.simulator.BankFailureSimulator;
import com.nurudeen.ussd_wallet.externalbank.simulator.BankProcessingScenario;
import com.nurudeen.ussd_wallet.externalbank.simulator.SimulatedBankResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BankSwitchService {

    private final BankFailureSimulator bankFailureSimulator;

    public BankSwitchService(BankFailureSimulator bankFailureSimulator) {
        this.bankFailureSimulator = bankFailureSimulator;
    }

    public SimulatedBankResponse processTransfer(ExternalTransfer transfer) {
        Bank bank = transfer.getBank();
        if (bank == null) {
            return new SimulatedBankResponse(BankResponseCode.BANK_UNAVAILABLE, "Bank not found", "SWITCH-" + randomReference(), false, 0L);
        }
        BankProcessingScenario scenario = bankFailureSimulator.getScenario(bank.getBankCode());
        if (!scenario.bankAvailable()) {
            return new SimulatedBankResponse(BankResponseCode.BANK_UNAVAILABLE, scenario.message(), "SWITCH-" + randomReference(), false, scenario.latencyMs());
        }
        switch (scenario.responseCode()) {
            case SUCCESS -> {
                return new SimulatedBankResponse(BankResponseCode.SUCCESS, "Transfer accepted by bank", "SWITCH-" + randomReference(), true, scenario.latencyMs());
            }
            case PENDING -> {
                return new SimulatedBankResponse(BankResponseCode.PENDING, scenario.message(), "SWITCH-" + randomReference(), true, scenario.latencyMs());
            }
            case TIMEOUT -> {
                return new SimulatedBankResponse(BankResponseCode.TIMEOUT, "Switch timed out while processing", "SWITCH-" + randomReference(), true, scenario.latencyMs());
            }
            case FAILED -> {
                return new SimulatedBankResponse(BankResponseCode.FAILED, scenario.message(), "SWITCH-" + randomReference(), true, scenario.latencyMs());
            }
            case ACCOUNT_NOT_FOUND -> {
                return new SimulatedBankResponse(BankResponseCode.ACCOUNT_NOT_FOUND, scenario.message(), "SWITCH-" + randomReference(), true, scenario.latencyMs());
            }
            case INSUFFICIENT_BANK_FUNDS -> {
                return new SimulatedBankResponse(BankResponseCode.INSUFFICIENT_BANK_FUNDS, scenario.message(), "SWITCH-" + randomReference(), true, scenario.latencyMs());
            }
            case DUPLICATE_TRANSACTION -> {
                return new SimulatedBankResponse(BankResponseCode.DUPLICATE_TRANSACTION, scenario.message(), "SWITCH-" + randomReference(), true, scenario.latencyMs());
            }
            case BANK_UNAVAILABLE -> {
                return new SimulatedBankResponse(BankResponseCode.BANK_UNAVAILABLE, scenario.message(), "SWITCH-" + randomReference(), false, scenario.latencyMs());
            }
            default -> throw new IllegalStateException("Unsupported bank scenario");
        }
    }

    private String randomReference() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
