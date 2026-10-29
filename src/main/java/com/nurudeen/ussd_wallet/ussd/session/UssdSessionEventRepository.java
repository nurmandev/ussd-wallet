package com.nurudeen.ussd_wallet.ussd.session;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UssdSessionEventRepository extends JpaRepository<UssdSessionEvent, UUID> {
}
