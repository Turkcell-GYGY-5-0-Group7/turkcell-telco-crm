package com.telco.notification.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmsChannelAdapter implements ChannelAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmsChannelAdapter.class);

    @Override
    public String channel() { return "SMS"; }

    @Override
    public void dispatch(String recipient, String subject, String body) {
        LOGGER.info("[MOCK-SMS] to={} body={}", recipient, body);
    }
}
