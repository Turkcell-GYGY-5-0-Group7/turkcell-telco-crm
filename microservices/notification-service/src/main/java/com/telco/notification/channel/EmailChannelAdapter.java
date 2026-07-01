package com.telco.notification.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailChannelAdapter implements ChannelAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailChannelAdapter.class);

    @Override
    public String channel() { return "EMAIL"; }

    @Override
    public void dispatch(String recipient, String subject, String body) {
        LOGGER.info("[MOCK-EMAIL] to={} subject={} body={}", recipient, subject, body);
    }
}
