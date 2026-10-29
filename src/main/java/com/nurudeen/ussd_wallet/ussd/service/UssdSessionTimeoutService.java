package com.nurudeen.ussd_wallet.ussd.service;

import com.nurudeen.ussd_wallet.ussd.config.UssdProperties;
import com.nurudeen.ussd_wallet.ussd.session.UssdSessionEvent;
import com.nurudeen.ussd_wallet.ussd.session.UssdSessionEventRepository;
import com.nurudeen.ussd_wallet.ussd.session.UssdSessionRepository;
import com.nurudeen.ussd_wallet.ussd.session.UssdSessionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UssdSessionTimeoutService {

    private final UssdSessionRepository sessionRepository;
    private final UssdSessionEventRepository eventRepository;
    private final UssdProperties properties;
    private final Clock clock;

    public UssdSessionTimeoutService(
            UssdSessionRepository sessionRepository,
            UssdSessionEventRepository eventRepository,
            UssdProperties properties,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${USSD_CLEANUP_INTERVAL_MILLIS:30000}")
    @Transactional
    public void expireAndClean() {
        Instant now = clock.instant();
        sessionRepository.findOpenExpired(now).forEach(session -> {
            String previousStep = session.getCurrentStep();
            session.getSessionData().clear();
            session.advance(
                    session.getCurrentMenu(),
                    previousStep,
                    UssdSessionStatus.EXPIRED,
                    now,
                    now);
            eventRepository.save(new UssdSessionEvent(
                    session.getSessionId(), "SESSION_EXPIRED", previousStep, "EXPIRED"));
        });
        sessionRepository.deleteCompletedBefore(
                now.minus(properties.sessionRetentionSeconds(), ChronoUnit.SECONDS));
    }
}
