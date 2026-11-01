package com.nurudeen.ussd_wallet.externalbank.config;

import com.nurudeen.ussd_wallet.externalbank.service.BankService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExternalBankSeedConfiguration {

    @Bean
    ApplicationRunner seedBanks(BankService bankService) {
        return args -> bankService.seedDefaultBanks();
    }
}
