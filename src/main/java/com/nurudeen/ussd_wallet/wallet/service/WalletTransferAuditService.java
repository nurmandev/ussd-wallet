package com.nurudeen.ussd_wallet.wallet.service;

import com.nurudeen.ussd_wallet.wallet.entity.WalletTransfer;
import com.nurudeen.ussd_wallet.wallet.entity.WalletTransferAudit;
import com.nurudeen.ussd_wallet.wallet.repository.WalletTransferAuditRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WalletTransferAuditService {

    private final WalletTransferAuditRepository auditRepository;
    private final Clock clock;

    public WalletTransferAuditService(WalletTransferAuditRepository auditRepository, Clock clock) {
        this.auditRepository = auditRepository;
        this.clock = clock;
    }

    public void record(
            WalletTransfer transfer,
            UUID customerId,
            WalletTransferService.TransferChannel channel,
            String correlationId) {
        auditRepository.save(new WalletTransferAudit(
                transfer,
                customerId,
                WalletTransferAudit.WalletTransferServiceChannel.valueOf(channel.name()),
                correlationId,
                clock.instant()));
    }
}
