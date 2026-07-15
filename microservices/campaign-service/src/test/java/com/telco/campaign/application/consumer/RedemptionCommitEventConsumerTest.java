package com.telco.campaign.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.campaign.application.command.ConfirmRedemptionCommand;
import com.telco.platform.mediator.Mediator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedemptionCommitEventConsumerTest {

    @Mock private Mediator mediator;

    private RedemptionCommitEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new RedemptionCommitEventConsumer(mediator, new ObjectMapper());
    }

    private static ConsumerRecord<String, String> record(String messageId, UUID orderId, String eventType) {
        String json = "{\"paymentId\":\"" + UUID.randomUUID() + "\","
                + "\"orderId\":\"" + orderId + "\","
                + "\"customerId\":\"" + UUID.randomUUID() + "\","
                + "\"amount\":49.99,"
                + "\"occurredAt\":\"2026-07-13T00:00:00Z\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("payment.events", 0, 0L, messageId, json);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        return record;
    }

    @Test
    void dispatches_confirm_redemption_command_for_payment_completed() {
        UUID orderId = UUID.randomUUID();

        consumer.onPaymentCompleted(record("msg-1", orderId, "payment.completed.v1"));

        verify(mediator).send(eq(new ConfirmRedemptionCommand(orderId, "msg-1")));
    }

    @Test
    void ignores_non_payment_completed_event_types() {
        UUID orderId = UUID.randomUUID();

        consumer.onPaymentCompleted(record("msg-2", orderId, "payment.failed.v1"));

        verify(mediator, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ignores_message_with_no_event_type_header() {
        UUID orderId = UUID.randomUUID();
        String json = "{\"orderId\":\"" + orderId + "\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("payment.events", 0, 0L, "msg-3", json);

        consumer.onPaymentCompleted(record);

        verify(mediator, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void redelivering_the_same_message_id_dispatches_an_identical_idempotency_key_each_time() {
        // A duplicate Kafka delivery (same messageId/record key) must produce an equal command on
        // every attempt so the platform InboxBehavior (proven in isolation by InboxBehaviorTest and
        // InboxTransactionAtomicityTest) can dedup it to a single effect regardless of how many times
        // this consumer is invoked for the same underlying message.
        UUID orderId = UUID.randomUUID();
        ConsumerRecord<String, String> firstDelivery = record("msg-dup-1", orderId, "payment.completed.v1");
        ConsumerRecord<String, String> redelivery = record("msg-dup-1", orderId, "payment.completed.v1");

        consumer.onPaymentCompleted(firstDelivery);
        consumer.onPaymentCompleted(redelivery);

        ConfirmRedemptionCommand expected = new ConfirmRedemptionCommand(orderId, "msg-dup-1");
        verify(mediator, org.mockito.Mockito.times(2)).send(eq(expected));
    }
}
