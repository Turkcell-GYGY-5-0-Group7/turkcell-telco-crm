package com.telco.billing.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.billing.application.command.ApplyDisputeAdjustmentCommand;
import com.telco.platform.inbox.InboxService;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisputeResolvedCustomerBillingConsumerTest {

    @Mock private Mediator mediator;
    @Mock private InboxService inboxService;

    private DisputeResolvedCustomerBillingConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new DisputeResolvedCustomerBillingConsumer(mediator, inboxService, objectMapper);
    }

    @Test
    void ignores_events_of_a_different_type_on_the_shared_topic() {
        ConsumerRecord<String, String> record = record(
                "{\"invoiceId\":\"" + UUID.randomUUID() + "\",\"resolutionAmount\":10.00}",
                "dispute.opened.v1");

        consumer.onDisputeResolvedCustomer(record);

        verify(mediator, never()).send(any());
    }

    @Test
    void dispatches_adjustment_command_for_referenced_invoice() {
        UUID invoiceId = UUID.randomUUID();
        when(inboxService.firstSeen(anyString(), eq("DisputeResolvedCustomerBillingConsumer"))).thenReturn(true);

        ConsumerRecord<String, String> record = record(
                "{\"disputeId\":\"" + UUID.randomUUID() + "\",\"invoiceId\":\"" + invoiceId
                        + "\",\"resolutionAmount\":25.50}",
                "dispute.resolved-customer.v1");

        consumer.onDisputeResolvedCustomer(record);

        verify(mediator).send(new ApplyDisputeAdjustmentCommand(invoiceId, new BigDecimal("25.50")));
    }

    @Test
    void no_ops_when_invoiceId_is_null_payment_only_dispute() {
        ConsumerRecord<String, String> record = record(
                "{\"disputeId\":\"" + UUID.randomUUID() + "\",\"resolutionAmount\":25.50}",
                "dispute.resolved-customer.v1");

        consumer.onDisputeResolvedCustomer(record);

        verify(mediator, never()).send(any());
    }

    private static ConsumerRecord<String, String> record(String value, String eventType) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "dispute.events", 0, 0L, "key-" + UUID.randomUUID(), value);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
