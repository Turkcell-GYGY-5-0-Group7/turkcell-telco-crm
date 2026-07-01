package com.telco.notification.channel;

public interface ChannelAdapter {
    String channel();
    void dispatch(String recipient, String subject, String body);
}
