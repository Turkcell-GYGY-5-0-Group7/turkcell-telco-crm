package com.telco.order.application.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telco.order.application.command.FulfillOrderCommand;
import com.telco.platform.mediator.Mediator;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Covers the PLAN_CHANGE fulfillment consumer (Sprint 24 Feature 24.4, design-note D2): the
 * eventType filter, the orderId-derived idempotency key (the record key is the subscriptionId,
 * which is NOT unique across successive plan changes), and the missing-orderId skip.
 * {@code FulfillOrderCommandHandler} behavior is covered by its own test.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionTariffChangedEventConsumerTest {

    @Mock private Mediator mediator;

    private SubscriptionTariffChangedEventConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        consumer = new SubscriptionTariffChangedEventConsumer(mediator, objectMapper);
    }

    private ConsumerRecord<String, String> record(UUID subscriptionId, String orderId, String eventType) {
        String json = "{\"subscriptionId\":\"" + subscriptionId + "\","
                + "\"customerId\":\"" + UUID.randomUUID() + "\","
                + "\"msisdn\":\"905320000001\","
                + "\"previousTariffCode\":\"POSTPAID-S\","
                + "\"newTariffCode\":\"POSTPAID-M\","
                + "\"newTariffVersion\":2,"
                + (orderId == null ? "" : "\"orderId\":\"" + orderId + "\",")
                + "\"changedAt\":1789000000000}";
        ConsumerRecord<String, String> rec = new ConsumerRecord<>(
                "subscription.events", 0, 0L, subscriptionId.toString(), json);
        if (eventType != null) {
            rec.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        }
        return rec;
    }

    @Test
    void dispatches_fulfill_with_order_scoped_idempotency_key() {
        UUID subscriptionId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        consumer.onTariffChanged(record(subscriptionId, orderId.toString(),
                "subscription.tariff-changed.v1"));

        verify(mediator).send(new FulfillOrderCommand(orderId, subscriptionId.toString(),
                "plan-change-fulfill:" + orderId));
    }

    @Test
    void ignores_other_event_types_and_missing_header() {
        consumer.onTariffChanged(record(UUID.randomUUID(), UUID.randomUUID().toString(),
                "subscription.activated.v1"));
        consumer.onTariffChanged(record(UUID.randomUUID(), UUID.randomUUID().toString(), null));

        verify(mediator, never()).send(any());
    }

    @Test
    void ignores_payload_without_order_id() {
        consumer.onTariffChanged(record(UUID.randomUUID(), null, "subscription.tariff-changed.v1"));

        verify(mediator, never()).send(any());
    }
}
