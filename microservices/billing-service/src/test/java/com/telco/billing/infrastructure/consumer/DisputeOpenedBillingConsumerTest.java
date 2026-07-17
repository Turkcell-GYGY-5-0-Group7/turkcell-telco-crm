package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.PlaceInvoiceOnDisputeHoldCommand;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
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
class DisputeOpenedBillingConsumerTest {

    @Mock private Mediator mediator;
    @Mock private InboxService inboxService;

    private DisputeOpenedBillingConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new DisputeOpenedBillingConsumer(mediator, inboxService, objectMapper);
    }

    @Test
    void ignores_events_of_a_different_type_on_the_shared_topic() {
        ConsumerRecord<String, String> record = record(
                "{\"invoiceId\":\"" + UUID.randomUUID() + "\"}", "dispute.resolved-customer.v1");

        consumer.onDisputeOpened(record);

        verify(mediator, never()).send(any());
        verify(inboxService, never()).firstSeen(anyString(), anyString());
    }

    @Test
    void places_hold_on_referenced_invoice() {
        UUID invoiceId = UUID.randomUUID();
        when(inboxService.firstSeen(anyString(), eq("DisputeOpenedBillingConsumer"))).thenReturn(true);

        ConsumerRecord<String, String> record = record(
                "{\"disputeId\":\"" + UUID.randomUUID() + "\",\"invoiceId\":\"" + invoiceId + "\"}",
                "dispute.opened.v1");

        consumer.onDisputeOpened(record);

        verify(mediator).send(new PlaceInvoiceOnDisputeHoldCommand(invoiceId));
    }

    @Test
    void no_ops_when_invoiceId_is_null_payment_only_dispute() {
        ConsumerRecord<String, String> record = record(
                "{\"disputeId\":\"" + UUID.randomUUID() + "\"}", "dispute.opened.v1");

        consumer.onDisputeOpened(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void skips_duplicate_message_already_seen_in_inbox() {
        when(inboxService.firstSeen(anyString(), eq("DisputeOpenedBillingConsumer"))).thenReturn(false);
        ConsumerRecord<String, String> record = record(
                "{\"invoiceId\":\"" + UUID.randomUUID() + "\"}", "dispute.opened.v1");

        consumer.onDisputeOpened(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void wraps_processing_failure_as_runtime_exception_for_kafka_retry() {
        ConsumerRecord<String, String> record = record("not-json", "dispute.opened.v1");

        assertThatThrownBy(() -> consumer.onDisputeOpened(record))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("dispute.opened.v1 billing consumer failed");
    }

    private static ConsumerRecord<String, String> record(String value, String eventType) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "dispute.events", 0, 0L, "key-" + UUID.randomUUID(), value);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
