package com.telco.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.notification.channel.ChannelAdapter;
import com.telco.notification.domain.CommunicationPreference;
import com.telco.notification.domain.Notification;
import com.telco.notification.domain.NotificationTemplate;
import com.telco.notification.infrastructure.persistence.CommunicationPreferenceRepository;
import com.telco.notification.infrastructure.persistence.NotificationRepository;
import com.telco.notification.infrastructure.persistence.NotificationTemplateRepository;
import com.telco.platform.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);
    private static final String AGGREGATE_TYPE = "notification";
    private static final String FALLBACK_LOCALE = "en";

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository templateRepository;
    private final CommunicationPreferenceRepository preferenceRepository;
    private final List<ChannelAdapter> channelAdapters;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository,
                                NotificationTemplateRepository templateRepository,
                                CommunicationPreferenceRepository preferenceRepository,
                                List<ChannelAdapter> channelAdapters,
                                OutboxService outboxService,
                                ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.preferenceRepository = preferenceRepository;
        this.channelAdapters = channelAdapters;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    public Notification dispatch(String userId, String templateCode, String channel,
                                  Map<String, String> variables, String locale) {
        Notification notification = Notification.create(userId, templateCode, channel,
                toJson(variables));

        boolean optedIn = preferenceRepository.findByUserIdAndChannel(userId, channel)
                .map(CommunicationPreference::isOptedIn)
                .orElse(true); // default opt-in

        if (!optedIn) {
            notification.markSuppressed();
            notificationRepository.save(notification);
            LOGGER.info("Suppressed notification userId={} channel={} templateCode={}",
                    userId, channel, templateCode);
            return notification;
        }

        Optional<NotificationTemplate> template = templateRepository
                .findByCodeAndChannelAndLocale(templateCode, channel, locale)
                .or(() -> templateRepository.findByCodeAndChannelAndLocale(templateCode, channel, FALLBACK_LOCALE))
                .or(() -> templateRepository.findByCodeAndChannel(templateCode, channel));

        String subject = template.map(NotificationTemplate::getSubject).orElse(templateCode);
        String body = template.map(t -> render(t.getBodyTemplate(), variables)).orElse(toJson(variables));

        ChannelAdapter adapter = channelAdapters.stream()
                .filter(a -> a.channel().equalsIgnoreCase(channel))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown channel: " + channel));

        try {
            adapter.dispatch(userId, subject, body);
            notification.markSent();
        } catch (Exception ex) {
            LOGGER.error("Channel dispatch failed userId={} channel={}", userId, channel, ex);
            notification.markFailed(ex.getMessage());
        }

        notification = notificationRepository.save(notification);

        if (Notification.STATUS_SENT.equals(notification.getStatus())) {
            outboxService.publish(AGGREGATE_TYPE, notification.getId(), "notification.dispatched.v1",
                    Map.of("notificationId", notification.getId(),
                            "userId", userId,
                            "channel", channel,
                            "templateCode", templateCode));
        }
        return notification;
    }

    public Page<Notification> history(String userId, int page, int size) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
    }

    public CommunicationPreference upsertPreference(String userId, String channel, boolean optedIn) {
        CommunicationPreference pref = preferenceRepository
                .findByUserIdAndChannel(userId, channel)
                .orElseGet(() -> CommunicationPreference.of(userId, channel, optedIn));
        pref.update(optedIn);
        return preferenceRepository.save(pref);
    }

    public List<CommunicationPreference> getPreferences(String userId) {
        return preferenceRepository.findByUserId(userId);
    }

    private String render(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> e : variables.entrySet()) {
            result = result.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return result;
    }

    private String toJson(Map<String, String> variables) {
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (Exception e) {
            return variables.toString();
        }
    }
}
