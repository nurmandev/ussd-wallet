package com.nurudeen.ussd_wallet.externalbank.config;

import com.nurudeen.ussd_wallet.externalbank.simulator.BankProcessingScenario;
import com.nurudeen.ussd_wallet.externalbank.enums.BankResponseCode;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "externalbank.simulator")
public record BankSimulatorConfig(
        boolean enabled,
        int defaultLatencyMs,
        String defaultScenario,
        Map<String, String> bankScenarios) {

    public BankSimulatorConfig {
        if (bankScenarios == null) {
            bankScenarios = new HashMap<>();
        }
        if (defaultScenario == null || defaultScenario.isBlank()) {
            defaultScenario = BankResponseCode.SUCCESS.name();
        }
    }

    public BankProcessingScenario defaultScenarioObject() {
        return new BankProcessingScenario(BankResponseCode.valueOf(defaultScenario.toUpperCase()), "Simulated response", defaultLatencyMs, true);
    }
}
