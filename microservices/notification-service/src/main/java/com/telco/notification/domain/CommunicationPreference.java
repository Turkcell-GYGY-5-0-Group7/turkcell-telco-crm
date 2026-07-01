package com.telco.notification.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "communication_preferences")
@CompoundIndex(name = "user_channel", def = "{'userId':1,'channel':1}", unique = true)
public class CommunicationPreference {

    @Id
    private String id;
    private String userId;
    private String channel;
    private boolean optedIn;
    private Instant updatedAt;

    protected CommunicationPreference() {}

    public static CommunicationPreference of(String userId, String channel, boolean optedIn) {
        var p = new CommunicationPreference();
        p.userId = userId;
        p.channel = channel;
        p.optedIn = optedIn;
        p.updatedAt = Instant.now();
        return p;
    }

    public void update(boolean optedIn) {
        this.optedIn = optedIn;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getChannel() { return channel; }
    public boolean isOptedIn() { return optedIn; }
    public Instant getUpdatedAt() { return updatedAt; }
}
