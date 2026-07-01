package com.telco.subscription.welcome;

import com.telco.subscription.application.event.SubscriptionActivatedV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-scoped stand-in for the welcome-SMS side effect of AC-01 step 6.
 *
 * <p>The REAL welcome consumer lives in notification-service, which is Sprint 12 (not yet built).
 * Until then this lightweight component proves the CONTRACT the future consumer relies on: given a
 * {@code subscription.activated.v1} event, it can derive and send a welcome SMS purely from the event
 * payload (customerId, msisdn, tariffCode; orderId for correlation) - no extra lookup required.
 *
 * <p>It logs {@code "welcome SMS -> {msisdn}"} (AC-01 step 6 wording) and records each welcome so a
 * test can assert the signal fired. This is deliberately NOT a Spring {@code @KafkaListener}; the
 * routing test invokes {@link #onSubscriptionActivated} directly with the exact payload the real
 * {@code subscription.activated.v1} outbox row carries, keeping the proof broker-free and Sprint-12
 * independent.
 */
public final class MockWelcomeSmsLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockWelcomeSmsLog.class);

    /** A welcome signal derived entirely from the activation event payload. */
    public record WelcomeSms(String msisdn, String customerId, String tariffCode, String orderId) {
    }

    private final List<WelcomeSms> sent = new ArrayList<>();

    /**
     * Handles a {@code subscription.activated.v1} event by "sending" a welcome SMS. Everything needed
     * is read from the event - proving the event is self-sufficient for the notification hop.
     */
    public WelcomeSms onSubscriptionActivated(SubscriptionActivatedV1 event) {
        WelcomeSms welcome = new WelcomeSms(
                event.msisdn(), event.customerId(), event.tariffCode(), event.orderId());
        sent.add(welcome);
        LOGGER.info("welcome SMS -> {}", event.msisdn());
        return welcome;
    }

    public List<WelcomeSms> sent() {
        return List.copyOf(sent);
    }
}
