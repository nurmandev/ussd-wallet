package com.nurudeen.ussd_wallet.wallet.service;

import com.nurudeen.ussd_wallet.common.exception.WalletTransferConflictException;
import com.nurudeen.ussd_wallet.wallet.repository.WalletTransferRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TransferLimitService {

    private final WalletTransferRepository transferRepository;
    private final TransferLimitProperties properties;

    public TransferLimitService(
            WalletTransferRepository transferRepository, TransferLimitProperties properties) {
        this.transferRepository = transferRepository;
        this.properties = properties;
    }

    public void validateDailyLimit(UUID senderWalletId, BigDecimal amount, Instant now) {
        Instant dayStart = LocalDate.ofInstant(now, ZoneOffset.UTC)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
        List<Object[]> result = transferRepository.findDailyTotals(senderWalletId, dayStart);
        if (result.isEmpty() || result.getFirst() == null) {
            throw new IllegalStateException("Daily transfer totals could not be calculated");
        }
        Object[] totals = result.getFirst();
        long count = ((Number) totals[0]).longValue();
        BigDecimal total = (BigDecimal) totals[1];
        if (count >= properties.dailyCount()) {
            throw new TransferLimitExceededException("Daily transfer count limit exceeded");
        }
        if (total.add(amount).compareTo(properties.dailyAmount()) > 0) {
            throw new TransferLimitExceededException("Daily transfer amount limit exceeded");
        }
    }

    public void validateSingleAmount(BigDecimal amount, BigDecimal minimum, BigDecimal maximum) {
        if (amount.compareTo(minimum) < 0 || amount.compareTo(maximum) > 0) {
            throw new WalletTransferConflictException("Transfer amount is outside the permitted limits");
        }
    }

    public BigDecimal configuredFee() {
        return properties.fee();
    }

    public int intentExpirySeconds() {
        return properties.intentExpirySeconds();
    }
}
