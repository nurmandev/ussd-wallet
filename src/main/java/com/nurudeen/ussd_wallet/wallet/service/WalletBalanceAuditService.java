package com.nurudeen.ussd_wallet.wallet.service;

import com.nurudeen.ussd_wallet.wallet.entity.WalletBalanceEnquiryAudit;
import com.nurudeen.ussd_wallet.wallet.repository.WalletBalanceEnquiryAuditRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletBalanceAuditService {

    private final WalletBalanceEnquiryAuditRepository auditRepository;
    private final Clock clock;

    public WalletBalanceAuditService(
            WalletBalanceEnquiryAuditRepository auditRepository, Clock clock) {
        this.auditRepository = auditRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID customerId,
            UUID walletId,
            String sessionId,
            WalletBalanceService.Channel channel,
            String correlationId,
            String resultCategory) {
        auditRepository.save(new WalletBalanceEnquiryAudit(
                customerId,
                walletId,
                sessionId,
                channel.name(),
                correlationId,
                resultCategory,
                clock.instant()));
    }
}
