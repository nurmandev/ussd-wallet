package com.nurudeen.ussd_wallet.ussd.service;

import com.nurudeen.ussd_wallet.ussd.config.UssdProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class UssdGatewayRateLimitService {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private final UssdProperties properties;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public UssdGatewayRateLimitService(UssdProperties properties) {
        this.properties = properties;
    }

    public boolean allow(String phoneNumber, Instant now) {
        Window window = windows.compute(phoneNumber, (key, current) -> {
            if (current == null || !current.startedAt().plus(WINDOW).isAfter(now)) {
                return new Window(now, 1);
            }
            return new Window(current.startedAt(), current.count() + 1);
        });
        return window.count() <= properties.maxRequestsPerMinute();
    }

    private record Window(Instant startedAt, int count) {
    }
}
