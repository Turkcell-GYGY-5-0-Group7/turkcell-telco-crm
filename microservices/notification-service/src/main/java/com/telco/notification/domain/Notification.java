package com.telco.notification.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "notifications")
public class Notification {

    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SUPPRESSED = "SUPPRESSED";

    @Id
    private String id;

    @Indexed
    private String userId;
    private String templateCode;
    private String channel;
    private String payloadJson;
    private String status;
    private String errorMessage;
    private Instant createdAt;
    private Instant sentAt;

    protected Notification() {}

    public static Notification create(String userId, String templateCode, String channel, String payloadJson) {
        var n = new Notification();
        n.userId = userId;
        n.templateCode = templateCode;
        n.channel = channel;
        n.payloadJson = payloadJson;
        n.status = "PENDING";
        n.createdAt = Instant.now();
        return n;
    }

    public void markSent() {
        this.status = STATUS_SENT;
        this.sentAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = STATUS_FAILED;
        this.errorMessage = error;
    }

    public void markSuppressed() {
        this.status = STATUS_SUPPRESSED;
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getTemplateCode() { return templateCode; }
    public String getChannel() { return channel; }
    public String getPayloadJson() { return payloadJson; }
    public String getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
}
