package com.nurudeen.ussd_wallet.externalbank.service;

import com.nurudeen.ussd_wallet.externalbank.config.TransferConfiguration;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class TransferFeeService {

    private final TransferConfiguration configuration;

    public TransferFeeService(TransferConfiguration configuration) {
        this.configuration = configuration;
    }

    public BigDecimal calculateFee(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal fee = configuration.flatFee();
        BigDecimal percentageFee = amount.multiply(configuration.percentageFee()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        fee = fee.add(percentageFee);
        if (fee.compareTo(configuration.minimumFee()) < 0) {
            fee = configuration.minimumFee();
        }
        if (configuration.maximumFee() != null && fee.compareTo(configuration.maximumFee()) > 0) {
            fee = configuration.maximumFee();
        }
        return fee.setScale(2, RoundingMode.HALF_UP);
    }
}
