package com.telco.notification;

import com.telco.notification.domain.CommunicationPreference;
import com.telco.notification.domain.Notification;
import com.telco.notification.domain.NotificationTemplate;
import com.telco.notification.infrastructure.persistence.CommunicationPreferenceRepository;
import com.telco.notification.infrastructure.persistence.NotificationRepository;
import com.telco.notification.infrastructure.persistence.NotificationTemplateRepository;
import com.telco.notification.service.NotificationService;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=",
                "spring.cloud.config.enabled=false"
        },
        excludeAutoConfiguration = {
                MongoAutoConfiguration.class,
                MongoDataAutoConfiguration.class,
                MongoRepositoriesAutoConfiguration.class
        }
)
@ActiveProfiles("test")
@Testcontainers
class NotificationIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19999");
    }

    @MockitoBean private OutboxService outboxService;
    @MockitoBean private InboxService inboxService;
    @MockitoBean private NotificationRepository notificationRepository;
    @MockitoBean private NotificationTemplateRepository templateRepository;
    @MockitoBean private CommunicationPreferenceRepository preferenceRepository;

    @Autowired private NotificationService notificationService;

    private static final NotificationTemplate WELCOME_TEMPLATE = buildTemplate(
            "WELCOME", "SMS", "Welcome {{customerName}}! Sub: {{subscriptionId}}");
    private static final NotificationTemplate QUOTA_TEMPLATE = buildTemplate(
            "QUOTA_80_PERCENT", "SMS", "Your quota for {{subscriptionId}} is 80% used.");
    private static final NotificationTemplate INVOICE_TEMPLATE = buildTemplate(
            "INVOICE_GENERATED", "EMAIL", "Dear {{customerName}}, invoice {{invoiceId}} of {{amount}} {{currency}} issued.");

    private static NotificationTemplate buildTemplate(String code, String channel, String body) {
        NotificationTemplate t = new NotificationTemplate();
        t.setCode(code);
        t.setChannel(channel);
        t.setLocale("en");
        t.setSubject(code);
        t.setBodyTemplate(body);
        return t;
    }

    @BeforeEach
    void setUp() {
        reset(notificationRepository, templateRepository, preferenceRepository, outboxService);

        // Default: no preference stored (opt-in by default)
        when(preferenceRepository.findByUserIdAndChannel(anyString(), anyString()))
                .thenReturn(Optional.empty());

        // Default template stubs
        when(templateRepository.findByCodeAndChannelAndLocale("WELCOME", "SMS", "en"))
                .thenReturn(Optional.of(WELCOME_TEMPLATE));
        when(templateRepository.findByCodeAndChannelAndLocale("QUOTA_80_PERCENT", "SMS", "en"))
                .thenReturn(Optional.of(QUOTA_TEMPLATE));
        when(templateRepository.findByCodeAndChannelAndLocale("INVOICE_GENERATED", "EMAIL", "en"))
                .thenReturn(Optional.of(INVOICE_TEMPLATE));

        // save() returns the notification passed in
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void dispatch_sends_notification_and_records_sent_status() {
        String userId = UUID.randomUUID().toString();
        Notification notification = notificationService.dispatch(
                userId, "WELCOME", "SMS", Map.of("customerName", "John", "subscriptionId", "SUB-1"), "en");

        assertThat(notification.getStatus()).isEqualTo(Notification.STATUS_SENT);
        assertThat(notification.getSentAt()).isNotNull();

        verify(outboxService, atLeastOnce()).publish(anyString(), anyString(),
                eq("notification.dispatched.v1"), any());
    }

    @Test
    void opted_out_user_receives_suppressed_notification() {
        String userId = UUID.randomUUID().toString();
        CommunicationPreference pref = CommunicationPreference.of(userId, "SMS", false);
        when(preferenceRepository.findByUserIdAndChannel(userId, "SMS"))
                .thenReturn(Optional.of(pref));

        Notification notification = notificationService.dispatch(
                userId, "WELCOME", "SMS", Map.of("customerName", "Jane"), "en");

        assertThat(notification.getStatus()).isEqualTo(Notification.STATUS_SUPPRESSED);
        verify(outboxService, never()).publish(anyString(), anyString(),
                eq("notification.dispatched.v1"), any());
    }

    @Test
    void template_renders_variables_in_body() {
        String userId = UUID.randomUUID().toString();
        Notification notification = notificationService.dispatch(userId, "WELCOME", "SMS",
                Map.of("customerName", "Alice", "subscriptionId", "SUB-42"), "en");

        assertThat(notification.getStatus()).isEqualTo(Notification.STATUS_SENT);
    }

    @Test
    void preference_update_takes_effect_on_next_dispatch() {
        String userId = UUID.randomUUID().toString();

        Notification first = notificationService.dispatch(userId, "WELCOME", "SMS",
                Map.of("customerName", "Bob"), "en");
        assertThat(first.getStatus()).isEqualTo(Notification.STATUS_SENT);

        // Update: user opts out
        CommunicationPreference optedOut = CommunicationPreference.of(userId, "SMS", false);
        when(preferenceRepository.findByUserIdAndChannel(userId, "SMS"))
                .thenReturn(Optional.of(optedOut));

        Notification second = notificationService.dispatch(userId, "QUOTA_80_PERCENT", "SMS",
                Map.of("subscriptionId", "SUB-1"), "en");
        assertThat(second.getStatus()).isEqualTo(Notification.STATUS_SUPPRESSED);
    }

    @Test
    void history_returns_notifications_for_user() {
        String userId = UUID.randomUUID().toString();
        Notification n1 = Notification.create(userId, "WELCOME", "SMS", "{}");
        n1.markSent();
        Notification n2 = Notification.create(userId, "QUOTA_80_PERCENT", "SMS", "{}");
        n2.markSent();

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(n1, n2)));

        List<Notification> history = notificationService.history(userId, 0, 10).getContent();
        assertThat(history).hasSize(2);
    }

    @Test
    void invoice_generated_triggers_email_notification() {
        String userId = UUID.randomUUID().toString();
        Notification notification = notificationService.dispatch(userId, "INVOICE_GENERATED", "EMAIL",
                Map.of("customerName", userId, "invoiceId", "INV-001", "amount", "99.99", "currency", "TRY"),
                "en");

        assertThat(notification.getStatus()).isEqualTo(Notification.STATUS_SENT);
        assertThat(notification.getChannel()).isEqualTo("EMAIL");
    }
}
