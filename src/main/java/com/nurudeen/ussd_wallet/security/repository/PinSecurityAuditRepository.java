package com.nurudeen.ussd_wallet.security.repository;

import com.nurudeen.ussd_wallet.security.entity.PinSecurityAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PinSecurityAuditRepository extends JpaRepository<PinSecurityAudit, UUID> {
}
