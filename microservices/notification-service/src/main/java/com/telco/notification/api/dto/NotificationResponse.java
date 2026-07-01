package com.telco.notification.api.dto;

import com.telco.notification.domain.Notification;
import java.time.Instant;

public record NotificationResponse(
        String id,
        String userId,
        String templateCode,
        String channel,
        String status,
        Instant createdAt,
        Instant sentAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getUserId(), n.getTemplateCode(),
                n.getChannel(), n.getStatus(), n.getCreatedAt(), n.getSentAt());
    }
}
