package com.telco.notification.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "notification_templates")
@CompoundIndex(name = "code_channel_locale", def = "{'code':1,'channel':1,'locale':1}", unique = true)
public class NotificationTemplate {

    @Id
    private String id;
    private String code;
    private String channel;
    private String locale;
    private String subject;
    private String bodyTemplate;
    private Instant createdAt;

    protected NotificationTemplate() {}

    public static NotificationTemplate of(String code, String channel, String locale,
                                           String subject, String bodyTemplate) {
        var t = new NotificationTemplate();
        t.code = code;
        t.channel = channel;
        t.locale = locale;
        t.subject = subject;
        t.bodyTemplate = bodyTemplate;
        t.createdAt = Instant.now();
        return t;
    }

    public boolean contentDiffers(String subject, String bodyTemplate) {
        return !this.subject.equals(subject) || !this.bodyTemplate.equals(bodyTemplate);
    }

    public void updateContent(String subject, String bodyTemplate) {
        this.subject = subject;
        this.bodyTemplate = bodyTemplate;
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getChannel() { return channel; }
    public String getLocale() { return locale; }
    public String getSubject() { return subject; }
    public String getBodyTemplate() { return bodyTemplate; }
}
