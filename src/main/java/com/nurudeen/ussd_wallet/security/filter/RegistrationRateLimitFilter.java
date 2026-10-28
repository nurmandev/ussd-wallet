package com.nurudeen.ussd_wallet.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nurudeen.ussd_wallet.common.response.ApiResponse;
import com.nurudeen.ussd_wallet.config.RegistrationProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RegistrationRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private static final long WINDOW_MILLIS = 1_000L;

    private final ObjectMapper objectMapper;
    private final int maxRequests;
    private final long windowMillis;
    private final Map<String, RequestWindow> requestWindows = new LinkedHashMap<>();

    public RegistrationRateLimitFilter(
            ObjectMapper objectMapper,
            RegistrationProperties properties) {
        this.objectMapper = objectMapper;
        this.maxRequests = properties.rateLimit().maxRequests();
        this.windowMillis = properties.rateLimit().windowSeconds() * WINDOW_MILLIS;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !"/api/v1/customers/register".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!allowRequest(request.getRemoteAddr(), System.currentTimeMillis())) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                    response.getOutputStream(),
                    ApiResponse.failure("Too many registration requests; try again later"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private synchronized boolean allowRequest(String clientAddress, long now) {
        RequestWindow window = requestWindows.get(clientAddress);
        if (window == null || now - window.startedAt() >= windowMillis) {
            removeExpiredWindows(now);
            if (requestWindows.size() >= MAX_TRACKED_CLIENTS) {
                Iterator<String> oldestClient = requestWindows.keySet().iterator();
                if (oldestClient.hasNext()) {
                    oldestClient.next();
                    oldestClient.remove();
                }
            }
            requestWindows.put(clientAddress, new RequestWindow(now, 1));
            return true;
        }
        if (window.requestCount() >= maxRequests) {
            return false;
        }
        window.increment();
        return true;
    }

    private void removeExpiredWindows(long now) {
        requestWindows.entrySet().removeIf(entry ->
                now - entry.getValue().startedAt() >= windowMillis);
    }

    private static final class RequestWindow {

        private final long startedAt;
        private int requestCount;

        private RequestWindow(long startedAt, int requestCount) {
            this.startedAt = startedAt;
            this.requestCount = requestCount;
        }

        private long startedAt() {
            return startedAt;
        }

        private int requestCount() {
            return requestCount;
        }

        private void increment() {
            requestCount++;
        }
    }
}
