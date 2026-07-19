package com.telco.ticket.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import com.telco.ticket.application.command.OpenTicketCommand;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisputeOpenedTicketConsumerTest {

    @Mock private Mediator mediator;
    @Mock private InboxService inboxService;

    private DisputeOpenedTicketConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new DisputeOpenedTicketConsumer(mediator, inboxService, objectMapper);
    }

    @Test
    void ignores_events_of_a_different_type_on_the_shared_topic() {
        ConsumerRecord<String, String> record = record(
                "{\"disputeId\":\"" + UUID.randomUUID() + "\",\"customerId\":\"" + UUID.randomUUID() + "\"}",
                "dispute.resolved-customer.v1");

        consumer.onDisputeOpened(record);

        verify(mediator, never()).send(any());
        verify(inboxService, never()).firstSeen(anyString(), anyString());
    }

    @Test
    void opens_dispute_ticket_via_existing_open_ticket_command_with_high_priority_for_a_large_amount() {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(inboxService.firstSeen(anyString(), eq("DisputeOpenedTicketConsumer"))).thenReturn(true);

        ConsumerRecord<String, String> record = record(
                "{\"disputeId\":\"" + disputeId + "\",\"customerId\":\"" + customerId
                        + "\",\"disputedAmount\":1500.00}",
                "dispute.opened.v1");

        consumer.onDisputeOpened(record);

        verify(mediator).send(new OpenTicketCommand(
                customerId, "DISPUTE", "HIGH", "Dispute opened: " + disputeId, disputeId.toString()));
    }

    @Test
    void defaults_to_medium_priority_when_disputedAmount_is_missing() {
        UUID disputeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(inboxService.firstSeen(anyString(), eq("DisputeOpenedTicketConsumer"))).thenReturn(true);

        ConsumerRecord<String, String> record = record(
                "{\"disputeId\":\"" + disputeId + "\",\"customerId\":\"" + customerId + "\"}",
                "dispute.opened.v1");

        consumer.onDisputeOpened(record);

        verify(mediator).send(new OpenTicketCommand(
                customerId, "DISPUTE", "MEDIUM", "Dispute opened: " + disputeId, disputeId.toString()));
    }

    @Test
    void skips_duplicate_message_already_seen_in_inbox() {
        when(inboxService.firstSeen(anyString(), eq("DisputeOpenedTicketConsumer"))).thenReturn(false);
        ConsumerRecord<String, String> record = record(
                "{\"disputeId\":\"" + UUID.randomUUID() + "\",\"customerId\":\"" + UUID.randomUUID() + "\"}",
                "dispute.opened.v1");

        consumer.onDisputeOpened(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void skips_when_customerId_missing() {
        ConsumerRecord<String, String> record = record(
                "{\"disputeId\":\"" + UUID.randomUUID() + "\"}", "dispute.opened.v1");

        consumer.onDisputeOpened(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void wraps_processing_failure_as_runtime_exception_for_kafka_retry() {
        ConsumerRecord<String, String> record = record("not-json", "dispute.opened.v1");

        assertThatThrownBy(() -> consumer.onDisputeOpened(record))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("dispute.opened.v1 ticket consumer failed");
    }

    private static ConsumerRecord<String, String> record(String value, String eventType) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "dispute.events", 0, 0L, "key-" + UUID.randomUUID(), value);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
