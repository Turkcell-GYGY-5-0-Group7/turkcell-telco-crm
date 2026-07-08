package com.telco.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.notification.channel.ChannelAdapter;
import com.telco.notification.domain.NotificationTemplate;
import com.telco.notification.infrastructure.persistence.CommunicationPreferenceRepository;
import com.telco.notification.infrastructure.persistence.NotificationRepository;
import com.telco.notification.infrastructure.persistence.NotificationTemplateRepository;
import com.telco.notification.service.NotificationService;
import com.telco.platform.events.testsupport.AvroContractAssertions;
import com.telco.platform.outbox.OutboxService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.avro.Schema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Event-schema contract gate for {@code notification.dispatched.v1} (feature 14.1.2, ADR-019, NFR-16;
 * extended feature 14.5 phase 6).
 *
 * <p>notification-service builds its outbox payload inline as {@code Map.of(...)} rather than a typed
 * record. This test drives {@link NotificationService#dispatch} with Mockito mocks, captures the
 * actual payload passed to {@link OutboxService#publish}, and compares its keys, runtime value types,
 * and observed nullness <b>against the canonical Avro schema loaded directly from
 * {@code platform-event-contracts}</b> (not a hand-copied local {@code .avsc} snapshot). A removed,
 * renamed, retyped, or added key fails the build, blocking a backward-incompatible change. No Kafka,
 * Schema Registry, or Spring context is booted.
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

        Schema schema = AvroContractAssertions.canonicalSchema(
                "com.telco.platform.events.notification.NotificationDispatchedV1");
        AvroContractAssertions.assertPayloadMatchesSchema(schema, asMap);
    }
}
