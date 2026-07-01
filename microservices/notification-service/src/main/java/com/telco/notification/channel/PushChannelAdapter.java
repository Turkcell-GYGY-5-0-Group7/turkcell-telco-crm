package com.telco.notification.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PushChannelAdapter implements ChannelAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushChannelAdapter.class);

    @Override
    public String channel() { return "PUSH"; }

    @Override
    public void dispatch(String recipient, String subject, String body) {
        LOGGER.info("[MOCK-PUSH] to={} body={}", recipient, body);
    }
}
