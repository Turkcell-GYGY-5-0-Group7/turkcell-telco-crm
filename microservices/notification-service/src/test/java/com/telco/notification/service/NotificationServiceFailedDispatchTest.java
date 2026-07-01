package com.telco.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.notification.channel.ChannelAdapter;
import com.telco.notification.domain.Notification;
import com.telco.notification.domain.NotificationTemplate;
import com.telco.notification.infrastructure.persistence.CommunicationPreferenceRepository;
import com.telco.notification.infrastructure.persistence.NotificationRepository;
import com.telco.notification.infrastructure.persistence.NotificationTemplateRepository;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 12.2.1 AC: "a failing adapter records FAILED".
 *
 * <p>Focused service-layer test: the SMS channel adapter throws, so {@link NotificationService}
 * must persist the notification with status {@code FAILED} and must NOT publish
 * {@code notification.dispatched.v1} (only a genuinely SENT notification is announced).
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceFailedDispatchTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationTemplateRepository templateRepository;
    @Mock private CommunicationPreferenceRepository preferenceRepository;
    @Mock private OutboxService outboxService;

    /** A real ChannelAdapter for SMS that always throws, simulating a downstream provider failure. */
    private static final ChannelAdapter FAILING_SMS = new ChannelAdapter() {
        @Override public String channel() { return "SMS"; }
        @Override public void dispatch(String recipient, String subject, String body) {
            throw new IllegalStateException("SMS provider unavailable");
        }
    };

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, templateRepository,
                preferenceRepository, List.of(FAILING_SMS), outboxService, new ObjectMapper());

        when(preferenceRepository.findByUserIdAndChannel(anyString(), anyString()))
                .thenReturn(Optional.empty()); // opted-in by default
        when(templateRepository.findByCodeAndChannelAndLocale("WELCOME", "SMS", "en"))
                .thenReturn(Optional.of(NotificationTemplate.of(
                        "WELCOME", "SMS", "en", "Welcome", "Hi {{customerName}}")));
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void failing_adapter_records_failed_and_does_not_publish_dispatched_event() {
        String userId = UUID.randomUUID().toString();

        Notification result = service.dispatch(userId, "WELCOME", "SMS",
                Map.of("customerName", "Kerem"), "en");

        assertThat(result.getStatus()).isEqualTo(Notification.STATUS_FAILED);
        assertThat(result.getErrorMessage()).contains("SMS provider unavailable");
        assertThat(result.getSentAt()).isNull();

        // The FAILED notification is persisted.
        verify(notificationRepository).save(any(Notification.class));
        // No dispatched event is emitted for a failed send.
        verify(outboxService, never()).publish(anyString(), anyString(),
                eq("notification.dispatched.v1"), any());
    }
}
