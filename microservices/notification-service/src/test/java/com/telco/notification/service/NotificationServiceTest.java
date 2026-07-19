package com.telco.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.notification.channel.ChannelAdapter;
import com.telco.notification.domain.CommunicationPreference;
import com.telco.notification.domain.Notification;
import com.telco.notification.domain.NotificationTemplate;
import com.telco.notification.infrastructure.persistence.CommunicationPreferenceRepository;
import com.telco.notification.infrastructure.persistence.NotificationRepository;
import com.telco.notification.infrastructure.persistence.NotificationTemplateRepository;
import com.telco.platform.common.exception.ValidationException;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationTemplateRepository templateRepository;
    @Mock private CommunicationPreferenceRepository preferenceRepository;
    @Mock private OutboxService outboxService;

    private static final ChannelAdapter NOOP_SMS = new ChannelAdapter() {
        @Override public String channel() { return "SMS"; }
        @Override public void dispatch(String recipient, String subject, String body) {}
    };

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, templateRepository,
                preferenceRepository, List.of(NOOP_SMS), outboxService, new ObjectMapper());
        // Lenient because tests that do not call dispatch() (history, preferences, throws) do not use this stub
        lenient().when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void dispatch_sends_notification_and_publishes_outbox_event_when_opted_in() {
        when(preferenceRepository.findByUserIdAndChannel("user-1", "SMS"))
                .thenReturn(Optional.empty()); // absent preference means default opt-in
        when(templateRepository.findByCodeAndChannelAndLocale("WELCOME", "SMS", "en"))
                .thenReturn(Optional.of(NotificationTemplate.of("WELCOME", "SMS", "en",
                        "Welcome", "Hi {{customerName}}")));

        Notification result = service.dispatch("user-1", "WELCOME", "SMS",
                Map.of("customerName", "Alice"), "en");

        assertThat(result.getStatus()).isEqualTo(Notification.STATUS_SENT);
        assertThat(result.getSentAt()).isNotNull();
        verify(notificationRepository).save(any(Notification.class));
        verify(outboxService).publish(eq("notification"), anyString(),
                eq("notification.dispatched.v1"), any());
    }

    @Test
    void dispatch_suppresses_and_does_not_publish_event_when_user_opted_out() {
        when(preferenceRepository.findByUserIdAndChannel("user-1", "SMS"))
                .thenReturn(Optional.of(CommunicationPreference.of("user-1", "SMS", false)));

        Notification result = service.dispatch("user-1", "WELCOME", "SMS", Map.of(), "en");

        assertThat(result.getStatus()).isEqualTo(Notification.STATUS_SUPPRESSED);
        verify(notificationRepository).save(any(Notification.class));
        verify(outboxService, never()).publish(anyString(), anyString(), anyString(), any());
    }

    @Test
    void dispatch_uses_raw_variables_as_body_when_no_template_found() {
        when(preferenceRepository.findByUserIdAndChannel(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(templateRepository.findByCodeAndChannelAndLocale(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(templateRepository.findByCodeAndChannel(anyString(), anyString()))
                .thenReturn(Optional.empty());

        Notification result = service.dispatch("user-1", "UNKNOWN_CODE", "SMS",
                Map.of("foo", "bar"), "en");

        assertThat(result.getStatus()).isEqualTo(Notification.STATUS_SENT);
    }

    @Test
    void dispatch_falls_back_to_locale_agnostic_template_when_locale_specific_missing() {
        when(preferenceRepository.findByUserIdAndChannel(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(templateRepository.findByCodeAndChannelAndLocale("WELCOME", "SMS", "tr"))
                .thenReturn(Optional.empty());
        when(templateRepository.findByCodeAndChannelAndLocale("WELCOME", "SMS", "en"))
                .thenReturn(Optional.empty());
        when(templateRepository.findByCodeAndChannel("WELCOME", "SMS"))
                .thenReturn(Optional.of(NotificationTemplate.of(
                        "WELCOME", "SMS", null, "Welcome", "Hello {{customerName}}")));

        Notification result = service.dispatch("user-1", "WELCOME", "SMS",
                Map.of("customerName", "Ayse"), "tr");

        assertThat(result.getStatus()).isEqualTo(Notification.STATUS_SENT);
    }

    @Test
    void dispatch_throws_illegal_argument_when_channel_has_no_adapter() {
        when(preferenceRepository.findByUserIdAndChannel(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(templateRepository.findByCodeAndChannelAndLocale(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(templateRepository.findByCodeAndChannel(anyString(), anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.dispatch("user-1", "WELCOME", "EMAIL", Map.of(), "en"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EMAIL");

        verify(notificationRepository, never()).save(any());
        verify(outboxService, never()).publish(anyString(), anyString(), anyString(), any());
    }

    @Test
    void history_delegates_to_repository_with_correct_page_request() {
        Notification n = Notification.create("user-1", "WELCOME", "SMS", "{}");
        when(notificationRepository.findByUserId(eq("user-1"), any()))
                .thenReturn(new PageImpl<>(List.of(n)));

        var page = service.history("user-1", 0, 10, null);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getUserId()).isEqualTo("user-1");
    }

    @Test
    void history_absent_sort_defaults_to_created_at_desc() {
        when(notificationRepository.findByUserId(eq("user-1"), any()))
                .thenReturn(new PageImpl<>(List.<Notification>of()));

        service.history("user-1", 0, 10, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByUserId(eq("user-1"), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void history_explicit_sort_is_applied_to_the_repository_call() {
        when(notificationRepository.findByUserId(eq("user-1"), any()))
                .thenReturn(new PageImpl<>(List.<Notification>of()));

        service.history("user-1", 0, 10, "sentAt,asc");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByUserId(eq("user-1"), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "sentAt"));
    }

    @Test
    void history_unknown_sort_property_raises_validation_error() {
        assertThatThrownBy(() -> service.history("user-1", 0, 10, "payloadJson,asc"))
                .isInstanceOf(ValidationException.class);
        verify(notificationRepository, never()).findByUserId(anyString(), any());
    }

    @Test
    void upsert_preference_creates_new_record_when_none_exists() {
        when(preferenceRepository.findByUserIdAndChannel("user-1", "SMS"))
                .thenReturn(Optional.empty());
        when(preferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommunicationPreference result = service.upsertPreference("user-1", "SMS", false);

        assertThat(result.getUserId()).isEqualTo("user-1");
        assertThat(result.getChannel()).isEqualTo("SMS");
        assertThat(result.isOptedIn()).isFalse();
        verify(preferenceRepository).save(any(CommunicationPreference.class));
    }

    @Test
    void upsert_preference_updates_existing_record() {
        CommunicationPreference existing = CommunicationPreference.of("user-1", "SMS", true);
        when(preferenceRepository.findByUserIdAndChannel("user-1", "SMS"))
                .thenReturn(Optional.of(existing));
        when(preferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CommunicationPreference result = service.upsertPreference("user-1", "SMS", false);

        assertThat(result.isOptedIn()).isFalse();
        verify(preferenceRepository).save(existing);
    }

    @Test
    void get_preferences_returns_all_preferences_for_user() {
        List<CommunicationPreference> prefs = List.of(
                CommunicationPreference.of("user-1", "SMS", true),
                CommunicationPreference.of("user-1", "EMAIL", false));
        when(preferenceRepository.findByUserId("user-1")).thenReturn(prefs);

        List<CommunicationPreference> result = service.getPreferences("user-1");

        assertThat(result).hasSize(2);
    }
}
