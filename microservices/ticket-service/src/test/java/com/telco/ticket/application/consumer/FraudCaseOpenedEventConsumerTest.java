package com.telco.ticket.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import com.telco.ticket.application.command.OpenTicketCommand;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudCaseOpenedEventConsumerTest {

    @Mock private Mediator mediator;
    @Mock private InboxService inboxService;

    private FraudCaseOpenedEventConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new FraudCaseOpenedEventConsumer(mediator, inboxService, objectMapper);
    }

    private static ConsumerRecord<String, String> record(String value, String eventType) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "fraud.events", 0, 0L, "key-" + UUID.randomUUID(), value);
        if (eventType != null) {
            record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    private static String payload(String eventId, UUID caseId, UUID customerId, String severity) {
        return "{\"eventId\":\"" + eventId + "\",\"caseId\":\"" + caseId + "\",\"customerId\":\""
                + customerId + "\",\"signalIds\":[\"" + UUID.randomUUID() + "\"],\"openedAt\":"
                + System.currentTimeMillis() + ",\"highestSeverity\":\"" + severity + "\"}";
    }

    @Test
    void opens_exactly_one_fraud_review_ticket_via_open_ticket_command() {
        String eventId = UUID.randomUUID().toString();
        UUID caseId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(inboxService.firstSeen(eq(eventId), eq("FraudCaseOpenedEventConsumer"))).thenReturn(true);

        consumer.onFraudCaseOpened(record(payload(eventId, caseId, customerId, "HIGH"),
                "fraud.case-opened.v1"));

        ArgumentCaptor<OpenTicketCommand> captor = ArgumentCaptor.forClass(OpenTicketCommand.class);
        verify(mediator, times(1)).send(captor.capture());
        OpenTicketCommand cmd = captor.getValue();
        assertThat(cmd.customerId()).isEqualTo(customerId);
        assertThat(cmd.category()).isEqualTo("FRAUD_REVIEW");
        assertThat(cmd.priority()).isEqualTo("HIGH");
        // Retrievable link back to the originating fraud case.
        assertThat(cmd.externalRef()).isEqualTo(caseId.toString());
        assertThat(cmd.subject()).contains(caseId.toString());
    }

    @Test
    void ignores_event_of_a_different_type_on_the_shared_topic() {
        consumer.onFraudCaseOpened(record(
                "{\"caseId\":\"" + UUID.randomUUID() + "\"}", "fraud.signal-raised.v1"));

        verify(inboxService, never()).firstSeen(anyString(), anyString());
        verify(mediator, never()).send(any());
    }

    @Test
    void replaying_the_same_event_opens_no_second_ticket() {
        String eventId = UUID.randomUUID().toString();
        UUID caseId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        // First delivery is new, replay is a duplicate.
        when(inboxService.firstSeen(eq(eventId), eq("FraudCaseOpenedEventConsumer")))
                .thenReturn(true).thenReturn(false);

        String json = payload(eventId, caseId, customerId, "MEDIUM");
        consumer.onFraudCaseOpened(record(json, "fraud.case-opened.v1"));
        consumer.onFraudCaseOpened(record(json, "fraud.case-opened.v1"));

        verify(mediator, times(1)).send(any(OpenTicketCommand.class));
    }

    @Test
    void defaults_priority_to_medium_when_severity_absent() {
        String eventId = UUID.randomUUID().toString();
        UUID caseId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(inboxService.firstSeen(eq(eventId), anyString())).thenReturn(true);

        String json = "{\"eventId\":\"" + eventId + "\",\"caseId\":\"" + caseId
                + "\",\"customerId\":\"" + customerId + "\",\"signalIds\":[]}";
        consumer.onFraudCaseOpened(record(json, "fraud.case-opened.v1"));

        ArgumentCaptor<OpenTicketCommand> captor = ArgumentCaptor.forClass(OpenTicketCommand.class);
        verify(mediator).send(captor.capture());
        assertThat(captor.getValue().priority()).isEqualTo("MEDIUM");
    }

    @Test
    void wraps_processing_failure_as_runtime_exception_for_kafka_retry() {
        assertThatThrownBy(() -> consumer.onFraudCaseOpened(record("not-json", "fraud.case-opened.v1")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("fraud.case-opened.v1 processing failed");
    }
}
