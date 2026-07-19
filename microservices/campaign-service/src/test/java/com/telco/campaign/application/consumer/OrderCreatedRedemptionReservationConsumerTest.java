package com.telco.campaign.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.campaign.application.command.CreateRedemptionReservationCommand;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderCreatedRedemptionReservationConsumerTest {

    @Mock private Mediator mediator;

    private OrderCreatedRedemptionReservationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderCreatedRedemptionReservationConsumer(mediator, new ObjectMapper());
    }

    private static ConsumerRecord<String, String> record(String messageId, UUID orderId, UUID customerId,
                                                           String eventType, String itemsJson) {
        String json = "{\"orderId\":\"" + orderId + "\","
                + "\"customerId\":\"" + customerId + "\","
                + "\"items\":" + itemsJson + "}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("order.events", 0, 0L, messageId, json);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    @Test
    void creates_one_reservation_per_campaign_priced_item() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        String itemsJson = "[{\"tariffId\":\"t1\",\"tariffName\":\"n\",\"unitPrice\":10,"
                + "\"quantity\":1,\"campaignId\":\"" + campaignId + "\"},"
                + "{\"tariffId\":\"t2\",\"tariffName\":\"n\",\"unitPrice\":20,"
                + "\"quantity\":1,\"campaignId\":null}]";

        consumer.onOrderCreated(record("msg-1", orderId, customerId, "order.created.v1", itemsJson));

        verify(mediator, times(1)).send(eq(new CreateRedemptionReservationCommand(
                campaignId, customerId, orderId, "msg-1:" + campaignId)));
    }

    @Test
    void order_with_no_campaign_priced_items_is_a_no_op() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String itemsJson = "[{\"tariffId\":\"t1\",\"tariffName\":\"n\",\"unitPrice\":10,"
                + "\"quantity\":1,\"campaignId\":null}]";

        consumer.onOrderCreated(record("msg-2", orderId, customerId, "order.created.v1", itemsJson));

        verify(mediator, never()).send(any());
    }

    @Test
    void ignores_non_order_created_event_types() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        consumer.onOrderCreated(record("msg-3", orderId, customerId, "order.cancelled.v1", "[]"));

        verify(mediator, never()).send(any());
    }

    @Test
    void two_items_priced_against_the_same_campaign_dispatch_two_distinct_commands() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        String itemsJson = "[{\"campaignId\":\"" + campaignId + "\"},{\"campaignId\":\"" + campaignId + "\"}]";

        consumer.onOrderCreated(record("msg-4", orderId, customerId, "order.created.v1", itemsJson));

        // Both items share the same idempotency key (messageId + campaignId) by design - a single
        // order.created.v1 message reserving the same campaign twice collapses to one reservation
        // attempt via inbox dedup, which is intentional (one order, one campaign, one redemption).
        verify(mediator, times(2)).send(eq(new CreateRedemptionReservationCommand(
                campaignId, customerId, orderId, "msg-4:" + campaignId)));
    }

    @Test
    void redelivering_the_same_message_id_dispatches_an_identical_idempotency_key_each_time() {
        // A duplicate Kafka delivery (same messageId/record key) must produce an equal command on
        // every attempt so the platform InboxBehavior dedups it to a single RESERVED row, however many
        // times this consumer itself is invoked for the redelivered record.
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        String itemsJson = "[{\"tariffId\":\"t1\",\"tariffName\":\"n\",\"unitPrice\":10,"
                + "\"quantity\":1,\"campaignId\":\"" + campaignId + "\"}]";

        consumer.onOrderCreated(record("msg-dup-1", orderId, customerId, "order.created.v1", itemsJson));
        consumer.onOrderCreated(record("msg-dup-1", orderId, customerId, "order.created.v1", itemsJson));

        CreateRedemptionReservationCommand expected = new CreateRedemptionReservationCommand(
                campaignId, customerId, orderId, "msg-dup-1:" + campaignId);
        verify(mediator, times(2)).send(eq(expected));
    }
}
