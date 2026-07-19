package com.telco.notification.channel;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Internal ops/security alert channel (Feature 23.4.3), distinct from the customer-facing SMS/EMAIL/PUSH
 * channels. Used to notify a security/ops responder that a fraud case was opened
 * ({@code fraud.case-opened.v1}, ADR-029 Section 5) - informational only, never a customer message.
 * Mirrors the existing {@link ChannelAdapter} implementations (mock delivery, resilience guards); a
 * real deployment would fan this out to an ops paging/alerting sink.
 */
@Component
public class OpsAlertChannelAdapter implements ChannelAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpsAlertChannelAdapter.class);

    public static final String CHANNEL = "OPS_ALERT";

    @Override
    public String channel() { return CHANNEL; }

    @Override
    @CircuitBreaker(name = "notification-channel", fallbackMethod = "dispatchFallback")
    @Bulkhead(name = "notification-channel")
    public void dispatch(String recipient, String subject, String body) {
        LOGGER.warn("[OPS-ALERT] to={} subject={} body={}", recipient, subject, body);
    }

    private void dispatchFallback(String recipient, String subject, String body, Throwable t) {
        LOGGER.warn("notification-channel circuit open, dropping OPS_ALERT to={}: {}",
                recipient, t.getMessage());
    }
}
