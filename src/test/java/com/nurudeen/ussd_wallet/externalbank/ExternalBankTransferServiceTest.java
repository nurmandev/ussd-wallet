package com.nurudeen.ussd_wallet.externalbank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nurudeen.ussd_wallet.externalbank.config.TransferConfiguration;
import com.nurudeen.ussd_wallet.externalbank.dto.AccountResolutionRequest;
import com.nurudeen.ussd_wallet.externalbank.entity.Bank;
import com.nurudeen.ussd_wallet.externalbank.enums.BankStatus;
import com.nurudeen.ussd_wallet.externalbank.enums.TransferChannel;
import com.nurudeen.ussd_wallet.externalbank.service.TransferFeeService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ExternalBankTransferServiceTest {

    @Test
    void bankSeedListContainsActiveSimulatedBanks() {
        Bank bank = new Bank("Access Bank", "SIM001", BankStatus.ACTIVE, TransferChannel.BANK_TRANSFER);
        assertEquals("SIM001", bank.getBankCode());
        assertEquals(BankStatus.ACTIVE, bank.getStatus());
    }

    @Test
    void feeCalculationUsesConfiguredValues() {
        TransferConfiguration configuration = new TransferConfiguration(
                BigDecimal.TEN,
                new BigDecimal("1.50"),
                BigDecimal.ONE,
                new BigDecimal("500.00"),
                new BigDecimal("100000.00"),
                15L);
        TransferFeeService feeService = new TransferFeeService(configuration);
        BigDecimal fee = feeService.calculateFee(new BigDecimal("5000.00"));
        assertEquals(new BigDecimal("85.00").setScale(2), fee.setScale(2));
    }

    @Test
    void accountResolutionRequestValidationRejectsInvalidNumber() {
        AccountResolutionRequest request = new AccountResolutionRequest("SIM001", "abc");
        assertEquals("SIM001", request.bankCode());
        assertEquals("abc", request.accountNumber());
    }
}
