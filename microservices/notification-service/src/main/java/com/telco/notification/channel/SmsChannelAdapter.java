package com.telco.notification.channel;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmsChannelAdapter implements ChannelAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmsChannelAdapter.class);

    @Override
    public String channel() { return "SMS"; }

    @Override
    @CircuitBreaker(name = "notification-channel", fallbackMethod = "dispatchFallback")
    @Bulkhead(name = "notification-channel")
    public void dispatch(String recipient, String subject, String body) {
        LOGGER.info("[MOCK-SMS] to={} body={}", recipient, body);
    }

    private void dispatchFallback(String recipient, String subject, String body, Throwable t) {
        LOGGER.warn("notification-channel circuit open, dropping SMS notification to={}: {}",
                recipient, t.getMessage());
    }
}
