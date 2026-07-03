package com.telco.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.notification.channel.ChannelAdapter;
import com.telco.notification.domain.NotificationTemplate;
import com.telco.notification.infrastructure.persistence.CommunicationPreferenceRepository;
import com.telco.notification.infrastructure.persistence.NotificationRepository;
import com.telco.notification.infrastructure.persistence.NotificationTemplateRepository;
import com.telco.notification.service.NotificationService;
import com.telco.platform.outbox.OutboxService;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Event-schema contract gate for {@code notification.dispatched.v1} (feature 14.1.2, ADR-019, NFR-16).
 *
 * <p>notification-service builds its outbox payload inline as {@code Map.of(...)} rather than a typed
 * record. This test drives {@link NotificationService#dispatch} with Mockito mocks, captures the
 * actual payload passed to {@link OutboxService#publish}, and compares its key set field-for-field
 * against the frozen Avro snapshot under {@code src/test/resources/avro/}. A removed, renamed, or
 * added key fails the build, blocking a backward-incompatible change. No Kafka, Schema Registry, or
 * Spring context is booted.
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationTemplateRepository templateRepository;
    @Mock private CommunicationPreferenceRepository preferenceRepository;
    @Mock private OutboxService outboxService;

    private static final ChannelAdapter NOOP_SMS = new ChannelAdapter() {
        @Override public String channel() { return "SMS"; }
        @Override public void dispatch(String recipient, String subject, String body) { }
    };

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, templateRepository,
                preferenceRepository, List.of(NOOP_SMS), outboxService, new ObjectMapper());
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void dispatched_event_payload_matches_frozen_contract() {
        when(preferenceRepository.findByUserIdAndChannel("user-1", "SMS"))
                .thenReturn(Optional.empty()); // default opt-in
        when(templateRepository.findByCodeAndChannelAndLocale("WELCOME", "SMS", "en"))
                .thenReturn(Optional.of(NotificationTemplate.of("WELCOME", "SMS", "en",
                        "Welcome", "Hi {{customerName}}")));

        service.dispatch("user-1", "WELCOME", "SMS", Map.of("customerName", "Alice"), "en");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).publish(eq("notification"), any(), eq("notification.dispatched.v1"),
                payload.capture());

        Map<String, Object> asMap = MAPPER.convertValue(payload.getValue(), new TypeReference<>() {});
        assertThat(asMap.keySet())
                .as("emitted notification.dispatched.v1 payload keys must equal the frozen contract; "
                        + "a removed/renamed/added key is a backward-incompatible change (NFR-16)")
                .containsExactlyInAnyOrderElementsOf(avroFieldNames("avro/notification-dispatched.avsc"));
    }

    private static Set<String> avroFieldNames(String classpathPath) {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(classpathPath)) {
            assertThat(in).as("schema resource not found: %s", classpathPath).isNotNull();
            JsonNode root = MAPPER.readTree(in);
            return StreamSupport.stream(root.get("fields").spliterator(), false)
                    .map(n -> n.get("name").asText())
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
