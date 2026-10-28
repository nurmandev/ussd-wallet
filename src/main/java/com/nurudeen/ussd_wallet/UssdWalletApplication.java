package com.nurudeen.ussd_wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UssdWalletApplication {

	public static void main(String[] args) {
		SpringApplication.run(UssdWalletApplication.class, args);
	}

}
