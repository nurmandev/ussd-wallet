package com.nurudeen.ussd_wallet.externalbank.simulator;

import com.nurudeen.ussd_wallet.externalbank.config.BankSimulatorConfig;
import com.nurudeen.ussd_wallet.externalbank.enums.BankResponseCode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class BankFailureSimulator {

    private final BankSimulatorConfig config;
    private final Map<String, BankProcessingScenario> overrides = new ConcurrentHashMap<>();

    public BankFailureSimulator(BankSimulatorConfig config) {
        this.config = config;
    }

    public BankProcessingScenario getScenario(String bankCode) {
        return overrides.getOrDefault(bankCode, toScenario(bankCode, config.defaultScenarioObject()));
    }

    public void setScenario(String bankCode, BankResponseCode responseCode, String message, long latencyMs, boolean bankAvailable) {
        overrides.put(bankCode, new BankProcessingScenario(responseCode, message, latencyMs, bankAvailable));
    }

    public void reset(String bankCode) {
        overrides.remove(bankCode);
    }

    private BankProcessingScenario toScenario(String bankCode, BankProcessingScenario defaultScenario) {
        String configured = config.bankScenarios().getOrDefault(bankCode, defaultScenario.responseCode().name());
        try {
            BankResponseCode code = BankResponseCode.valueOf(configured.toUpperCase());
            return new BankProcessingScenario(code, defaultScenario.message(), defaultScenario.latencyMs(), defaultScenario.bankAvailable());
        } catch (IllegalArgumentException ignored) {
            return defaultScenario;
        }
    }
}
