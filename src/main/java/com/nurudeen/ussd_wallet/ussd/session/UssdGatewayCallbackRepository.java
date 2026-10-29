package com.nurudeen.ussd_wallet.ussd.session;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UssdGatewayCallbackRepository
        extends JpaRepository<UssdGatewayCallback, UssdGatewayCallback.UssdGatewayCallbackId> {

    Optional<UssdGatewayCallback> findByIdSessionIdAndIdCallbackId(String sessionId, String callbackId);
}
