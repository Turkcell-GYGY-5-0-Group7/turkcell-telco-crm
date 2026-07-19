package com.telco.campaign.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.campaign.application.command.ReleaseRedemptionCommand;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderCancelledEventConsumerTest {

    @Mock private Mediator mediator;

    private OrderCancelledEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderCancelledEventConsumer(mediator, new ObjectMapper());
    }

    private static ConsumerRecord<String, String> record(String messageId, UUID orderId, String eventType) {
        String json = "{\"orderId\":\"" + orderId + "\","
                + "\"customerId\":\"" + UUID.randomUUID() + "\","
                + "\"reason\":\"customer requested\","
                + "\"occurredAt\":\"2026-07-13T00:00:00Z\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("order.events", 0, 0L, messageId, json);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    @Test
    void dispatches_release_redemption_command_for_order_cancelled() {
        UUID orderId = UUID.randomUUID();

        consumer.onOrderCancelled(record("msg-1", orderId, "order.cancelled.v1"));

        verify(mediator).send(eq(new ReleaseRedemptionCommand(orderId, "msg-1")));
    }

    @Test
    void ignores_non_order_cancelled_event_types() {
        UUID orderId = UUID.randomUUID();

        consumer.onOrderCancelled(record("msg-2", orderId, "order.created.v1"));

        verify(mediator, never()).send(any());
    }

    @Test
    void redelivering_the_same_message_id_dispatches_an_identical_idempotency_key_each_time() {
        // Duplicate delivery of the same messageId must yield an equal command each time so the
        // platform InboxBehavior dedups it to a single RESERVED -> RELEASED transition, however many
        // times this consumer itself is invoked for the redelivered record.
        UUID orderId = UUID.randomUUID();
        ConsumerRecord<String, String> firstDelivery = record("msg-dup-1", orderId, "order.cancelled.v1");
        ConsumerRecord<String, String> redelivery = record("msg-dup-1", orderId, "order.cancelled.v1");

        consumer.onOrderCancelled(firstDelivery);
        consumer.onOrderCancelled(redelivery);

        ReleaseRedemptionCommand expected = new ReleaseRedemptionCommand(orderId, "msg-dup-1");
        verify(mediator, org.mockito.Mockito.times(2)).send(eq(expected));
    }
}
